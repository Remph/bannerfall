package de.bannerfall.now;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Now list (2.0 N1): everything that runs in the player's villages in one answer. Two players;
 * the clock is moved by hand, the stock and the home units are set with SQL.
 */
@IntegrationTest
class NowFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;

    private TestPlayer alice;
    private TestPlayer bob;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from trade_transport").update();
        alice = TestPlayer.register(mockMvc);
        bob = TestPlayer.register(mockMvc);
        for (TestPlayer player : List.of(alice, bob)) {
            jdbc.sql(
                            "update village set b_market = 5, b_warehouse = 10, b_barracks = 1,"
                                    + " wood = 5000, clay = 5000, iron = 5000,"
                                    + " resources_updated_at = ? where id = ?")
                    .params(Timestamp.from(clock.instant()), player.villageId)
                    .update();
            jdbc.sql(
                            "update unit_stack set u_spearman = 100, u_axeman = 50 where"
                                    + " home_village_id = ? and location_village_id = ?")
                    .params(player.villageId, player.villageId)
                    .update();
        }
        // no beginner protection in the way of an attack
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), bob.playerId)
                .update();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from trade_transport").update();
    }

    @Test
    void aNewPlayerHasOneVillageAndNothingRunning() throws Exception {
        TestPlayer carol = TestPlayer.register(mockMvc);

        carol.browser
                .get("/api/now")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].village.id").value(carol.villageId))
                .andExpect(jsonPath("$.villages[0].builds", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].training", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].dismissals", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].research", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].outgoing", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].incoming", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].merchants", hasSize(0)));
    }

    @Test
    void itNeedsALogin() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/now"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theBuildQueueRunsFirstAndWaitsBehind() throws Exception {
        long first = alice.enqueueOk(BuildingType.HEADQUARTERS);
        long second = alice.enqueueOk(BuildingType.WAREHOUSE);

        String now = body(alice.browser.get("/api/now").andExpect(status().isOk()));

        assertThat(JsonPath.<List<Integer>>read(now, "$.villages[0].builds[*].id"))
                .containsExactly((int) first, (int) second);
        Instant firstEnd = Instant.parse(JsonPath.read(now, "$.villages[0].builds[0].endAt"));
        Instant secondStart = Instant.parse(JsonPath.read(now, "$.villages[0].builds[1].startAt"));
        // the second one waits until the first is done
        assertThat(secondStart).isEqualTo(firstEnd);
    }

    @Test
    void aFinishedOrderIsGoneEvenBeforeTheVillageCompletesIt() throws Exception {
        alice.enqueueOk(BuildingType.HEADQUARTERS);
        String queued = body(alice.browser.get("/api/now"));
        Instant end = Instant.parse(JsonPath.read(queued, "$.villages[0].builds[0].endAt"));

        // the worker has not run: the order is still in the database, but it is done
        clock.set(end.minusSeconds(1));
        alice.browser.get("/api/now").andExpect(jsonPath("$.villages[0].builds", hasSize(1)));
        clock.set(end);
        alice.browser.get("/api/now").andExpect(jsonPath("$.villages[0].builds", hasSize(0)));
        processor.processDue(clock.instant());
    }

    @Test
    void trainingCountsTheFinishedUnitsWithoutCatchingTheVillageUp() throws Exception {
        long order = alice.recruitOk(UnitType.SPEARMAN, 4);
        // barracks level 1: 1000 s per spearman
        clock.advance(Duration.ofSeconds(2500));

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].training", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].training[0].id").value(order))
                .andExpect(jsonPath("$.villages[0].training[0].count").value(4))
                .andExpect(jsonPath("$.villages[0].training[0].completedCount").value(2))
                .andExpect(jsonPath("$.villages[0].training[0].secondsPerUnit").value(1000));
        // reading it moved nothing: the stored order still counts none
        assertThat(
                        jdbc.sql("select completed_count from recruit_order where id = ?")
                                .param(order)
                                .query(Integer.class)
                                .single())
                .isZero();

        clock.advance(Duration.ofSeconds(2000));
        alice.browser.get("/api/now").andExpect(jsonPath("$.villages[0].training", hasSize(0)));
    }

    @Test
    void dismissalsRunAlongside() throws Exception {
        alice.dismissOk(UnitType.SPEARMAN, 5);
        clock.advance(Duration.ofSeconds(450));

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].dismissals", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].dismissals[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$.villages[0].dismissals[0].count").value(5))
                .andExpect(jsonPath("$.villages[0].dismissals[0].completedCount").value(2));
    }

    @Test
    void aRunningResearchIsListedInItsVillageUntilItIsDone() throws Exception {
        Instant start = clock.instant();
        jdbc.sql(
                        "insert into research_order (player_id, village_id, unit, level, start_at,"
                                + " end_at, created_at, cost_wood, cost_clay, cost_iron) values"
                                + " (?, ?, 'SPEARMAN', 2, ?, ?, ?, 0, 0, 0)")
                .params(
                        alice.playerId,
                        alice.villageId,
                        Timestamp.from(start),
                        Timestamp.from(start.plusSeconds(600)),
                        Timestamp.from(start))
                .update();

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].research", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].research[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$.villages[0].research[0].level").value(2))
                .andExpect(jsonPath("$.villages[0].research[0].villageId").value(alice.villageId));
        clock.set(start.plusSeconds(600));
        alice.browser.get("/api/now").andExpect(jsonPath("$.villages[0].research", hasSize(0)));
        jdbc.sql("delete from research_order where player_id = ?").param(alice.playerId).update();
    }

    @Test
    void anAttackIsOutgoingForTheSenderAndIncomingWithHiddenUnitsForTheTarget() throws Exception {
        Coordinates bobAt = coordinates(bob);
        String sent =
                body(
                        alice.command("ATTACK", bobAt, "{\"AXEMAN\":10}")
                                .andExpect(status().isCreated()));
        long id = number(sent, "$.id").longValue();

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].outgoing", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].outgoing[0].id").value(id))
                .andExpect(jsonPath("$.villages[0].outgoing[0].kind").value("ATTACK"))
                .andExpect(jsonPath("$.villages[0].outgoing[0].units.AXEMAN").value(10))
                .andExpect(jsonPath("$.villages[0].incoming", hasSize(0)));
        bob.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].outgoing", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].incoming", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].incoming[0].kind").value("ATTACK"))
                .andExpect(jsonPath("$.villages[0].incoming[0].units").value(nullValue()))
                .andExpect(jsonPath("$.villages[0].incoming[0].departAt").value(nullValue()))
                .andExpect(
                        jsonPath("$.villages[0].incoming[0].sourceVillage.id")
                                .value(alice.villageId));

        // a cancel turns it around: the units come home as a RETURN, which is incoming for alice
        alice.cancelMovement(id).andExpect(status().isOk());
        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].outgoing", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].incoming", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].incoming[0].kind").value("RETURN"));
        bob.browser.get("/api/now").andExpect(jsonPath("$.villages[0].incoming", hasSize(0)));
    }

    @Test
    void merchantsOnTheWayStayListedUntilTheyAreBack() throws Exception {
        Coordinates bobAt = coordinates(bob);
        Instant sentAt = clock.instant();
        String sent =
                body(
                        alice.browser
                                .post(
                                        alice.villagePath() + "/transports",
                                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":300,\"clay\":0,\"iron\":0}"
                                                .formatted(bobAt.x(), bobAt.y()))
                                .andExpect(status().isCreated()));
        Instant arriveAt = Instant.parse(JsonPath.read(sent, "$.arriveAt"));
        Instant returnAt = Instant.parse(JsonPath.read(sent, "$.returnAt"));

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].merchants", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].merchants[0].arrived").value(false));
        // bob's own list has only what he sends: the merchants are alice's
        bob.browser.get("/api/now").andExpect(jsonPath("$.villages[0].merchants", hasSize(0)));

        clock.set(arriveAt);
        processor.processDue(clock.instant());
        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages[0].merchants", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].merchants[0].arrived").value(true));
        clock.set(returnAt);
        alice.browser.get("/api/now").andExpect(jsonPath("$.villages[0].merchants", hasSize(0)));
        assertThat(sentAt).isBefore(arriveAt);
    }

    @Test
    void youNeverSeeSomebodyElsesQueues() throws Exception {
        bob.enqueueOk(BuildingType.HEADQUARTERS);
        bob.recruitOk(UnitType.SPEARMAN, 2);

        alice.browser
                .get("/api/now")
                .andExpect(jsonPath("$.villages", hasSize(1)))
                .andExpect(jsonPath("$.villages[0].builds", hasSize(0)))
                .andExpect(jsonPath("$.villages[0].training", hasSize(0)));
    }

    @Test
    void everyVillageOfThePlayerIsThereOldestFirst() throws Exception {
        long second = secondVillageOf(alice);
        alice.enqueueOk(BuildingType.HEADQUARTERS);

        String now = body(alice.browser.get("/api/now").andExpect(status().isOk()));

        assertThat(JsonPath.<List<Integer>>read(now, "$.villages[*].village.id"))
                .containsExactly((int) alice.villageId, (int) second);
        assertThat(JsonPath.<List<?>>read(now, "$.villages[0].builds")).hasSize(1);
        assertThat(JsonPath.<List<?>>read(now, "$.villages[1].builds")).isEmpty();
        // the other village is not alice's after the test
        jdbc.sql("delete from build_order where village_id = ?").param(second).update();
        jdbc.sql("delete from unit_stack where home_village_id = ?").param(second).update();
        jdbc.sql("delete from village where id = ?").param(second).update();
    }

    // ---- helpers ----

    /** Gives the player a second village on a free field far from the others. */
    private long secondVillageOf(TestPlayer player) {
        long id =
                jdbc.sql(
                                """
                                insert into village (player_id, name, x, y, wood, clay, iron,
                                                     resources_updated_at, loyalty_updated_at,
                                                     points, created_at)
                                values (:player, 'Zweitdorf', :x, 3, 0, 0, 0, now(), now(), 0, now())
                                returning id
                                """)
                        .param("player", player.playerId)
                        .param("x", 3 + (int) (player.villageId % 7))
                        .query(Long.class)
                        .single();
        jdbc.sql("insert into unit_stack (home_village_id, location_village_id) values (?, ?)")
                .params(id, id)
                .update();
        return id;
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
