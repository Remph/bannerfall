package de.bannerfall.statistics;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Statistics (1.5, 02-game-rules §24): what the game counts, the ranking tabs, the profile, the
 * nightly snapshot and the history. Battles as in {@code AchievementFlowTest}: luck off, units and
 * stock set with SQL, the clock moved by hand and the event worker called directly.
 */
@IntegrationTest
class StatisticsFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;
    @Autowired TestRandomSource dice;
    @Autowired StatisticsService statistics;
    @Autowired PlatformTransactionManager transactionManager;

    private double luckRangeBefore;
    private int nobleMaxDistanceBefore;
    private Instant sinceBefore;
    private TestPlayer attacker;
    private TestPlayer defender;
    private Coordinates defenderAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        sinceBefore = statistics.since();
        jdbc.sql("update world_settings set luck_range = 0, noble_max_distance = 1000").update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        defenderAt = coordinates(defender);
        setUnits(defender, Map.of());
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql("update world_settings set luck_range = ?, noble_max_distance = ?")
                .params(luckRangeBefore, nobleMaxDistanceBefore)
                .update();
        worldService.reload();
        jdbc.sql("delete from player_day").update();
        jdbc.sql("delete from world_day").update();
        jdbc.sql("update statistics_since set since = ?")
                .param(Timestamp.from(sinceBefore))
                .update();
    }

    // ---- counting ----

    @Test
    void everyAttackCountsForBothSidesAndShowsOnTheProfile() throws Exception {
        setStock(defender, 300, 300, 300);
        arriveAt(attackWithAxemen(20));
        // a second attack on the same village right after counts too (achievements wouldn't)
        assertThat(stat(attacker, Stat.LOOT_TAKEN)).isEqualTo(300);
        setStock(defender, 0, 0, 0);
        arriveAt(attackWithAxemen(20));

        assertThat(stat(attacker, Stat.ATTACKS_WON)).isEqualTo(2);
        assertThat(stat(defender, Stat.DEFENCES_LOST)).isEqualTo(2);
        // what grew there on the way is the second loot
        long loot = stat(attacker, Stat.LOOT_TAKEN);
        assertThat(loot).isGreaterThan(300);
        assertThat(stat(defender, Stat.LOOT_LOST)).isEqualTo(loot);
        assertThat(stat(attacker, Stat.UNITS_LOST)).isZero();

        // everybody sees the numbers, the attacker's own as well as the defender's
        defender.browser
                .get("/api/players/" + attacker.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.attacksWon").value(2))
                .andExpect(jsonPath("$.stats.lootTaken").value(loot))
                .andExpect(jsonPath("$.stats.defencesLost").value(0))
                .andExpect(jsonPath("$.stats.since").isNotEmpty());
        attacker.browser
                .get("/api/players/" + defender.playerId)
                .andExpect(jsonPath("$.stats.defencesLost").value(2))
                .andExpect(jsonPath("$.stats.lootLost").value(loot));
    }

    @Test
    void aDefenceKillsTheAttackersAndCountsTheLossesInPopulation() throws Exception {
        setUnits(defender, Map.of(UnitType.SPEARMAN, 200));
        arriveAt(attackWithAxemen(20));

        long pop = gameData.unit(UnitType.AXEMAN).pop();
        assertThat(stat(attacker, Stat.ATTACKS_LOST)).isEqualTo(1);
        assertThat(stat(attacker, Stat.UNITS_LOST)).isEqualTo(20 * pop);
        assertThat(stat(defender, Stat.DEFENCES_WON)).isEqualTo(1);
        assertThat(stat(defender, Stat.KILLED_DEFENDING)).isEqualTo(20 * pop);
        assertThat(stat(attacker, Stat.LOOT_TAKEN)).isZero();
    }

    @Test
    void aConquestCountsForTheConquerorAndTheLoser() throws Exception {
        dice.use(FixedRandomSource.constant(0.99));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.NOBLE, 3));

        arriveAt(attack("{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(stat(attacker, Stat.VILLAGES_CONQUERED)).isEqualTo(1);
        assertThat(stat(defender, Stat.VILLAGES_LOST)).isEqualTo(1);
    }

    @Test
    void recruitedUnitsAndFinishedLevelsCount() throws Exception {
        recruitOrder(attacker, UnitType.SPEARMAN, 5, clock.instant().minusSeconds(10));
        attacker.village().andExpect(status().isOk());
        assertThat(stat(attacker, Stat.UNITS_RECRUITED)).isEqualTo(5);

        jdbc.sql("update village set b_headquarters = 9 where id = ?")
                .param(attacker.villageId)
                .update();
        buildOrder(attacker, BuildingType.HEADQUARTERS, 10, 60);
        arriveAt(clock.instant().plusSeconds(60));
        assertThat(stat(attacker, Stat.BUILDING_LEVELS)).isEqualTo(1);
    }

    // ---- the ranking tabs ----

    @Test
    void theRankingTabsSortByTheirTotal() throws Exception {
        setStock(defender, 300, 300, 300);
        arriveAt(attackWithAxemen(20));

        String plunderers =
                body(
                        attacker.browser
                                .get("/api/ranking?by=PLUNDERER&size=100")
                                .andExpect(status().isOk()));
        List<Integer> values = JsonPath.read(plunderers, "$.entries[*].value");
        assertThat(values).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        assertThat(
                        JsonPath.<List<Integer>>read(
                                plunderers,
                                "$.entries[?(@.playerId == " + attacker.playerId + ")].value"))
                .containsExactly(300);
        // equal totals share a rank: every player without loot has the same one
        List<Integer> zeroRanks = JsonPath.read(plunderers, "$.entries[?(@.value == 0)].rank");
        assertThat(zeroRanks).isNotEmpty().containsOnly(zeroRanks.getFirst());

        // the points tab is the ranking as before, its value the points
        String points = body(attacker.browser.get("/api/ranking?size=100"));
        assertThat(JsonPath.<List<Integer>>read(points, "$.entries[*].value"))
                .isEqualTo(JsonPath.<List<Integer>>read(points, "$.entries[*].points"));

        attacker.browser.get("/api/ranking?by=NOPE").andExpect(status().isBadRequest());
    }

    // ---- snapshot and history ----

    @Test
    void theSnapshotWritesADayOnceAndTheHistoryEndsWithToday() throws Exception {
        setStock(defender, 300, 300, 300);
        arriveAt(attackWithAxemen(20));
        LocalDate today = statistics.today();
        LocalDate yesterday = today.minusDays(1);
        // nothing before the statistics started
        jdbc.sql("update statistics_since set since = ?")
                .param(Timestamp.from(clock.instant()))
                .update();
        assertThat(statistics.snapshot(yesterday)).isFalse();

        jdbc.sql("update statistics_since set since = ?")
                .param(Timestamp.from(clock.instant().minus(Duration.ofDays(3))))
                .update();
        assertThat(statistics.snapshot(yesterday)).isTrue();
        assertThat(statistics.snapshot(yesterday)).isFalse();

        String history =
                body(defender.browser.get("/api/players/" + attacker.playerId + "/history"));
        assertThat(JsonPath.<List<String>>read(history, "$.days[*].day"))
                .containsExactly(yesterday.toString(), today.toString());
        int points =
                jdbc.sql("select points from player where id = ?")
                        .param(attacker.playerId)
                        .query(Integer.class)
                        .single();
        assertThat(JsonPath.<Integer>read(history, "$.days[1].points")).isEqualTo(points);
        assertThat(JsonPath.<Integer>read(history, "$.days[0].villages")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(history, "$.days[0].rank")).isPositive();
        // the day keeps a copy of the totals for the balancing export
        String copy =
                jdbc.sql("select stats::text from player_day where player_id = ? and day = ?")
                        .params(attacker.playerId, java.sql.Date.valueOf(yesterday))
                        .query(String.class)
                        .single();
        assertThat(JsonPath.<Integer>read(copy, "$.LOOT_TAKEN")).isEqualTo(300);

        String world =
                body(attacker.browser.get("/api/statistics/world").andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(world, "$.days[*].day"))
                .containsExactly(yesterday.toString(), today.toString());
        assertThat(JsonPath.<Integer>read(world, "$.days[0].players")).isPositive();
        assertThat(JsonPath.<List<Object>>read(world, "$.leaders"))
                .hasSizeBetween(1, StatisticsService.LEADERS);
        assertThat(JsonPath.<List<String>>read(world, "$.leaders[0].days[*].day"))
                .endsWith(today.toString());
    }

    @Test
    void aDeletedOrUnknownPlayerHasNoHistory() throws Exception {
        attacker.browser.get("/api/players/999999/history").andExpect(status().isNotFound());
        jdbc.sql(
                        "update account set disabled = true where id = (select account_id from"
                                + " player where id = ?)")
                .param(defender.playerId)
                .update();
        attacker.browser
                .get("/api/players/" + defender.playerId + "/history")
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private long stat(TestPlayer player, Stat stat) {
        return jdbc.sql(
                        "select coalesce(max(value), 0) from player_stat"
                                + " where player_id = ? and stat = ?")
                .params(player.playerId, stat.name())
                .query(Long.class)
                .single();
    }

    private Instant attackWithAxemen(int count) throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, count));
        return attack("{\"AXEMAN\":" + count + "}");
    }

    private Instant attack(String unitsJson) throws Exception {
        String json =
                "{\"kind\":\"ATTACK\",\"target\":{\"x\":"
                        + defenderAt.x()
                        + ",\"y\":"
                        + defenderAt.y()
                        + "},\"units\":"
                        + unitsJson
                        + "}";
        String sentJson =
                body(
                        attacker.browser
                                .post(attacker.villagePath() + "/commands", json)
                                .andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sentJson, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set u_"
                                    + type.name().toLowerCase(Locale.ROOT)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    private void setStock(TestPlayer player, int wood, int clay, int iron) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(wood, clay, iron, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    /** A queued build order whose event finishes it after {@code seconds}. */
    private void buildOrder(
            TestPlayer player, BuildingType building, int targetLevel, int seconds) {
        Instant start = clock.instant();
        long id =
                jdbc.sql(
                                """
                                insert into build_order (village_id, building, target_level,
                                    duration_seconds, start_at, end_at, cost_wood, cost_clay,
                                    cost_iron, cost_pop, created_at)
                                values (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?) returning id
                                """)
                        .params(
                                player.villageId,
                                building.name(),
                                targetLevel,
                                seconds,
                                Timestamp.from(start),
                                Timestamp.from(start.plusSeconds(seconds)),
                                Timestamp.from(start))
                        .query(Long.class)
                        .single();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                scheduler.schedule(
                                        GameEventKind.BUILD_COMPLETE,
                                        id,
                                        start.plusSeconds(seconds)));
    }

    /** A recruit order that makes one unit per second, free of cost. */
    private void recruitOrder(TestPlayer player, UnitType unit, int count, Instant startAt) {
        jdbc.sql(
                        """
                        insert into recruit_order (village_id, building, unit, count,
                            completed_count, seconds_per_unit, start_at, end_at, unit_cost_wood,
                            unit_cost_clay, unit_cost_iron, unit_pop, created_at)
                        values (?, ?, ?, ?, 0, 1, ?, ?, 0, 0, 0, 1, ?)
                        """)
                .params(
                        player.villageId,
                        gameData.unit(unit).building().name(),
                        unit.name(),
                        count,
                        Timestamp.from(startAt),
                        Timestamp.from(startAt.plusSeconds(count)),
                        Timestamp.from(startAt))
                .update();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
