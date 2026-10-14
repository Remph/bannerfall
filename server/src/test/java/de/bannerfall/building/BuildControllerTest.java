package de.bannerfall.building;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestGameData;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.event.GameEventRepository;
import de.bannerfall.event.GameEventStatus;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.rules.BuildRules;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** The build endpoints: queueing, the rule checks and their error codes, the overview (M4b). */
@IntegrationTest
class BuildControllerTest {

    private static final double EPSILON = 1e-9;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired GameEventRepository events;

    private final GameData data = TestGameData.load();
    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- queueing ----

    @Test
    void queueingAnUpgradePaysTheCostAndCountsThePopulation() throws Exception {
        ResourceAmounts cost = BuildRules.cost(data.building(BuildingType.WOODCUTTER), 2);
        assertThat(cost).isEqualTo(new ResourceAmounts(63, 75, 50)); // 02-game-rules §4.2
        long seconds = BuildRules.buildSeconds(data.building(BuildingType.WOODCUTTER), 2, 1, 1.0);
        Instant now = clock.instant();

        player.enqueue(BuildingType.WOODCUTTER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.building").value("WOODCUTTER"))
                .andExpect(jsonPath("$.targetLevel").value(2))
                .andExpect(jsonPath("$.startAt").value(now.toString()))
                .andExpect(jsonPath("$.endAt").value(now.plusSeconds(seconds).toString()));

        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(500 - 63, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(500 - 75, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(500 - 50, EPSILON)))
                // Town Hall 5 + woodcutter 5 + clay pit 10 + iron mine 10, plus 1 for the queued
                // woodcutter level 2 (5 → round(5 × 1.17) = 6)
                .andExpect(jsonPath("$.population.used").value(31))
                // the building keeps its level and its production until the order is done
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(1))
                .andExpect(jsonPath("$.production.wood", closeTo(30, EPSILON)));
    }

    @Test
    void theQueueIsPartOfTheVillage() throws Exception {
        player.village().andExpect(jsonPath("$.buildQueue").isEmpty());

        long id = player.enqueueOk(BuildingType.CLAY_PIT);

        player.village()
                .andExpect(jsonPath("$.buildQueue.length()").value(1))
                .andExpect(jsonPath("$.buildQueue[0].id").value(id))
                .andExpect(jsonPath("$.buildQueue[0].building").value("CLAY_PIT"))
                .andExpect(jsonPath("$.buildQueue[0].targetLevel").value(2))
                .andExpect(jsonPath("$.buildQueue[0].startAt").isString())
                .andExpect(jsonPath("$.buildQueue[0].endAt").isString());
    }

    @Test
    void ordersRunOneAfterAnotherAndTheTownHallLevelOfTheMomentSetsTheTime() throws Exception {
        long first = player.enqueueOk(BuildingType.HEADQUARTERS);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);

        String village = body(player.village());
        assertThat(number(village, "$.buildQueue[0].id").longValue()).isEqualTo(first);
        assertThat(number(village, "$.buildQueue[1].id").longValue()).isEqualTo(second);
        Instant firstStart = instant(village, "$.buildQueue[0].startAt");
        Instant firstEnd = instant(village, "$.buildQueue[0].endAt");
        Instant secondStart = instant(village, "$.buildQueue[1].startAt");
        Instant secondEnd = instant(village, "$.buildQueue[1].endAt");

        assertThat(firstStart).isEqualTo(clock.instant());
        // Town Hall 1 → 2 and clay pit 1 → 2, both with Town Hall level 1: the queued Town Hall
        // upgrade does not make the second order faster (02-game-rules §4.2).
        assertThat(firstEnd)
                .isEqualTo(
                        firstStart.plusSeconds(
                                BuildRules.buildSeconds(
                                        data.building(BuildingType.HEADQUARTERS), 2, 1, 1.0)));
        assertThat(secondStart).isEqualTo(firstEnd);
        assertThat(secondEnd)
                .isEqualTo(
                        secondStart.plusSeconds(
                                BuildRules.buildSeconds(
                                        data.building(BuildingType.CLAY_PIT), 2, 1, 1.0)));
    }

    @Test
    void queueingSchedulesExactlyOneCompletionEventAtTheEndOfTheOrder() throws Exception {
        long id = player.enqueueOk(BuildingType.IRON_MINE);

        Instant end = instant(body(player.village()), "$.buildQueue[0].endAt");
        List<GameEvent> scheduled = eventsOf(id);
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.get(0).getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(scheduled.get(0).getDueAt()).isEqualTo(end);
    }

    // ---- rule checks ----

    @Test
    void theQueueHoldsTenOrdersWithThePaidPlaces() throws Exception {
        makeRich();
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.CLAY_PIT);
        for (int i = 0; i < 7; i++) {
            player.enqueueOk(BuildingType.FARM); // needs no population
        }

        player.enqueue(BuildingType.IRON_MINE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
        player.village().andExpect(jsonPath("$.buildQueue.length()").value(10));
    }

    @Test
    void aBuildingAtItsMaximumLevelCanNotBeUpgraded() throws Exception {
        makeRich();

        // the rally point is built at start and has maximum level 1
        player.enqueue(BuildingType.RALLY_POINT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_LEVEL_REACHED"));
    }

    @Test
    void queuedLevelsCountForTheMaximumLevel() throws Exception {
        makeRich();
        setBuildingLevel("b_hiding_place", 9); // maximum level 10

        player.enqueueOk(BuildingType.HIDING_PLACE); // → 10
        player.enqueue(BuildingType.HIDING_PLACE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_LEVEL_REACHED"));
    }

    @Test
    void requirementsAreCheckedAgainstTheCurrentLevels() throws Exception {
        makeRich();

        // barracks need Town Hall 3, the village has 1
        player.enqueue(BuildingType.BARRACKS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void queuedLevelsCountForTheRequirements() throws Exception {
        makeRich();
        setBuildingLevel("b_headquarters", 2);
        player.enqueue(BuildingType.BARRACKS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        player.enqueueOk(BuildingType.HEADQUARTERS); // queued level 3

        player.enqueue(BuildingType.BARRACKS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetLevel").value(1));
    }

    @Test
    void theStockIsComparedRoundedDown() throws Exception {
        // woodcutter 2 costs 63 / 75 / 50
        setStock(62.999, 1000, 1000);
        player.enqueue(BuildingType.WOODCUTTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        player.village().andExpect(jsonPath("$.buildQueue").isEmpty());

        setStock(63.0, 75.0, 50.0);
        player.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated());
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(0, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(0, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(0, EPSILON)));
    }

    @Test
    void anOrderThatNeedsMorePopulationThanIsFreeIsRefused() throws Exception {
        makeRich();
        // Farm level 1 holds 240; buildings use 30. 210 spearmen use the rest.
        setHomeSpearmen(210);
        player.enqueue(BuildingType.WOODCUTTER) // +1 population
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));

        setHomeSpearmen(209);
        player.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated());
        player.village().andExpect(jsonPath("$.population.used").value(240));
    }

    @Test
    void queuedUpgradesUsePopulationBeforeTheyAreDone() throws Exception {
        makeRich();
        setHomeSpearmen(208); // 238 of 240 used: room for two single points
        player.enqueueOk(BuildingType.WOODCUTTER); // +1
        player.enqueueOk(BuildingType.WOODCUTTER); // 6 → round(5 × 1.17²) = 7: +1

        player.enqueue(BuildingType.WOODCUTTER) // 7 → round(5 × 1.17³) = 8: +1, but 240 is full
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));
    }

    @Test
    void theQueueIsCheckedBeforeEverythingElse() throws Exception {
        makeRich();
        for (int i = 0; i < 10; i++) {
            player.enqueueOk(BuildingType.FARM);
        }
        setStock(0, 0, 0);

        player.enqueue(BuildingType.RALLY_POINT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
    }

    // ---- request handling ----

    @Test
    void foreignVillagesAreNotFound() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        String path = player.villagePath();

        other.browser
                .post(path + "/build-orders", "{\"building\":\"WOODCUTTER\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .delete(path + "/build-orders/" + order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(path + "/build-orders/" + order + "/move", "{\"position\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .get(path + "/buildings")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        // nothing happened to the order
        player.village().andExpect(jsonPath("$.buildQueue.length()").value(1));
    }

    @Test
    void theBuildEndpointsNeedLogin() throws Exception {
        Browser anonymous = Browser.open(mockMvc);
        String path = player.villagePath();

        anonymous.get(path + "/buildings").andExpect(status().isUnauthorized());
        anonymous
                .post(path + "/build-orders", "{\"building\":\"WOODCUTTER\"}")
                .andExpect(status().isUnauthorized());
        anonymous.delete(path + "/build-orders/1").andExpect(status().isUnauthorized());
        anonymous
                .post(path + "/build-orders/1/move", "{\"position\":1}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theNewPlaceOfAMovedOrderMustBeGiven() throws Exception {
        player.enqueueOk(BuildingType.WOODCUTTER);
        long order = player.enqueueOk(BuildingType.CLAY_PIT);
        String path = player.villagePath() + "/build-orders/" + order + "/move";

        player.browser
                .post(path, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("position"));
        player.browser
                .post(path, "{\"position\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("position"));
        player.village().andExpect(jsonPath("$.buildQueue[1].id").value(order));
    }

    @Test
    void theBuildingMustBeNamed() throws Exception {
        String path = player.villagePath() + "/build-orders";

        player.browser
                .post(path, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("building"));
        player.browser
                .post(path, "{\"building\":\"CASTLE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.village().andExpect(jsonPath("$.buildQueue").isEmpty());
    }

    @Test
    void devCheatsDoNotExistWithoutTheDevProfile() throws Exception {
        player.browser
                .post(
                        "/api/dev/villages/" + player.villageId + "/resources",
                        "{\"wood\":1000,\"clay\":0,\"iron\":0}")
                .andExpect(status().isNotFound());
        player.browser
                .post(
                        "/api/dev/villages/" + player.villageId + "/units",
                        "{\"units\":{\"SPEARMAN\":1}}")
                .andExpect(status().isNotFound());
        player.browser
                .post("/api/dev/villages/" + player.villageId + "/finish-orders", "")
                .andExpect(status().isNotFound());
        player.browser
                .post("/api/dev/barbarians", "{\"x\":1,\"y\":1}")
                .andExpect(status().isNotFound());
        player.browser.get("/api/dev/fail").andExpect(status().isNotFound());
    }

    // ---- the overview ----

    @Test
    void theOverviewListsEveryBuildingInOrder() throws Exception {
        String overview = body(player.buildings().andExpect(status().isOk()));

        List<String> names = JsonPath.read(overview, "$[*].building");
        assertThat(names)
                .containsExactlyElementsOf(
                        Arrays.stream(BuildingType.values()).map(Enum::name).toList());
    }

    @Test
    void theOverviewShowsTheNextUpgradeOfABuilding() throws Exception {
        long seconds = BuildRules.buildSeconds(data.building(BuildingType.WOODCUTTER), 2, 1, 1.0);

        player.buildings()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[8].building").value("WOODCUTTER"))
                .andExpect(jsonPath("$[8].level").value(1))
                .andExpect(jsonPath("$[8].queuedLevel").value(1))
                .andExpect(jsonPath("$[8].maxLevel").value(30))
                .andExpect(jsonPath("$[8].next.level").value(2))
                .andExpect(jsonPath("$[8].next.cost.wood").value(63))
                .andExpect(jsonPath("$[8].next.cost.clay").value(75))
                .andExpect(jsonPath("$[8].next.cost.iron").value(50))
                .andExpect(jsonPath("$[8].next.pop").value(1))
                .andExpect(jsonPath("$[8].next.durationSeconds").value(seconds))
                .andExpect(jsonPath("$[8].requirementsMet").value(true))
                .andExpect(jsonPath("$[8].missing").isEmpty());
    }

    @Test
    void theOverviewSaysWhatTheNextUpgradeChanges() throws Exception {
        player.buildings()
                .andExpect(jsonPath("$[8].building").value("WOODCUTTER"))
                .andExpect(jsonPath("$[8].next.effects.length()").value(1))
                .andExpect(jsonPath("$[8].next.effects[0].kind").value("PRODUCTION_PER_HOUR"))
                .andExpect(jsonPath("$[8].next.effects[0].from").value(closeTo(30.0, 0.001)))
                .andExpect(jsonPath("$[8].next.effects[0].to").value(closeTo(34.95, 0.001)))
                // the Smithy only unlocks things
                .andExpect(jsonPath("$[5].building").value("SMITHY"))
                .andExpect(jsonPath("$[5].next.effects").isEmpty());
    }

    @Test
    void thePreviewFollowsTheQueuedLevelsAndThePaidSlotPrice() throws Exception {
        makeRich();
        setBuildingLevel("b_headquarters", 2);
        player.enqueueOk(BuildingType.WOODCUTTER); // queued level 2
        player.enqueueOk(BuildingType.WOODCUTTER); // queued level 3
        player.enqueueOk(BuildingType.HEADQUARTERS); // queued level 3: the Barracks are open
        player.enqueueOk(BuildingType.CLAY_PIT);
        player.enqueueOk(BuildingType.FARM); // 5 orders: the next one takes the first paid slot

        player.buildings()
                // the woodcutter preview starts from the queued level 3, not from level 1: 30 ×
                // 1.165²
                .andExpect(jsonPath("$[8].queuedLevel").value(3))
                .andExpect(jsonPath("$[8].next.level").value(4))
                .andExpect(jsonPath("$[8].next.effects[0].from").value(closeTo(40.716750, 0.0001)))
                .andExpect(jsonPath("$[8].next.effects[0].to").value(closeTo(47.435014, 0.0001)))
                // the queued Town Hall level 3 already opened the Barracks: level 4 unlocks nothing
                // new
                .andExpect(jsonPath("$[0].next.level").value(4))
                .andExpect(jsonPath("$[0].next.unlocksBuildings").isEmpty())
                // the cost is the price of the next free place, a paid slot's surcharge included
                .andExpect(jsonPath("$[8].next.surchargePercent").value(25));
    }

    @Test
    void theOverviewNamesWhatTheNextUpgradeUnlocks() throws Exception {
        setBuildingLevel("b_headquarters", 2);

        player.buildings()
                // Town Hall 3 is all the Barracks need
                .andExpect(jsonPath("$[0].next.level").value(3))
                .andExpect(jsonPath("$[0].next.unlocksBuildings[0]").value("BARRACKS"))
                .andExpect(jsonPath("$[0].next.unlocksUnits").isEmpty())
                // Barracks 1 opens the Wall and the Spearman; the Swordsman also needs the Smithy
                .andExpect(jsonPath("$[1].next.unlocksBuildings[0]").value("WALL"))
                .andExpect(jsonPath("$[1].next.unlocksUnits[0]").value("SPEARMAN"))
                .andExpect(jsonPath("$[1].next.unlocksUnits.length()").value(1))
                // a building nothing waits for lists nothing
                .andExpect(jsonPath("$[8].next.unlocksBuildings").isEmpty())
                .andExpect(jsonPath("$[8].next.unlocksUnits").isEmpty());
    }

    @Test
    void theOverviewListsMissingRequirements() throws Exception {
        player.buildings()
                .andExpect(jsonPath("$[1].building").value("BARRACKS"))
                .andExpect(jsonPath("$[1].level").value(0))
                .andExpect(jsonPath("$[1].requirementsMet").value(false))
                .andExpect(jsonPath("$[1].missing.length()").value(1))
                .andExpect(jsonPath("$[1].missing[0].building").value("HEADQUARTERS"))
                .andExpect(jsonPath("$[1].missing[0].level").value(3))
                // still shows what the first level would cost
                .andExpect(jsonPath("$[1].next.level").value(1))
                .andExpect(jsonPath("$[1].next.cost.wood").value(200));
    }

    @Test
    void theOverviewCountsQueuedLevels() throws Exception {
        makeRich();
        setBuildingLevel("b_headquarters", 2);
        player.enqueueOk(BuildingType.HEADQUARTERS);
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.WOODCUTTER);

        player.buildings()
                // Town Hall: 2 now, 3 with the queue; the next upgrade is level 4
                .andExpect(jsonPath("$[0].level").value(2))
                .andExpect(jsonPath("$[0].queuedLevel").value(3))
                .andExpect(jsonPath("$[0].next.level").value(4))
                .andExpect(jsonPath("$[8].level").value(1))
                .andExpect(jsonPath("$[8].queuedLevel").value(3))
                .andExpect(jsonPath("$[8].next.level").value(4))
                // barracks only need the queued Town Hall level 3
                .andExpect(jsonPath("$[1].requirementsMet").value(true))
                .andExpect(jsonPath("$[1].missing").isEmpty());
    }

    @Test
    void aBuildingAtItsMaximumLevelHasNoNextUpgrade() throws Exception {
        player.buildings()
                .andExpect(jsonPath("$[6].building").value("RALLY_POINT"))
                .andExpect(jsonPath("$[6].level").value(1))
                .andExpect(jsonPath("$[6].maxLevel").value(1))
                .andExpect(jsonPath("$[6].next").value(nullValue()));
    }

    // ---- helpers ----

    private void makeRich() {
        setStock(100_000, 100_000, 100_000);
    }

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(wood, clay, iron, player.villageId)
                .update();
    }

    /** {@code column} is one of the fixed {@code b_*} names of the test, never user input. */
    private void setBuildingLevel(String column, int level) {
        jdbc.sql("update village set " + column + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setHomeSpearmen(int count) {
        jdbc.sql("update unit_stack set u_spearman = ? where home_village_id = ?")
                .params(count, player.villageId)
                .update();
    }

    private List<GameEvent> eventsOf(long orderId) {
        return events.findAll().stream()
                .filter(e -> e.getKind() == GameEventKind.BUILD_COMPLETE)
                .filter(e -> e.getRefId() != null && e.getRefId() == orderId)
                .toList();
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
