package de.bannerfall.recruit;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.OwnerChange;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dismissing units (02-game-rules §5.2a): the units leave the home stack at once, are dismissed one
 * by one at a fifth of the recruit time, keep their population until then, and a cancel puts the
 * rest back. Spearmen at barracks level 1 take 1000 s to recruit and so 200 s to dismiss.
 */
@IntegrationTest
class DismissFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired OwnerChange ownerChange;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        player = TestPlayer.register(mockMvc);
        setLevel(BuildingType.BARRACKS, 1);
        setHomeSpearmen(10);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- placing an order ----

    @Test
    void theUnitsLeaveTheHomeStackAtOnceButStillEatFromTheFarm() throws Exception {
        Instant now = clock.instant();
        // 10 spearmen at home: population 30 + 7 + 10
        player.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(10))
                .andExpect(jsonPath("$.population.used").value(47));

        player.dismiss(UnitType.SPEARMAN, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.building").value("BARRACKS"))
                .andExpect(jsonPath("$.unit").value("SPEARMAN"))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.secondsPerUnit").value(200))
                .andExpect(jsonPath("$.startAt").value(now.toString()))
                .andExpect(jsonPath("$.endAt").value(now.plusSeconds(800).toString()));

        // they no longer defend or can be sent, but their population is still used
        player.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(6))
                .andExpect(jsonPath("$.population.used").value(47));
        // and nothing was paid back
        player.village().andExpect(jsonPath("$.resources.wood", closeTo(500, 1)));
    }

    @Test
    void eachDismissedUnitFreesItsPopulationInTurn() throws Exception {
        player.dismissOk(UnitType.SPEARMAN, 4);

        clock.advance(Duration.ofSeconds(450)); // two units are gone
        player.village().andExpect(jsonPath("$.population.used").value(45));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue[0].completedCount").value(2));

        clock.advance(Duration.ofSeconds(400)); // all four, the order is gone
        player.village()
                .andExpect(jsonPath("$.population.used").value(43))
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(6));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue.length()").value(0));
        assertThat(openDismissOrders()).isZero();
    }

    @Test
    void unitsWaitingToBeDismissedStillBlockTheFarm() throws Exception {
        // farm level 1 holds 240: 30 + 7 + 193 spearmen leave 10 free
        setHomeSpearmen(193);
        jdbc.sql("update village set wood = 900, clay = 900, iron = 900 where id = ?")
                .params(player.villageId)
                .update();
        player.dismissOk(UnitType.SPEARMAN, 100);

        // dismissing frees no place yet, so recruiting into it must not work either
        player.recruit(UnitType.SPEARMAN, 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));
        player.recruit(UnitType.SPEARMAN, 10).andExpect(status().isCreated());
    }

    @Test
    void theOrdersOfOneBuildingRunOneAfterAnother() throws Exception {
        Instant now = clock.instant();
        player.dismissOk(UnitType.SPEARMAN, 3); // 600 s
        player.dismiss(UnitType.SPEARMAN, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value(now.plusSeconds(600).toString()))
                .andExpect(jsonPath("$.endAt").value(now.plusSeconds(1000).toString()));
    }

    @Test
    void onlyUnitsAtHomeCanBeDismissed() throws Exception {
        player.dismiss(UnitType.SPEARMAN, 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        player.dismiss(UnitType.AXEMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        // the ones already in a dismiss order are no longer at home
        player.dismissOk(UnitType.SPEARMAN, 10);
        player.dismiss(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
    }

    @Test
    void eachBuildingHasItsOwnQueueOfFiveOrders() throws Exception {
        setLevel(BuildingType.STABLE, 1);
        setHomeSpearmen(10);
        jdbc.sql("update unit_stack set u_scout = 3 where home_village_id = ?")
                .params(player.villageId)
                .update();
        for (int i = 0; i < 5; i++) {
            player.dismissOk(UnitType.SPEARMAN, 1);
        }

        player.dismiss(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
        player.dismiss(UnitType.SCOUT, 1).andExpect(status().isCreated());
        // the queue is checked before the units
        player.dismiss(UnitType.SPEARMAN, 1000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
    }

    @Test
    void aRazedBuildingDismissesAtTheTimeOfLevelOne() throws Exception {
        setLevel(BuildingType.BARRACKS, 0);

        player.dismiss(UnitType.SPEARMAN, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secondsPerUnit").value(200));
    }

    @Test
    void unitAndCountAreValidated() throws Exception {
        String path = "/api/villages/" + player.villageId + "/dismiss-orders";
        for (String body :
                new String[] {
                    "{}",
                    "{\"unit\":\"SPEARMAN\"}",
                    "{\"unit\":\"SPEARMAN\",\"count\":0}",
                    "{\"unit\":\"SPEARMAN\",\"count\":-1}",
                    "{\"unit\":\"SPEARMAN\",\"count\":30001}",
                    "{\"unit\":\"DRAGON\",\"count\":1}"
                }) {
            player.browser
                    .post(path, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
    }

    // ---- cancelling ----

    @Test
    void cancellingPutsTheUnitsNotYetDismissedBackHome() throws Exception {
        long order = player.dismissOk(UnitType.SPEARMAN, 5);
        clock.advance(Duration.ofSeconds(450)); // two are gone for good

        player.cancelDismiss(order).andExpect(status().isNoContent());

        player.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(8)) // 5 at home + 3 back
                .andExpect(jsonPath("$.population.used").value(45)); // 2 spearmen less
        player.cancelDismiss(order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISMISS_ORDER_NOT_FOUND"));
    }

    @Test
    void theOrdersBehindACancelledOneMoveUp() throws Exception {
        long first = player.dismissOk(UnitType.SPEARMAN, 3);
        player.dismissOk(UnitType.SPEARMAN, 2);
        Instant now = clock.instant();

        player.cancelDismiss(first).andExpect(status().isNoContent());

        player.recruitment()
                .andExpect(jsonPath("$[0].dismissQueue.length()").value(1))
                .andExpect(jsonPath("$[0].dismissQueue[0].startAt").value(now.toString()))
                .andExpect(
                        jsonPath("$[0].dismissQueue[0].endAt")
                                .value(now.plusSeconds(400).toString()));
        player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(8));
    }

    @Test
    void cancellingTheRunningOrderMovesTheNextOneToNow() throws Exception {
        long first = player.dismissOk(UnitType.SPEARMAN, 3); // 600 s
        player.dismissOk(UnitType.SPEARMAN, 2); // 400 s, after the first
        clock.advance(Duration.ofSeconds(250)); // one unit of the first is gone
        Instant now = clock.instant();

        player.cancelDismiss(first).andExpect(status().isNoContent());

        player.recruitment()
                .andExpect(jsonPath("$[0].dismissQueue.length()").value(1))
                .andExpect(jsonPath("$[0].dismissQueue[0].completedCount").value(0))
                .andExpect(jsonPath("$[0].dismissQueue[0].startAt").value(now.toString()))
                .andExpect(
                        jsonPath("$[0].dismissQueue[0].endAt")
                                .value(now.plusSeconds(400).toString()));
        // 5 at home + 2 back, one is gone for good; the second order's two still wait
        player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(7));
    }

    @Test
    void anOrderCantBeCancelledWhileAnAttackIsOnItsWay() throws Exception {
        long order = player.dismissOk(UnitType.SPEARMAN, 5);
        TestPlayer attacker = TestPlayer.register(mockMvc);
        jdbc.sql("update unit_stack set u_spearman = 5 where home_village_id = ?")
                .params(attacker.villageId)
                .update();
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
        String village = body(player.village());
        Coordinates target =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        attacker.command("ATTACK", target, "{\"SPEARMAN\":5}").andExpect(status().isCreated());

        // the units of the order can't be hit, so taking them back now would hide them
        player.cancelDismiss(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISMISS_LOCKED"));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue.length()").value(1));
        player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(5));
        // a new order is still possible
        player.dismiss(UnitType.SPEARMAN, 1).andExpect(status().isCreated());

        // once the attack has landed the order can be cancelled again
        jdbc.sql("delete from movement").update();
        player.cancelDismiss(order).andExpect(status().isNoContent());
        // 4 left at home after the extra order, plus the 5 that came back
        player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(9));
    }

    @Test
    void onlyOrdersOfTheVillageCanBeCancelled() throws Exception {
        long order = player.dismissOk(UnitType.SPEARMAN, 1);
        TestPlayer other = TestPlayer.register(mockMvc);

        other.cancelDismiss(order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISMISS_ORDER_NOT_FOUND"));
        player.cancelDismiss(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISMISS_ORDER_NOT_FOUND"));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue.length()").value(1));
    }

    @Test
    void foreignVillagesAreNotFound() throws Exception {
        long order = player.dismissOk(UnitType.SPEARMAN, 1);
        TestPlayer other = TestPlayer.register(mockMvc);
        String path = "/api/villages/" + player.villageId;

        other.browser
                .post(path + "/dismiss-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .delete(path + "/dismiss-orders/" + order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue.length()").value(1));
    }

    // ---- overview, owner change ----

    @Test
    void theOverviewShowsWhatIsAtHomeTheDismissTimeAndTheQueue() throws Exception {
        long order = player.dismissOk(UnitType.SPEARMAN, 4);

        player.recruitment()
                .andExpect(jsonPath("$[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[0].units[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$[0].units[0].atHome").value(6))
                .andExpect(jsonPath("$[0].units[0].secondsPerUnit").value(1000))
                .andExpect(jsonPath("$[0].units[0].dismissSecondsPerUnit").value(200))
                .andExpect(jsonPath("$[0].dismissQueue[0].id").value(order))
                .andExpect(jsonPath("$[0].dismissQueue[0].count").value(4))
                .andExpect(jsonPath("$[1].dismissQueue.length()").value(0));
    }

    @Test
    void whenTheVillageChangesHandsTheDismissOrdersGoWithoutAnyRefund() throws Exception {
        player.dismissOk(UnitType.SPEARMAN, 4);
        assertThat(openDismissOrders()).isEqualTo(1);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> ownerChange.dropOrders(player.villageId, clock.instant()));

        assertThat(openDismissOrders()).isZero();
    }

    @Test
    void theEndpointsNeedLogin() throws Exception {
        Browser anonymous = Browser.open(mockMvc);
        String path = player.villagePath();

        anonymous
                .post(path + "/dismiss-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}")
                .andExpect(status().isUnauthorized());
        anonymous.delete(path + "/dismiss-orders/1").andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private int openDismissOrders() {
        return jdbc.sql("select count(*) from dismiss_order where village_id = ?")
                .params(player.villageId)
                .query(Integer.class)
                .single();
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
}
