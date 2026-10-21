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
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Population;
import de.bannerfall.rules.Storage;
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Demolishing a building (1.6, 02-game-rules §4.5): one level per order in the build queue, free, a
 * fifth of the build time, the level goes when the order is done. The clock is moved by hand and
 * the event worker is called by hand.
 */
@IntegrationTest
class DemolishTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;

    private final GameData data = TestGameData.load();
    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- queueing and completion ----

    @Test
    void aDemolitionIsFreeTakesAFifthOfTheBuildTimeAndTheLevelGoesWhenItIsDone() throws Exception {
        setLevel(BuildingType.HEADQUARTERS, 5);
        setLevel(BuildingType.WALL, 3);
        String before = body(player.village());
        int popBefore = number(before, "$.population.used").intValue();

        long id = player.demolishOk(BuildingType.WALL);

        String queued = body(player.village());
        assertThat(number(queued, "$.buildQueue[0].id").longValue()).isEqualTo(id);
        assertThat(number(queued, "$.buildQueue[0].targetLevel").intValue()).isEqualTo(2);
        assertThat(JsonPath.<Boolean>read(queued, "$.buildQueue[0].demolish")).isTrue();
        long seconds = BuildRules.demolishSeconds(data.building(BuildingType.WALL), 3, 5, 1.0);
        Instant start = instant(queued, "$.buildQueue[0].startAt");
        assertThat(Duration.between(start, instant(queued, "$.buildQueue[0].endAt")).toSeconds())
                .isEqualTo(seconds);
        // nothing paid (only production since), and the population stays until it is done
        assertThat(number(queued, "$.resources.wood").doubleValue())
                .isGreaterThanOrEqualTo(number(before, "$.resources.wood").doubleValue());
        assertThat(number(queued, "$.population.used").intValue()).isEqualTo(popBefore);
        assertThat(number(queued, "$.buildings.WALL").intValue()).isEqualTo(3);

        clock.set(start.plusSeconds(seconds));
        assertThat(runWorker()).isEqualTo(1);

        String done = body(player.village());
        assertThat(number(done, "$.buildings.WALL").intValue()).isEqualTo(2);
        assertThat((List<?>) JsonPath.read(done, "$.buildQueue")).isEmpty();
        int freed =
                Population.buildingPop(data.building(BuildingType.WALL), 3)
                        - Population.buildingPop(data.building(BuildingType.WALL), 2);
        assertThat(number(done, "$.population.used").intValue()).isEqualTo(popBefore - freed);
        assertThat(number(done, "$.points").intValue()).isEqualTo(expectedPoints(done));
        // the player's points (the ranking) follow; the built levels of the statistics stay
        assertThat(playerPoints()).isEqualTo(number(done, "$.points").intValue());
        assertThat(buildingLevelsStat()).isEqualTo(0);
    }

    @Test
    void stockAboveTheSmallerWarehouseStaysAfterItsDemolition() throws Exception {
        setLevel(BuildingType.WAREHOUSE, 10);
        int full = Storage.warehouseCapacity(10);
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(full, full, full, player.villageId)
                .update();

        player.demolishOk(BuildingType.WAREHOUSE);
        finishQueue();

        String done = body(player.village());
        assertThat(number(done, "$.buildings.WAREHOUSE").intValue()).isEqualTo(9);
        assertThat(number(done, "$.storageCapacity").intValue())
                .isEqualTo(Storage.warehouseCapacity(9));
        // like after catapults (02-game-rules §3): nothing is lost, nothing more is produced
        assertThat(number(done, "$.resources.wood").doubleValue()).isEqualTo(full);
    }

    @Test
    void severalLevelsGoOneOrderAtATimeDownToZero() throws Exception {
        player.demolishOk(BuildingType.WOODCUTTER); // 1 → 0

        player.demolish(BuildingType.WOODCUTTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIN_LEVEL_REACHED"));
        finishQueue();
        player.village().andExpect(jsonPath("$.buildings.WOODCUTTER").value(0));
    }

    @Test
    void theTownHallFarmWarehouseAndRallyPointKeepLevelOneAndUnbuiltBuildingsCantGo()
            throws Exception {
        for (BuildingType type :
                List.of(
                        BuildingType.HEADQUARTERS,
                        BuildingType.FARM,
                        BuildingType.WAREHOUSE,
                        BuildingType.RALLY_POINT,
                        BuildingType.BARRACKS)) {
            player.demolish(type)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MIN_LEVEL_REACHED"));
        }
        // from level 2 the Town Hall can go down to 1, not further
        setLevel(BuildingType.HEADQUARTERS, 2);
        player.demolishOk(BuildingType.HEADQUARTERS);
        player.demolish(BuildingType.HEADQUARTERS)
                .andExpect(jsonPath("$.code").value("MIN_LEVEL_REACHED"));
    }

    @Test
    void aBuildingHasEitherUpgradesOrDemolitionsQueuedNeverBoth() throws Exception {
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.demolish(BuildingType.WOODCUTTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILD_ORDER_CONFLICT"));

        player.demolishOk(BuildingType.CLAY_PIT);
        player.enqueue(BuildingType.CLAY_PIT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILD_ORDER_CONFLICT"));
    }

    @Test
    void aDemolitionTakesAPlaceInTheQueue() throws Exception {
        int max = number(body(player.browser.get("/api/world")), "$.buildQueueMax").intValue();
        setLevel(BuildingType.WALL, max);

        for (int i = 0; i < max; i++) {
            player.demolishOk(BuildingType.WALL);
        }
        player.demolish(BuildingType.WALL).andExpect(jsonPath("$.code").value("QUEUE_FULL"));
        // the paid slots cost a share of nothing
        player.village().andExpect(jsonPath("$.resources.wood").value(500.0));
    }

    @Test
    void anUpgradeAfterADemolitionCountsTheLowerLevel() throws Exception {
        setLevel(BuildingType.BARRACKS, 1); // the wall needs Barracks 1
        player.demolishOk(BuildingType.BARRACKS);

        player.enqueue(BuildingType.WALL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    // ---- farm ----

    @Test
    void theFarmCantGoBelowThePopulationInUse() throws Exception {
        setLevel(BuildingType.FARM, 3);
        int used = number(body(player.village()), "$.population.used").intValue();
        // fill the farm up to one place more than a level 2 farm holds
        setUnits(Map.of(UnitType.SPEARMAN, Population.farmCapacity(2) - used + 1));

        player.demolish(BuildingType.FARM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));

        setUnits(Map.of(UnitType.SPEARMAN, Population.farmCapacity(2) - used));
        player.demolishOk(BuildingType.FARM);
    }

    @Test
    void whileAFarmDemolitionIsQueuedRecruitsCountTheSmallerFarm() throws Exception {
        setLevel(BuildingType.FARM, 3);
        setLevel(BuildingType.BARRACKS, 1);
        int used = number(body(player.village()), "$.population.used").intValue();
        setStock(100_000);
        player.demolishOk(BuildingType.FARM);

        // the village says so too, so the recruit page's maximum matches the server's
        player.village()
                .andExpect(jsonPath("$.population.capacity").value(Population.farmCapacity(2)));
        int free = Population.farmCapacity(2) - used;
        player.recruit(UnitType.SPEARMAN, free + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));
        player.recruit(UnitType.SPEARMAN, free).andExpect(status().isCreated());
    }

    // ---- cancel and move ----

    @Test
    void cancellingADemolitionRefundsNothingAndTakesTheLaterDemolitionsOfTheBuildingWithIt()
            throws Exception {
        setLevel(BuildingType.WALL, 3);
        long first = player.demolishOk(BuildingType.WALL);
        long second = player.demolishOk(BuildingType.WALL);
        long woodcutter = player.enqueueOk(BuildingType.WOODCUTTER);
        String before = body(player.village());

        player.cancel(first).andExpect(status().isNoContent());

        String after = body(player.village());
        assertThat(JsonPath.<List<Number>>read(after, "$.buildQueue[*].id"))
                .extracting(Number::longValue)
                .containsExactly(woodcutter)
                .doesNotContain(second);
        // the woodcutter moved up to now, nothing came back
        assertThat(instant(after, "$.buildQueue[0].startAt")).isEqualTo(clock.instant());
        assertThat(number(after, "$.resources.wood").doubleValue())
                .isEqualTo(number(before, "$.resources.wood").doubleValue());
    }

    @Test
    void aDemolitionCantMoveInFrontOfAnUpgradeThatNeedsTheLevel() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        player.enqueueOk(BuildingType.WOODCUTTER); // running
        player.enqueueOk(BuildingType.WALL); // needs Barracks 1
        long demolition = player.demolishOk(BuildingType.BARRACKS);

        player.move(demolition, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    // ---- the Town Hall's list ----

    @Test
    void theBuildingListNamesTheNextDemolitionWithWhatItFreesCostsAndLocks() throws Exception {
        setLevel(BuildingType.HEADQUARTERS, 3); // so the barracks can be upgraded below
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WALL, 1);
        String list = body(player.buildings());

        Map<String, Object> barracks = building(list, "BARRACKS");
        assertThat(barracks.get("demolish")).isNotNull();
        String demolish = "$[?(@.building == 'BARRACKS')].demolish";
        assertThat(JsonPath.<List<Integer>>read(list, demolish + ".level")).containsExactly(0);
        assertThat(JsonPath.<List<Integer>>read(list, demolish + ".pop"))
                .containsExactly(Population.buildingPop(data.building(BuildingType.BARRACKS), 1));
        assertThat(JsonPath.<List<Integer>>read(list, demolish + ".points"))
                .containsExactly(BuildRules.points(data.building(BuildingType.BARRACKS), 1));
        // the wall stands but can't be upgraded without the barracks; the infantry can't be
        // recruited
        assertThat(JsonPath.<List<List<String>>>read(list, demolish + ".locksBuildings"))
                .containsExactly(List.of("WALL"));
        assertThat(JsonPath.<List<List<String>>>read(list, demolish + ".locksUnits"))
                .containsExactly(List.of("SPEARMAN"));
        // the farm at level 1 and the rally point have none
        assertThat(building(list, "FARM").get("demolish")).isNull();
        assertThat(building(list, "RALLY_POINT").get("demolish")).isNull();

        // with an upgrade of the barracks queued it says why not
        player.enqueueOk(BuildingType.BARRACKS);
        String later = body(player.buildings());
        assertThat(JsonPath.<List<String>>read(later, demolish + ".blocker"))
                .containsExactly("BUILD_ORDER_CONFLICT");
    }

    @Test
    void aDemolitionOnlyWarnsOfArchersInAWorldWithArchers() throws Exception {
        setLevel(BuildingType.BARRACKS, 5);
        setLevel(BuildingType.SMITHY, 5); // the archer needs both at 5
        String smithy = "$[?(@.building == 'SMITHY')].demolish.locksUnits";
        try {
            setArchers(false);
            assertThat(JsonPath.<List<List<String>>>read(body(player.buildings()), smithy))
                    .containsExactly(List.of());
            setArchers(true);
            assertThat(JsonPath.<List<List<String>>>read(body(player.buildings()), smithy))
                    .containsExactly(List.of("ARCHER"));
        } finally {
            setArchers(false);
        }
    }

    // ---- helpers ----

    private void setArchers(boolean enabled) {
        jdbc.sql("update world_settings set archers_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> building(String list, String type) {
        return ((List<Map<String, Object>>)
                        JsonPath.read(list, "$[?(@.building == '" + type + "')]"))
                .get(0);
    }

    private void setLevel(BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setUnits(Map<UnitType, Integer> units) {
        units.forEach(
                (type, count) ->
                        jdbc.sql(
                                        "update unit_stack set u_"
                                                + type.name().toLowerCase(Locale.ROOT)
                                                + " = ? where home_village_id = ?"
                                                + " and location_village_id = home_village_id")
                                .params(count, player.villageId)
                                .update());
    }

    private void setStock(double amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, b_warehouse = 30"
                                + " where id = ?")
                .params(amount, amount, amount, player.villageId)
                .update();
    }

    private int playerPoints() {
        return jdbc.sql(
                        "select p.points from player p join village v on v.player_id = p.id"
                                + " where v.id = ?")
                .param(player.villageId)
                .query(Integer.class)
                .single();
    }

    private long buildingLevelsStat() {
        return jdbc.sql(
                        "select coalesce(sum(value), 0) from player_stat s"
                                + " join village v on v.player_id = s.player_id"
                                + " where v.id = ? and s.stat = 'BUILDING_LEVELS'")
                .param(player.villageId)
                .query(Long.class)
                .single();
    }

    private int expectedPoints(String village) {
        Map<String, Integer> levels = JsonPath.read(village, "$.buildings");
        int sum = 0;
        for (var entry : levels.entrySet()) {
            sum +=
                    BuildRules.points(
                            data.building(BuildingType.valueOf(entry.getKey())), entry.getValue());
        }
        return sum;
    }

    private void finishQueue() throws Exception {
        String village = body(player.village());
        List<String> ends = JsonPath.read(village, "$.buildQueue[*].endAt");
        clock.set(Instant.parse(ends.get(ends.size() - 1)));
        runWorker();
    }

    private int runWorker() {
        return processor.processDue(clock.instant());
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
