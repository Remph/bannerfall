package de.bannerfall.recruit;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Recruitment over time (M5a): units are done one by one and move home whenever the village is
 * caught up, the queues of different buildings run in parallel, and what a cancel does. The clock
 * is moved by hand. The village has barracks level 1: a spearman takes 1000 s.
 */
@IntegrationTest
class RecruitQueueTest {

    private static final double EPSILON = 1e-6;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
        setLevel(BuildingType.BARRACKS, 1); // population: 30 + 7
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- units over time ----

    @Test
    void unitsAreDoneOneByOne() throws Exception {
        Instant start = startOf(player.recruit(UnitType.SPEARMAN, 3));

        clock.set(start.plusSeconds(999));
        expectSpearmenHome(0);
        player.recruitment().andExpect(jsonPath("$[0].queue[0].completedCount").value(0));

        clock.set(start.plusSeconds(1000));
        expectSpearmenHome(1);
        player.recruitment().andExpect(jsonPath("$[0].queue[0].completedCount").value(1));

        clock.set(start.plusSeconds(2999));
        expectSpearmenHome(2);

        // the last one: the order is gone
        clock.set(start.plusSeconds(3000));
        expectSpearmenHome(3);
        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());

        // the population stayed the same all the time: units in training count already
        player.village().andExpect(jsonPath("$.population.used").value(40));
    }

    @Test
    void twoBuildingsTrainAtTheSameTime() throws Exception {
        setLevel(BuildingType.STABLE, 1); // a scout takes 900 s
        Instant spearmenStart = startOf(player.recruit(UnitType.SPEARMAN, 2));
        Instant scoutsStart = startOf(player.recruit(UnitType.SCOUT, 2));
        assertThat(scoutsStart).isEqualTo(spearmenStart);

        clock.set(spearmenStart.plusSeconds(1800));
        player.village()
                .andExpect(jsonPath("$.unitsHome.SCOUT").value(2))
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(1));

        clock.set(spearmenStart.plusSeconds(2000));
        player.village()
                .andExpect(jsonPath("$.unitsHome.SCOUT").value(2))
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(2));
    }

    @Test
    void theOrdersOfOneBuildingRunOneAfterAnother() throws Exception {
        String first = body(player.recruit(UnitType.SPEARMAN, 2));
        String second = body(player.recruit(UnitType.SPEARMAN, 1));
        Instant start = instant(first, "$.startAt");
        assertThat(instant(first, "$.endAt")).isEqualTo(start.plusSeconds(2000));
        assertThat(instant(second, "$.startAt")).isEqualTo(start.plusSeconds(2000));
        assertThat(instant(second, "$.endAt")).isEqualTo(start.plusSeconds(3000));

        clock.set(start.plusSeconds(2999));
        expectSpearmenHome(2);
        player.recruitment()
                .andExpect(jsonPath("$[0].queue.length()").value(1))
                .andExpect(jsonPath("$[0].queue[0].completedCount").value(0));

        clock.set(start.plusSeconds(3000));
        expectSpearmenHome(3);
    }

    @Test
    void theEventWorkerCatchesUnitsUpToTheTimeOfItsEvent() throws Exception {
        Instant start = startOf(player.recruit(UnitType.SPEARMAN, 5));
        // a woodcutter upgrade at Town Hall 1 takes 1011 s; its completion catches the village up
        Instant buildEnd = instant(body(player.enqueue(BuildingType.WOODCUTTER)), "$.endAt");
        assertThat(buildEnd).isEqualTo(start.plusSeconds(1011));

        // The worker was down. When it handles the completion, the village is brought to 1011 s:
        // one spearman is done by then.
        clock.set(start.plusSeconds(4500));
        assertThat(processor.processDue(clock.instant())).isEqualTo(1);
        assertThat(spearmenInTheHomeStack()).isEqualTo(1);
        assertThat(completedCountOfTheOrder()).isEqualTo(1);

        // the next look at the village brings it to now, nobody is counted twice
        expectSpearmenHome(4);
        player.recruitment().andExpect(jsonPath("$[0].queue[0].completedCount").value(4));
        clock.set(start.plusSeconds(5000));
        expectSpearmenHome(5);
    }

    // ---- cancelling ----

    @Test
    void cancellingRefundsOnlyTheUnitsInTrainingAndMovesTheLaterOrdersUp() throws Exception {
        String first = body(player.recruit(UnitType.SPEARMAN, 4));
        long firstId = number(first, "$.id").longValue();
        long secondId = player.recruitOk(UnitType.SPEARMAN, 2); // from 4000 s to 6000 s
        Instant start = instant(first, "$.startAt");

        clock.set(start.plusSeconds(1500)); // one spearman of the first order is done
        String before = body(player.village());
        // home 1, first order 3, second order 2
        assertThat(number(before, "$.population.used").intValue()).isEqualTo(43);

        player.cancelRecruit(firstId).andExpect(status().isNoContent());

        String after = body(player.village());
        assertThat(number(after, "$.unitsHome.SPEARMAN").intValue()).isEqualTo(1);
        assertThat(number(after, "$.population.used").intValue()).isEqualTo(40);
        // three spearmen refunded: 3 × (40, 35, 15)
        assertThat(stock(after, "wood")).isCloseTo(stock(before, "wood") + 120, within(EPSILON));
        assertThat(stock(after, "clay")).isCloseTo(stock(before, "clay") + 105, within(EPSILON));
        assertThat(stock(after, "iron")).isCloseTo(stock(before, "iron") + 45, within(EPSILON));

        // the second order starts now instead of at 4000 s and keeps its duration
        player.recruitment()
                .andExpect(jsonPath("$[0].queue.length()").value(1))
                .andExpect(jsonPath("$[0].queue[0].id").value(secondId))
                .andExpect(
                        jsonPath("$[0].queue[0].startAt").value(start.plusSeconds(1500).toString()))
                .andExpect(
                        jsonPath("$[0].queue[0].endAt").value(start.plusSeconds(3500).toString()));

        clock.set(start.plusSeconds(2500));
        expectSpearmenHome(2);
        clock.set(start.plusSeconds(3500));
        expectSpearmenHome(3);
        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());
    }

    @Test
    void cancellingTheLastOrderLeavesTheOthersAlone() throws Exception {
        String first = body(player.recruit(UnitType.SPEARMAN, 4));
        long firstId = number(first, "$.id").longValue();
        long secondId = player.recruitOk(UnitType.SPEARMAN, 2);
        Instant start = instant(first, "$.startAt");
        clock.set(start.plusSeconds(1500));
        String before = body(player.village());

        player.cancelRecruit(secondId).andExpect(status().isNoContent());

        String after = body(player.village());
        assertThat(stock(after, "wood")).isCloseTo(stock(before, "wood") + 80, within(EPSILON));
        assertThat(number(after, "$.population.used").intValue()).isEqualTo(41);
        player.recruitment()
                .andExpect(jsonPath("$[0].queue.length()").value(1))
                .andExpect(jsonPath("$[0].queue[0].id").value(firstId))
                .andExpect(jsonPath("$[0].queue[0].completedCount").value(1))
                .andExpect(jsonPath("$[0].queue[0].startAt").value(start.toString()))
                .andExpect(
                        jsonPath("$[0].queue[0].endAt").value(start.plusSeconds(4000).toString()));
    }

    @Test
    void theRefundIsCappedAtTheWarehouse() throws Exception {
        long order = player.recruitOk(UnitType.SPEARMAN, 4); // 200 / 120 / 40
        setStock(990, 990, 990); // the warehouse holds 1000

        player.cancelRecruit(order).andExpect(status().isNoContent());

        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(1000, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(1000, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(1000, EPSILON)));
    }

    @Test
    void anOrderWhoseUnitsAreAllDoneCanNotBeCancelled() throws Exception {
        String order = body(player.recruit(UnitType.SPEARMAN, 1));
        clock.set(instant(order, "$.endAt"));

        player.cancelRecruit(number(order, "$.id").longValue())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_ORDER_NOT_FOUND"));
        expectSpearmenHome(1);
    }

    // ---- population ----

    @Test
    void unitsInTrainingUsePopulationForEverything() throws Exception {
        makeRich();
        setHomeSpearmen(200); // 237 of 240 used
        long order = player.recruitOk(UnitType.SPEARMAN, 3);
        player.village().andExpect(jsonPath("$.population.used").value(240));

        // neither a building (woodcutter +1) nor another unit fits any more
        player.enqueue(BuildingType.WOODCUTTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));

        // one of them is done and at home: still the same population
        clock.advance(Duration.ofSeconds(1000));
        player.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(201))
                .andExpect(jsonPath("$.population.used").value(240));

        // cancelling frees the population of the two still in training
        player.cancelRecruit(order).andExpect(status().isNoContent());
        player.village().andExpect(jsonPath("$.population.used").value(238));
        player.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated());
    }

    // ---- helpers ----

    private void expectSpearmenHome(int count) throws Exception {
        if (count == 0) {
            player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").doesNotExist());
        } else {
            player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(count));
        }
    }

    /** What the database holds, without catching the village up. */
    private int spearmenInTheHomeStack() {
        return jdbc.sql(
                        "select u_spearman from unit_stack"
                                + " where home_village_id = ? and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .query(Integer.class)
                .single();
    }

    private int completedCountOfTheOrder() {
        return jdbc.sql("select completed_count from recruit_order where village_id = ?")
                .params(player.villageId)
                .query(Integer.class)
                .single();
    }

    private void makeRich() {
        setStock(100_000, 100_000, 100_000);
    }

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(wood, clay, iron, player.villageId)
                .update();
    }

    /** The column name comes from the enum, never from user input. */
    private void setLevel(BuildingType building, int level) {
        String column = "b_" + building.name().toLowerCase(Locale.ROOT);
        jdbc.sql("update village set " + column + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setHomeSpearmen(int count) {
        jdbc.sql("update unit_stack set u_spearman = ? where home_village_id = ?")
                .params(count, player.villageId)
                .update();
    }

    private static Instant startOf(ResultActions created) throws Exception {
        return instant(body(created.andExpect(status().isCreated())), "$.startAt");
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }

    private static double stock(String villageJson, String resource) {
        return number(villageJson, "$.resources." + resource).doubleValue();
    }
}
