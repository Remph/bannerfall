package de.bannerfall.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.rules.Population;
import de.bannerfall.village.OwnerChange;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * Village automation (02-game-rules §25, 1.6): build plans, the switch per village, and what a
 * village does by itself when it looks.
 */
@IntegrationTest
class AutomationFlowTest {

    private static final String PLANS = "/api/build-plans";

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired EventProcessor processor;
    @Autowired OwnerChange ownerChange;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("update world_settings set automation_villages = 3").update();
        worldService.reload();
    }

    private String automation() {
        return player.villagePath() + "/automation";
    }

    private void savePlan(String letter, String lines) throws Exception {
        player.browser
                .put(
                        PLANS + "/" + letter,
                        "{\"name\":\"Plan " + letter + "\",\"lines\":" + lines + "}")
                .andExpect(status().isOk());
    }

    private void switchOn(String letter) throws Exception {
        player.browser
                .put(automation(), "{\"plan\":\"" + letter + "\",\"enabled\":true}")
                .andExpect(status().isOk());
    }

    private void unlock() {
        setVillages(3);
    }

    private void setVillages(int count) {
        jdbc.sql("update player set village_count = ? where id = ?")
                .params(count, player.playerId)
                .update();
    }

    private void look() {
        processor.processDue(clock.instant());
    }

    private void setLevel(BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setStock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private List<Instant> pendingLooks() {
        return jdbc
                .sql(
                        "select due_at from game_event where kind = 'AUTOMATION_CHECK'"
                                + " and ref_id = ? and status = 'PENDING'")
                .param(player.villageId)
                .query(Timestamp.class)
                .list()
                .stream()
                .map(Timestamp::toInstant)
                .toList();
    }

    // ---- plans ----

    @Test
    void plansAreSavedListedAndDeleted() throws Exception {
        player.browser.get(PLANS).andExpect(jsonPath("$", hasSize(0)));
        player.browser
                .put(
                        PLANS + "/B",
                        "{\"name\":\" Wirtschaft \",\"lines\":[{\"building\":\"WOODCUTTER\","
                            + "\"level\":5},{\"level\":0,\"recruit\":\"A\"},{\"building\":\"FARM\","
                            + "\"level\":6}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letter").value("B"))
                .andExpect(jsonPath("$.name").value("Wirtschaft"))
                .andExpect(jsonPath("$.lines", hasSize(3)))
                .andExpect(jsonPath("$.lines[1].recruit").value("A"))
                .andExpect(jsonPath("$.lines[1].level").value(0))
                .andExpect(jsonPath("$.lines[2].building").value("FARM"));
        player.browser
                .get(PLANS)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lines[0].level").value(5));

        // another player's plans are his own
        TestPlayer other = TestPlayer.register(mockMvc);
        other.browser.get(PLANS).andExpect(jsonPath("$", hasSize(0)));

        player.browser.delete(PLANS + "/B").andExpect(status().isNoContent());
        player.browser
                .delete(PLANS + "/B")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILD_PLAN_NOT_FOUND"));
    }

    @Test
    void plansWhoseLinesDontFitAreRefused() throws Exception {
        for (String lines :
                List.of(
                        "[]",
                        "[{\"building\":\"BARRACKS\",\"level\":1}]",
                        "[{\"building\":\"FARM\",\"level\":31}]",
                        "[{\"level\":0,\"recruit\":\"A\"},{\"level\":0,\"recruit\":\"B\"}]",
                        "[{\"level\":3}]")) {
            player.browser
                    .put(PLANS + "/A", "{\"name\":\"Falsch\",\"lines\":" + lines + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
    }

    // ---- the switch ----

    @Test
    void theEditorGetsEveryBuildingWithItsRequirements() throws Exception {
        player.browser
                .get(PLANS + "/buildings")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(BuildingType.values().length)))
                .andExpect(jsonPath("$[?(@.building == 'STABLE')].maxLevel").value(20))
                .andExpect(jsonPath("$[?(@.building == 'STABLE')].startLevel").value(0))
                .andExpect(
                        jsonPath("$[?(@.building == 'STABLE')].requires[*].building")
                                .value(containsInAnyOrder("HEADQUARTERS", "BARRACKS", "SMITHY")))
                .andExpect(jsonPath("$[?(@.building == 'HEADQUARTERS')].startLevel").value(1));
    }

    @Test
    void theAutomationNeedsThreeVillages() throws Exception {
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":3}]");
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("LOCKED"))
                .andExpect(jsonPath("$.villagesNeeded").value(3))
                .andExpect(jsonPath("$.villages").value(1));
        player.browser
                .put(automation(), "{\"plan\":\"A\",\"enabled\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTOMATION_LOCKED"));

        unlock();
        player.browser
                .put(automation(), "{\"plan\":\"A\",\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("A"))
                .andExpect(jsonPath("$.enabled").value(true));
        // the world can switch it off
        jdbc.sql("update world_settings set automation_villages = 0").update();
        worldService.reload();
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("LOCKED"))
                .andExpect(jsonPath("$.villagesNeeded").value(0));
    }

    @Test
    void anUnknownPlanIsRefusedAndANullPlanDropsTheAutomation() throws Exception {
        unlock();
        player.browser
                .put(automation(), "{\"plan\":\"C\",\"enabled\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILD_PLAN_NOT_FOUND"));
        savePlan("C", "[{\"building\":\"WOODCUTTER\",\"level\":3}]");
        player.browser
                .put(automation(), "{\"plan\":\"C\",\"enabled\":false}")
                .andExpect(jsonPath("$.state").value("OFF"))
                .andExpect(jsonPath("$.plan").value("C"));
        player.browser
                .put(automation(), "{\"plan\":null,\"enabled\":false}")
                .andExpect(jsonPath("$.state").value("OFF"))
                .andExpect(jsonPath("$.plan").doesNotExist());
        // a deleted plan stops the villages that worked through it
        switchOn("C");
        player.browser.delete(PLANS + "/C").andExpect(status().isNoContent());
        player.browser.get(automation()).andExpect(jsonPath("$.state").value("NO_PLAN"));
    }

    @Test
    void belowTheUnlockAVillageRestsAndWakesUpWithTheNextVillage() throws Exception {
        unlock();
        setStock(900);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":5}]");
        switchOn("A");
        // the player loses a village before the first look
        setVillages(2);
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(0)));
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("LOCKED"))
                .andExpect(jsonPath("$.plan").value("A"))
                .andExpect(jsonPath("$.enabled").value(true));
        // plan and switch stay; it looks again now and then
        assertThat(pendingLooks())
                .containsExactly(clock.instant().plus(AutomationService.FALLBACK));

        setVillages(3);
        clock.advance(AutomationService.FALLBACK);
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(2)));
    }

    @Test
    void aLockedVillageWithoutAnAutomationStopsLooking() throws Exception {
        unlock();
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":5}]");
        switchOn("A");
        setVillages(1);
        player.browser
                .put(automation(), "{\"plan\":null,\"enabled\":false}")
                .andExpect(status().isOk());
        look();
        assertThat(pendingLooks()).isEmpty();
    }

    @Test
    void aVillageThatChangesOwnerLosesItsAutomation() throws Exception {
        unlock();
        setStock(0);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":5}]");
        switchOn("A");
        look();
        assertThat(pendingLooks()).hasSize(1);

        // a conquest drops the old owner's orders and automation; the new owner has few villages
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        s -> ownerChange.dropOrders(player.villageId, clock.instant()));
        setVillages(1);
        assertThat(
                        jdbc.sql("select count(*) from village_automation where village_id = ?")
                                .param(player.villageId)
                                .query(Long.class)
                                .single())
                .isZero();
        // its pending look finds nothing to do and schedules none
        clock.advance(AutomationService.FALLBACK);
        look();
        assertThat(pendingLooks()).isEmpty();
    }

    // ---- building ----

    @Test
    void aQueueFullOfTapsLeavesThePaidSlotsAlone() throws Exception {
        unlock();
        setStock(3000);
        BuildingType[] taps = {
            BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE
        };
        int normal = worldService.settings().buildQueueSize();
        for (int i = 0; i < normal; i++) {
            player.enqueueOk(taps[i % taps.length]);
        }
        savePlan("A", "[{\"building\":\"WAREHOUSE\",\"level\":3}]");
        switchOn("A");
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(normal)));
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("WAITING_QUEUE"))
                .andExpect(jsonPath("$.building").value("WAREHOUSE"));
        // it looks again when the first order is done
        Instant firstEnd =
                Instant.parse(
                        JsonPath.read(TestPlayer.body(player.village()), "$.buildQueue[0].endAt"));
        assertThat(pendingLooks()).containsExactly(firstEnd.plusSeconds(1));
    }

    @Test
    void aVillageQueuesItsPlanAndKeepsTwoOrdersOfItsOwn() throws Exception {
        unlock();
        setStock(900);
        savePlan(
                "A",
                "[{\"building\":\"WOODCUTTER\",\"level\":3},{\"building\":\"CLAY_PIT\",\"level\":2}]");
        switchOn("A");
        assertThat(pendingLooks()).containsExactly(clock.instant());

        look();
        player.village()
                .andExpect(jsonPath("$.buildQueue", hasSize(2)))
                .andExpect(jsonPath("$.buildQueue[0].building").value("WOODCUTTER"))
                .andExpect(jsonPath("$.buildQueue[0].targetLevel").value(2))
                .andExpect(jsonPath("$.buildQueue[0].automated").value(true))
                .andExpect(jsonPath("$.buildQueue[1].targetLevel").value(3))
                .andExpect(jsonPath("$.buildQueue[1].automated").value(true));
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("WAITING_QUEUE"))
                .andExpect(jsonPath("$.line").value(1))
                .andExpect(jsonPath("$.lineCount").value(2))
                .andExpect(jsonPath("$.building").value("CLAY_PIT"))
                .andExpect(jsonPath("$.level").value(2));
        // it looks again right after the first order is done
        Instant firstEnd =
                Instant.parse(
                        JsonPath.read(TestPlayer.body(player.village()), "$.buildQueue[0].endAt"));
        assertThat(pendingLooks()).containsExactly(firstEnd.plusSeconds(1));

        // a tap of the player counts: his own order doesn't count towards the automation's two
        clock.set(firstEnd.plusSeconds(1));
        look();
        player.village()
                .andExpect(jsonPath("$.buildQueue", hasSize(2)))
                .andExpect(jsonPath("$.buildQueue[1].building").value("CLAY_PIT"))
                .andExpect(jsonPath("$.buildQueue[1].automated").value(true));
        player.enqueueOk(BuildingType.IRON_MINE);
        player.village().andExpect(jsonPath("$.buildQueue[2].automated").value(false));

        // all lines reached once the orders are done
        clock.advance(Duration.ofDays(1));
        look();
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.line").value(2))
                .andExpect(jsonPath("$.building").doesNotExist());
    }

    @Test
    void aPlanTheStockDoesntPayWaitsForIt() throws Exception {
        unlock();
        setStock(0);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":2}]");
        switchOn("A");
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(0)));
        String status = TestPlayer.body(player.browser.get(automation()));
        assertThat(status).contains("\"state\":\"WAITING_RESOURCES\"");
        Instant readyAt = Instant.parse(JsonPath.read(status, "$.readyAt"));
        assertThat(readyAt).isAfter(clock.instant());
        // it looks again when the stock is there, but at the latest after half an hour
        Instant fallback = clock.instant().plus(AutomationService.FALLBACK);
        assertThat(pendingLooks())
                .containsExactly(readyAt.isBefore(fallback) ? readyAt.plusSeconds(1) : fallback);

        clock.set(readyAt.plusSeconds(1));
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(1)));
    }

    @Test
    void resourcesFromOutsideAreUsedOnceTheStatusIsOpened() throws Exception {
        unlock();
        setStock(0);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":2}]");
        switchOn("A");
        look();
        // loot or a transport fills the stock long before the planned look
        setStock(10_000);
        player.browser.get(automation()).andExpect(jsonPath("$.state").value("READY"));
        assertThat(pendingLooks()).contains(clock.instant().plusSeconds(1));

        clock.set(clock.instant().plusSeconds(1));
        look();
        player.village().andExpect(jsonPath("$.buildQueue", hasSize(1)));
    }

    @Test
    void aFullFarmIsUpgradedFirst() throws Exception {
        unlock();
        setStock(900);
        // spearmen at home fill the farm up to less than a tenth
        jdbc.sql(
                        "update unit_stack set u_spearman = 200 where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .update();
        savePlan("A", "[{\"building\":\"CLAY_PIT\",\"level\":2}]");
        switchOn("A");
        look();
        player.village().andExpect(jsonPath("$.buildQueue[0].building").value("FARM"));
    }

    @Test
    void aQueuedDemolitionCountsOneLevelLessAndTheAutomationWaitsForIt() throws Exception {
        unlock();
        setStock(3000);
        setLevel(BuildingType.WOODCUTTER, 3);
        player.demolishOk(BuildingType.WOODCUTTER);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":3}]");
        switchOn("A");
        look();
        // 3 with the demolition queued is 2: the line isn't reached, but no upgrade goes next to it
        player.village()
                .andExpect(jsonPath("$.buildQueue", hasSize(1)))
                .andExpect(jsonPath("$.buildQueue[0].demolish").value(true));
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("WAITING_QUEUE"))
                .andExpect(jsonPath("$.building").value("WOODCUTTER"))
                .andExpect(jsonPath("$.level").value(3));
        Instant end =
                Instant.parse(
                        JsonPath.read(TestPlayer.body(player.village()), "$.buildQueue[0].endAt"));
        assertThat(pendingLooks()).containsExactly(end.plusSeconds(1));
    }

    @Test
    void whileAFarmDemolitionIsQueuedTheAutomationRecruitsForTheSmallerFarm() throws Exception {
        unlock();
        setLevel(BuildingType.HEADQUARTERS, 3);
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WAREHOUSE, 20);
        setLevel(BuildingType.FARM, 3);
        setStock(20000);
        player.demolishOk(BuildingType.FARM);
        player.browser
                .put(
                        "/api/recruit-templates/B",
                        "{\"name\":\"Deff\",\"units\":{\"SPEARMAN\":1000}}")
                .andExpect(status().isOk());
        savePlan(
                "A", "[{\"building\":\"WOODCUTTER\",\"level\":1},{\"level\":0,\"recruit\":\"B\"}]");
        switchOn("A");
        look();
        // only the population of the farm at level 2 is recruited: counted with level 3 the order
        // would ask for more than the recruit check allows, and nothing would be queued
        player.recruitment()
                .andExpect(jsonPath("$[0].queue", hasSize(1)))
                .andExpect(jsonPath("$[0].queue[0].unit").value("SPEARMAN"));
        String village = TestPlayer.body(player.village());
        int used = JsonPath.read(village, "$.population.used");
        int capacity = JsonPath.read(village, "$.population.capacity");
        assertThat(capacity).isEqualTo(Population.farmCapacity(2));
        assertThat(used).isBetween(capacity - 1, capacity); // the share is rounded down
    }

    @Test
    void aMinesLineBuildsAllThreeMinesTheLowestFirst() throws Exception {
        unlock();
        setStock(900);
        setLevel(BuildingType.CLAY_PIT, 2);
        savePlan("A", "[{\"building\":null,\"level\":3,\"recruit\":null,\"mines\":true}]");
        player.browser
                .get(PLANS)
                .andExpect(jsonPath("$[0].lines[0].mines").value(true))
                .andExpect(jsonPath("$[0].lines[0].building").doesNotExist());
        switchOn("A");

        look();
        // the clay pit (level 2) waits while the other two (level 1) catch up, lowest first
        player.village()
                .andExpect(jsonPath("$.buildQueue", hasSize(2)))
                .andExpect(jsonPath("$.buildQueue[0].building").value("WOODCUTTER"))
                .andExpect(jsonPath("$.buildQueue[0].targetLevel").value(2))
                .andExpect(jsonPath("$.buildQueue[1].building").value("IRON_MINE"))
                .andExpect(jsonPath("$.buildQueue[1].targetLevel").value(2));
        player.browser.get(automation()).andExpect(jsonPath("$.line").value(0));

        clock.advance(Duration.ofDays(1));
        look();
        clock.advance(Duration.ofDays(1));
        look();
        clock.advance(Duration.ofDays(1));
        look();
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.line").value(1));
    }

    @Test
    void demolishingALevelThePlanBuildsPausesItOtherDemolitionsDont() throws Exception {
        unlock();
        setLevel(BuildingType.WOODCUTTER, 4);
        setLevel(BuildingType.CLAY_PIT, 2);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":3}]");
        switchOn("A");
        // the plan doesn't build the clay pit, nor the woodcutter's level 4
        player.demolishOk(BuildingType.CLAY_PIT);
        player.demolishOk(BuildingType.WOODCUTTER);
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.pausedByCancel").value(false));
        // level 3 it would build again
        player.demolishOk(BuildingType.WOODCUTTER);
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.pausedByCancel").value(true));
    }

    @Test
    void demolishingALevelOfTheMinesLinePausesItToo() throws Exception {
        unlock();
        setLevel(BuildingType.IRON_MINE, 4);
        savePlan("A", "[{\"building\":null,\"level\":3,\"recruit\":null,\"mines\":true}]");
        switchOn("A");
        player.demolishOk(BuildingType.IRON_MINE); // 4 → 3: the line wants level 3, not more
        player.browser.get(automation()).andExpect(jsonPath("$.enabled").value(true));
        player.demolishOk(BuildingType.IRON_MINE); // 3 → 2: the line builds it again
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.pausedByCancel").value(true));
    }

    @Test
    void cancellingAnOrderOfTheAutomationPausesIt() throws Exception {
        unlock();
        setStock(900);
        savePlan("A", "[{\"building\":\"WOODCUTTER\",\"level\":3}]");
        switchOn("A");
        look();
        long orderId =
                TestPlayer.number(TestPlayer.body(player.village()), "$.buildQueue[1].id")
                        .longValue();
        player.cancel(orderId).andExpect(status().isNoContent());
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.pausedByCancel").value(true));
        // its next look does nothing and schedules none
        clock.advance(Duration.ofHours(1));
        look();
        assertThat(pendingLooks()).isEmpty();

        switchOn("A");
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.pausedByCancel").value(false));
    }

    // ---- recruiting ----

    @Test
    void fromTheRecruitLineOnTheTemplateIsKeptFilledWithoutNobles() throws Exception {
        unlock();
        setLevel(BuildingType.HEADQUARTERS, 3);
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WAREHOUSE, 10);
        setStock(5000);
        player.browser
                .put(
                        "/api/recruit-templates/B",
                        "{\"name\":\"Deff\",\"units\":{\"SPEARMAN\":20,\"NOBLE\":1}}")
                .andExpect(status().isOk());
        savePlan(
                "A",
                "[{\"building\":\"WOODCUTTER\",\"level\":1},{\"level\":0,\"recruit\":\"B\"},"
                        + "{\"building\":\"WOODCUTTER\",\"level\":30}]");
        switchOn("A");
        look();
        player.recruitment()
                .andExpect(jsonPath("$[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[0].queue", hasSize(1)))
                .andExpect(jsonPath("$[0].queue[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$[0].queue[0].count").value(20));
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.recruiting").value("B"))
                .andExpect(jsonPath("$.byHand[0]").value("NOBLE"))
                // units in training count as had
                .andExpect(jsonPath("$.missing.SPEARMAN").doesNotExist());

        // nothing more while the goal is had; losses are recruited again
        clock.advance(Duration.ofDays(1));
        look();
        player.recruitment().andExpect(jsonPath("$[0].queue", hasSize(0)));
        jdbc.sql(
                        "update unit_stack set u_spearman = 15 where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .update();
        player.browser.get(automation()).andExpect(jsonPath("$.missing.SPEARMAN").value(5));
        setStock(5000);
        clock.advance(Duration.ofHours(1));
        look();
        player.recruitment().andExpect(jsonPath("$[0].queue[0].count").value(5));
    }

    @Test
    void aUnitTheVillageCannotRecruitYetNamesWhatItNeeds() throws Exception {
        unlock();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WAREHOUSE, 10);
        setStock(5000);
        player.browser
                .put(
                        "/api/recruit-templates/B",
                        "{\"name\":\"Mix\",\"units\":{\"SPEARMAN\":10,\"SWORDSMAN\":10}}")
                .andExpect(status().isOk());
        savePlan("A", "[{\"level\":0,\"recruit\":\"B\"}]");
        switchOn("A");
        look();
        player.browser
                .get(automation())
                .andExpect(jsonPath("$.missing.SWORDSMAN").value(10))
                .andExpect(jsonPath("$.missing.SPEARMAN").doesNotExist())
                .andExpect(jsonPath("$.blocked.SWORDSMAN[0].building").value("SMITHY"))
                .andExpect(jsonPath("$.blocked.SWORDSMAN[0].level").value(1))
                .andExpect(jsonPath("$.blocked.SPEARMAN").doesNotExist());
    }

    @Test
    void openingTheStatusAfterLossesLooksAtOnceInsteadOfAtTheFallback() throws Exception {
        unlock();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WAREHOUSE, 10);
        setStock(5000);
        player.browser
                .put("/api/recruit-templates/B", "{\"name\":\"Deff\",\"units\":{\"SPEARMAN\":20}}")
                .andExpect(status().isOk());
        savePlan("A", "[{\"level\":0,\"recruit\":\"B\"}]");
        switchOn("A");
        look();
        player.recruitment().andExpect(jsonPath("$[0].queue[0].count").value(20));
        clock.advance(Duration.ofDays(1));
        look();
        jdbc.sql(
                        "update unit_stack set u_spearman = 15 where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .update();
        setStock(5000);
        // the next look is up to 30 minutes away; opening the status moves it to now
        player.browser.get(automation()).andExpect(jsonPath("$.missing.SPEARMAN").value(5));
        assertThat(pendingLooks()).hasSize(1);
        assertThat(pendingLooks().getFirst()).isBefore(clock.instant().plusSeconds(5));
        clock.advance(Duration.ofSeconds(2));
        look();
        player.recruitment().andExpect(jsonPath("$[0].queue[0].count").value(5));
    }
}
