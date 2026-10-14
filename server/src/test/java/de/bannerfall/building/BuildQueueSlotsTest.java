package de.bannerfall.building;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestGameData;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.rules.BuildRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The longer build queue (M13c, 02-game-rules §4.3): five places at the normal price, the paid
 * places 6–10 with a surcharge by position, no moving from or to a paid place. The test world has
 * the default settings ({@code buildQueueSize} 5, {@code buildQueueMax} 10).
 */
@IntegrationTest
class BuildQueueSlotsTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;

    private final GameData data = TestGameData.load();
    private final BuildingDef farm = data.building(BuildingType.FARM);
    private TestPlayer player;

    @BeforeEach
    void registerRichPlayer() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
        // warehouse 20 holds about 50,000: room for the stock and for every refund
        jdbc.sql("update village set b_warehouse = 20, wood = ?, clay = ?, iron = ? where id = ?")
                .params(40_000, 40_000, 40_000, player.villageId)
                .update();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void thePaidPlacesCostASurchargeByPosition() throws Exception {
        // farm upgrades need no population, so ten of them fit; the clock stands still, so the
        // stock changes only by what is paid
        List<ResourceAmounts> paid = new ArrayList<>();
        for (int position = 1; position <= 10; position++) {
            ResourceAmounts before = stock();
            player.enqueueOk(BuildingType.FARM);
            paid.add(before.minus(stock()));
        }

        for (int position = 1; position <= 10; position++) {
            int level = position + 1;
            assertThat(paid.get(position - 1))
                    .as("position %d", position)
                    .isEqualTo(BuildRules.cost(farm, level, position, 5));
        }
        assertThat(paid.get(4)).isEqualTo(BuildRules.cost(farm, 6)); // 5: the normal price
        assertThat(paid.get(5)).isEqualTo(scaled(BuildRules.cost(farm, 7), 1.25)); // 6: +25 %
        assertThat(paid.get(9)).isEqualTo(scaled(BuildRules.cost(farm, 11), 5.0)); // 10: +400 %

        player.enqueue(BuildingType.FARM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
        player.village().andExpect(jsonPath("$.buildQueue.length()").value(10));
    }

    @Test
    void theOverviewShowsThePriceAtTheNextFreePlace() throws Exception {
        player.buildings()
                .andExpect(jsonPath("$[?(@.building == 'FARM')].next.surchargePercent").value(0));

        for (int i = 0; i < 5; i++) {
            player.enqueueOk(BuildingType.FARM);
        }
        ResourceAmounts sixth = BuildRules.cost(farm, 7, 6, 5);
        player.buildings()
                .andExpect(jsonPath("$[?(@.building == 'FARM')].next.surchargePercent").value(25))
                .andExpect(
                        jsonPath("$[?(@.building == 'FARM')].next.cost.wood").value(sixth.wood()))
                // every building is priced at that place
                .andExpect(
                        jsonPath("$[?(@.building == 'WOODCUTTER')].next.surchargePercent")
                                .value(25));

        for (int i = 0; i < 5; i++) {
            player.enqueueOk(BuildingType.FARM);
        }
        // the queue is full: the overview shows the last place's price
        player.buildings()
                .andExpect(jsonPath("$[?(@.building == 'FARM')].next.surchargePercent").value(400));
    }

    @Test
    void cancellingAnOrderInAPaidPlaceRefundsTheSurchargedPrice() throws Exception {
        for (int i = 0; i < 5; i++) {
            player.enqueueOk(BuildingType.FARM);
        }
        ResourceAmounts before = stock();
        long sixth = player.enqueueOk(BuildingType.FARM);
        assertThat(before.minus(stock())).isEqualTo(BuildRules.cost(farm, 7, 6, 5));

        player.cancel(sixth).andExpect(status().isNoContent());

        assertThat(stock()).isEqualTo(before);
    }

    @Test
    void ordersInPaidPlacesStayWhereTheyAreUntilTheyMoveUp() throws Exception {
        player.enqueueOk(BuildingType.FARM);
        player.enqueueOk(BuildingType.FARM);
        player.enqueueOk(BuildingType.FARM);
        long woodcutter = player.enqueueOk(BuildingType.WOODCUTTER); // position 4
        long clayPit = player.enqueueOk(BuildingType.CLAY_PIT); // position 5
        long ironMine = player.enqueueOk(BuildingType.IRON_MINE); // position 6: paid

        // out of a paid place, and into one, nothing moves
        player.move(ironMine, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
        player.move(clayPit, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
        // within the first five places orders move as before
        String moved = body(player.move(clayPit, 3).andExpect(status().isOk()));
        assertThat(ids(moved).subList(3, 6)).containsExactly(clayPit, woodcutter, ironMine);

        // the first farm is done: the iron mine is on place 5 now and moves like any other
        clock.set(Instant.parse(JsonPath.read(moved, "$[0].endAt")));
        processor.processDue(clock.instant());
        String afterMove =
                body(
                        player.move(ironMine, 2)
                                .andExpect(status().isOk())); // in front of the clay pit
        assertThat(ids(afterMove).subList(2, 5)).containsExactly(ironMine, clayPit, woodcutter);
    }

    private ResourceAmounts stock() throws Exception {
        String village = body(player.village());
        return new ResourceAmounts(
                (int) Math.floor(number(village, "$.resources.wood").doubleValue()),
                (int) Math.floor(number(village, "$.resources.clay").doubleValue()),
                (int) Math.floor(number(village, "$.resources.iron").doubleValue()));
    }

    private static ResourceAmounts scaled(ResourceAmounts cost, double factor) {
        return new ResourceAmounts(
                (int) Math.round(cost.wood() * factor),
                (int) Math.round(cost.clay() * factor),
                (int) Math.round(cost.iron() * factor));
    }

    private static List<Long> ids(String queueJson) {
        List<Number> ids = JsonPath.read(queueJson, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
