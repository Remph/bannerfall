package de.bannerfall.barbarian;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.Barbarians;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.MapPlacement;
import de.bannerfall.rules.Storage;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Barbarian villages (M10, 02-game-rules §2, §11): the initial spawn, the spawn around new players,
 * growth and the conversion of a deleted player's villages. The test world starts without
 * barbarians ({@code TestWorldConfiguration}); tests that change a setting put it back.
 */
@IntegrationTest
class BarbarianFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired BarbarianService barbarians;
    @Autowired VillageRepository villages;
    @Autowired UnitStackRepository stacks;
    @Autowired PlayerRepository players;
    @Autowired GameData gameData;
    @Autowired SentMessages sent;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- initial spawn ----

    @Test
    void theFirstStartSpawnsTheInitialBarbariansOnceAndSchedulesGrowth() {
        int initialCount = worldService.settings().barbarianInitialCount();
        jdbc.sql("update world_settings set initialized_at = null, barbarian_initial_count = 5")
                .update();
        worldService.reload();
        try {
            Set<Long> before = new HashSet<>(villages.findBarbarianIds());

            initializer.initialize();

            List<Long> spawned = newBarbarians(before);
            assertThat(spawned).hasSize(5);
            int center = worldService.settings().mapSize() / 2;
            for (long id : spawned) {
                Village village = villages.findById(id).orElseThrow();
                assertFreshBarbarian(village);
                // r = random(4, 30) around the center, + 0.25 per failed try, rounded to fields
                assertThat(village.coordinates().distanceTo(new Coordinates(center, center)))
                        .isBetween(3.0, 45.0);
            }
            assertSpacingOfAllVillages();
            assertThat(initializedAt()).isEqualTo(clock.instant());
            assertThat(pendingGrowthDueTimes()).containsExactly(clock.instant().plus(day()));

            // a restart: no new villages, still exactly one growth event
            Set<Long> afterFirst = new HashSet<>(villages.findBarbarianIds());
            clock.advance(Duration.ofMinutes(5));
            initializer.initialize();
            assertThat(newBarbarians(afterFirst)).isEmpty();
            assertThat(pendingGrowthDueTimes()).hasSize(1);
        } finally {
            jdbc.sql("update world_settings set barbarian_initial_count = ?")
                    .param(initialCount)
                    .update();
            worldService.reload();
        }
    }

    @Test
    void aStartWithoutGrowthEventSchedulesANewOne() {
        jdbc.sql("update world_settings set initialized_at = ?")
                .param(Timestamp.from(clock.instant()))
                .update();
        Set<Long> before = new HashSet<>(villages.findBarbarianIds());

        initializer.initialize();

        assertThat(newBarbarians(before)).isEmpty();
        assertThat(pendingGrowthDueTimes()).containsExactly(clock.instant().plus(day()));
    }

    // ---- spawn around new players ----

    @Test
    void barbariansSpawnAroundANewPlayer() throws Exception {
        setBarbariansPerNewPlayer(3);
        try {
            Set<Long> before = new HashSet<>(villages.findBarbarianIds());

            TestPlayer player = TestPlayer.register(mockMvc);

            List<Long> spawned = newBarbarians(before);
            assertThat(spawned).hasSize(3);
            Coordinates home = villages.findById(player.villageId).orElseThrow().coordinates();
            for (long id : spawned) {
                Village village = villages.findById(id).orElseThrow();
                assertFreshBarbarian(village);
                // r = random(3, 8), + 0.25 per failed try, rounded to fields
                assertThat(village.coordinates().distanceTo(home)).isBetween(2.0, 20.0);
            }
            assertSpacingOfAllVillages();
        } finally {
            setBarbariansPerNewPlayer(TestWorldConfiguration.BARBARIANS_PER_NEW_PLAYER);
        }
    }

    @Test
    void barbariansAreGreyOnTheMap() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        Village barbarian = barbarians.spawnAt(freeField());

        player.browser
                .get("/api/map?x=%d&y=%d&w=1&h=1".formatted(barbarian.getX(), barbarian.getY()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(barbarian.getId()))
                .andExpect(jsonPath("$[0].name").isEmpty())
                .andExpect(jsonPath("$[0].playerId").isEmpty())
                .andExpect(jsonPath("$[0].playerName").isEmpty())
                .andExpect(jsonPath("$[0].npc").value(false))
                .andExpect(jsonPath("$[0].points").value(barbarian.getPoints()));
    }

    // ---- growth ----

    @Test
    void growthRaisesOneMinePerVillageUpToTwelveAndReschedules() {
        long almost = barbarians.spawnAt(freeField()).getId();
        long full = barbarians.spawnAt(freeField()).getId();
        long fresh = barbarians.spawnAt(freeField()).getId();
        setMines(almost, 11, 12, 12);
        setMines(full, 12, 12, 12);
        int freshMinesBefore = mineSum(fresh);
        Instant due = clock.instant().plus(Duration.ofHours(1));
        scheduleGrowth(due);

        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());

        Village grown = villages.findById(almost).orElseThrow();
        assertThat(grown.getBuildings().get(BuildingType.WOODCUTTER)).isEqualTo(12);
        assertThat(grown.getPoints())
                .isEqualTo(BuildRules.villagePoints(gameData, grown.getBuildings().toMap()));
        // production up to the growth with the old levels, anchored at the due time
        assertThat(grown.getResourcesUpdatedAt()).isEqualTo(due);
        assertThat(mineSum(full)).isEqualTo(36);
        assertThat(mineSum(fresh)).isEqualTo(freshMinesBefore + 1);
        assertThat(pendingGrowthDueTimes()).containsExactly(due.plus(day()));
    }

    @Test
    void aLateWorkerCatchesUpOneRoundAfterTheOther() {
        long village = barbarians.spawnAt(freeField()).getId();
        setMines(village, 1, 1, 1);
        Instant due = clock.instant().plus(Duration.ofHours(1));
        scheduleGrowth(due);

        // due, +1 d, +2 d, +3 d are all past
        clock.advance(Duration.ofHours(1).plus(day().multipliedBy(3)));
        processor.processDue(clock.instant());

        assertThat(mineSum(village)).isEqualTo(3 + 4);
        assertThat(pendingGrowthDueTimes()).containsExactly(due.plus(day().multipliedBy(4)));
    }

    @Test
    void switchedOffGrowthOnlyReschedules() {
        long village = barbarians.spawnAt(freeField()).getId();
        int minesBefore = mineSum(village);
        jdbc.sql("update world_settings set barbarian_growth_enabled = false").update();
        worldService.reload();
        try {
            Instant due = clock.instant().plus(Duration.ofMinutes(1));
            scheduleGrowth(due);

            clock.advance(Duration.ofMinutes(2));
            processor.processDue(clock.instant());

            assertThat(mineSum(village)).isEqualTo(minesBefore);
            assertThat(pendingGrowthDueTimes()).containsExactly(due.plus(day()));
        } finally {
            jdbc.sql("update world_settings set barbarian_growth_enabled = true").update();
            worldService.reload();
        }
    }

    // ---- walls (M29) ----

    @Test
    void withWallsOnANewBarbarianVillageStartsWithoutAWall() {
        setWalls(true, 3, 5, false);
        try {
            for (int i = 0; i < 6; i++) {
                Village village = barbarians.spawnAt(freeField());
                assertThat(village.getBuildings().get(BuildingType.WALL)).isZero();
                assertThat(village.isWallRazed()).isFalse();
            }
        } finally {
            setWalls(false, 3, 5, false);
        }
    }

    @Test
    void withWallsOffANewBarbarianVillageKeepsTheOldRandomWall() {
        // the default: a world keeps its behaviour until the host switches walls on
        boolean seenWall = false;
        for (int i = 0; i < 25; i++) {
            int wall = barbarians.spawnAt(freeField()).getBuildings().get(BuildingType.WALL);
            assertThat(wall).isBetween(0, 2);
            seenWall |= wall > 0;
        }
        assertThat(seenWall).as("the dice give a wall now and then").isTrue();
    }

    @Test
    void wallsGrowOneLevelPerRoundFromTheStartDayOnUpToTheCap() {
        long village = barbarians.spawnAt(freeField()).getId();
        long highWall = barbarians.spawnAt(freeField()).getId();
        setWall(village, 0);
        setWall(highWall, 9); // e.g. a deleted player's village: above the cap, so left alone
        Instant opened = clock.instant();
        Instant before = initializedAtOrNull();
        setInitializedAt(opened);
        setWalls(true, 2, 3, false);
        try {
            // two rounds before the start day: nothing
            runGrowthRound(opened.plus(Duration.ofHours(30)));
            runGrowthRound(opened.plus(Duration.ofHours(47)));
            assertThat(wall(village)).isZero();
            int minesBefore = mineSum(village);

            // the round at the start day builds the first level, the next ones go up to the cap
            runGrowthRound(opened.plus(Duration.ofDays(2)));
            assertThat(wall(village)).isEqualTo(1);
            runGrowthRound(opened.plus(Duration.ofDays(3)));
            runGrowthRound(opened.plus(Duration.ofDays(4)));
            runGrowthRound(opened.plus(Duration.ofDays(5)));
            assertThat(wall(village)).isEqualTo(3);
            assertThat(wall(highWall)).isEqualTo(9);
            // the mines grew in every round after the first two (growth is on) – one each time
            assertThat(mineSum(village)).isEqualTo(minesBefore + 4);
            Village grown = villages.findById(village).orElseThrow();
            assertThat(grown.getPoints())
                    .isEqualTo(BuildRules.villagePoints(gameData, grown.getBuildings().toMap()));
        } finally {
            setWalls(false, 3, 5, false);
            setInitializedAt(before);
        }
    }

    @Test
    void wallsGrowEvenWhileMineGrowthIsSwitchedOff() {
        long village = barbarians.spawnAt(freeField()).getId();
        setWall(village, 0);
        int minesBefore = mineSum(village);
        Instant opened = clock.instant();
        Instant before = initializedAtOrNull();
        setInitializedAt(opened);
        setWalls(true, 0, 5, false);
        jdbc.sql("update world_settings set barbarian_growth_enabled = false").update();
        worldService.reload();
        try {
            runGrowthRound(opened.plusSeconds(1));

            assertThat(wall(village)).isEqualTo(1);
            assertThat(mineSum(village)).isEqualTo(minesBefore);
        } finally {
            jdbc.sql("update world_settings set barbarian_growth_enabled = true").update();
            setWalls(false, 3, 5, false);
            setInitializedAt(before);
        }
    }

    @Test
    void aRazedWallGrowsBackOnlyWithRebuild() {
        long razed = barbarians.spawnAt(freeField()).getId();
        long untouched = barbarians.spawnAt(freeField()).getId();
        setWall(razed, 1);
        setWall(untouched, 1);
        jdbc.sql("update village set wall_razed = true where id = ?").param(razed).update();
        Instant opened = clock.instant();
        Instant before = initializedAtOrNull();
        setInitializedAt(opened);
        try {
            setWalls(true, 0, 4, false);
            runGrowthRound(opened.plusSeconds(1));
            assertThat(wall(razed)).as("no rebuild").isEqualTo(1);
            assertThat(wall(untouched)).isEqualTo(2);

            setWalls(true, 0, 4, true);
            runGrowthRound(opened.plusSeconds(2));
            assertThat(wall(razed)).as("rebuild").isEqualTo(2);
            assertThat(wall(untouched)).isEqualTo(3);
        } finally {
            setWalls(false, 3, 5, false);
            setInitializedAt(before);
        }
    }

    @Test
    void noWallsAreBuiltBeforeTheWorldHasOpened() {
        long village = barbarians.spawnAt(freeField()).getId();
        setWall(village, 0);
        Instant before = initializedAtOrNull();
        setInitializedAt(null);
        setWalls(true, 0, 5, false);
        try {
            runGrowthRound(clock.instant().plusSeconds(1));

            assertThat(wall(village)).isZero();
        } finally {
            setWalls(false, 3, 5, false);
            setInitializedAt(before);
        }
    }

    @Test
    void aVillageThatTurnsBarbarianAgainIsNoLongerRazed() throws Exception {
        TestPlayer gone = TestPlayer.register(mockMvc);
        jdbc.sql("update village set wall_razed = true where id = ?")
                .param(gone.villageId)
                .update();

        transaction.executeWithoutResult(status -> barbarians.convertVillagesOf(gone.playerId));

        assertThat(villages.findById(gone.villageId).orElseThrow().isWallRazed()).isFalse();
    }

    @Test
    void growthLeavesConqueredVillagesAlone() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        int minesBefore = mineSum(player.villageId);
        scheduleGrowth(clock.instant().plus(Duration.ofMinutes(1)));

        clock.advance(Duration.ofMinutes(2));
        processor.processDue(clock.instant());

        assertThat(mineSum(player.villageId)).isEqualTo(minesBefore);
    }

    // ---- a deleted player's villages ----

    @Test
    void aDeletedPlayersVillagesBecomeBarbarianWithoutUnitsOrOrders() throws Exception {
        TestPlayer gone = TestPlayer.register(mockMvc);
        TestPlayer neighbour = TestPlayer.register(mockMvc);
        Coordinates neighbourAt =
                villages.findById(neighbour.villageId).orElseThrow().coordinates();
        endProtection(neighbour);
        jdbc.sql(
                        "update village set b_barracks = 1, b_rally_point = 1, b_farm = 10, wood ="
                                + " 5000, clay = 5000, iron = 5000 where id = ?")
                .param(gone.villageId)
                .update();
        jdbc.sql(
                        "update unit_stack set u_spearman = 100, u_axeman = 50"
                                + " where home_village_id = ? and location_village_id = ?")
                .params(gone.villageId, gone.villageId)
                .update();
        // its support in the neighbour's village, the neighbour's support in its village
        insertStack(gone.villageId, neighbour.villageId, 7);
        insertStack(neighbour.villageId, gone.villageId, 9);
        gone.enqueueOk(BuildingType.WOODCUTTER);
        gone.recruit(UnitType.SPEARMAN, 3).andExpect(status().isCreated());
        long attackId =
                number(
                                body(
                                        gone.command("ATTACK", neighbourAt, "{\"AXEMAN\":10}")
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        int neighbourMessagesBefore = sent.toPlayer(neighbour.playerId).size();

        List<Village> converted = barbarians.convertVillagesOf(gone.playerId);

        assertThat(converted).extracting(Village::getId).containsExactly(gone.villageId);
        Village village = villages.findById(gone.villageId).orElseThrow();
        assertThat(village.getPlayer()).isNull();
        assertThat(village.getName()).isNull();
        assertThat(stacks.findHomeStack(gone.villageId))
                .hasValueSatisfying(stack -> assertThat(stack.getUnits().isEmpty()).isTrue());
        assertThat(
                        stacks.findByHomeVillageIdAndLocationVillageId(
                                gone.villageId, neighbour.villageId))
                .isEmpty();
        assertThat(
                        stacks.findByHomeVillageIdAndLocationVillageId(
                                neighbour.villageId, gone.villageId))
                .hasValueSatisfying(
                        stack -> assertThat(stack.getUnits().get(UnitType.SPEARMAN)).isEqualTo(9));
        assertThat(count("select count(*) from movement where home_village_id = ?", gone.villageId))
                .isZero();
        assertThat(count("select count(*) from build_order where village_id = ?", gone.villageId))
                .isZero();
        assertThat(count("select count(*) from recruit_order where village_id = ?", gone.villageId))
                .isZero();
        assertThat(count("select count(*) from game_event where ref_id = ?", attackId)).isZero();
        assertThat(count("select count(*) from game_event")).isZero();

        Player player = players.findById(gone.playerId).orElseThrow();
        assertThat(player.getVillageCount()).isZero();
        assertThat(player.getPoints()).isZero();

        // the neighbour's attack counter and troops change: told twice (support gone, attack gone)
        assertThat(
                        sent.toPlayer(neighbour.playerId)
                                .subList(
                                        neighbourMessagesBefore,
                                        sent.toPlayer(neighbour.playerId).size()))
                .hasSize(2)
                .allSatisfy(json -> assertThat(json).contains("VILLAGE_UPDATED"));
        neighbour.troops().andExpect(jsonPath("$.incoming").isEmpty());

        // from now on it is a barbarian village like any other
        assertThat(villages.findBarbarianIds()).contains(gone.villageId);
    }

    @Test
    void aPlayerWithoutVillagesConvertsNothing() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        barbarians.convertVillagesOf(player.playerId);

        assertThat(barbarians.convertVillagesOf(player.playerId)).isEmpty();
    }

    // ---- helpers ----

    private void assertFreshBarbarian(Village village) {
        assertThat(village.getPlayer()).isNull();
        assertThat(village.getName()).isNull();
        assertThat(village.getLoyalty()).isEqualTo(100);
        var levels = village.getBuildings();
        for (BuildingType mine : Barbarians.MINES) {
            assertThat(levels.get(mine)).isBetween(1, 5);
        }
        assertThat(levels.get(BuildingType.WALL)).isBetween(0, 2);
        assertThat(levels.get(BuildingType.WAREHOUSE)).isEqualTo(5);
        assertThat(levels.get(BuildingType.FARM)).isEqualTo(1);
        assertThat(levels.get(BuildingType.HEADQUARTERS)).isEqualTo(1);
        assertThat(levels.get(BuildingType.BARRACKS)).isZero();
        assertThat(village.getPoints())
                .isEqualTo(BuildRules.villagePoints(gameData, levels.toMap()));
        double half = Storage.warehouseCapacity(5) / 2.0;
        assertThat(village.getWood()).isEqualTo(half);
        assertThat(village.getClay()).isEqualTo(half);
        assertThat(village.getIron()).isEqualTo(half);
        assertThat(stacks.findHomeStack(village.getId()))
                .hasValueSatisfying(stack -> assertThat(stack.getUnits().isEmpty()).isTrue());
    }

    private void assertSpacingOfAllVillages() {
        List<Coordinates> all = villages.findAllCoordinates();
        int mapSize = worldService.settings().mapSize();
        for (Coordinates spot : all) {
            assertThat(spot.x()).isBetween(0, mapSize - 1);
            assertThat(spot.y()).isBetween(0, mapSize - 1);
        }
        // only the barbarians are new here; the rest was checked when they were placed
        Set<Long> barbarianIds = new HashSet<>(villages.findBarbarianIds());
        for (Village barbarian : villages.findAllById(barbarianIds)) {
            if (barbarian.getCreatedAt().equals(clock.instant())) {
                for (Coordinates other : all) {
                    if (!other.equals(barbarian.coordinates())) {
                        assertThat(barbarian.coordinates().distanceTo(other))
                                .as("%s to %s", barbarian.coordinates(), other)
                                .isGreaterThanOrEqualTo(2);
                    }
                }
            }
        }
    }

    private List<Long> newBarbarians(Set<Long> before) {
        return villages.findBarbarianIds().stream().filter(id -> !before.contains(id)).toList();
    }

    /** A field with free neighbours, scanning from the top left corner (away from the players). */
    private Coordinates freeField() {
        Set<Coordinates> occupied = new HashSet<>(villages.findAllCoordinates());
        int mapSize = worldService.settings().mapSize();
        for (int y = 1; y < mapSize - 1; y += 2) {
            for (int x = 1; x < mapSize - 1; x += 2) {
                Coordinates spot = new Coordinates(x, y);
                if (MapPlacement.spacingOk(spot, occupied::contains)) {
                    return spot;
                }
            }
        }
        throw new IllegalStateException("map full");
    }

    private void setWalls(boolean enabled, int startDays, int maxLevel, boolean rebuild) {
        jdbc.sql(
                        "update world_settings set barbarian_walls_enabled = ?,"
                                + " barbarian_wall_start_days = ?, barbarian_wall_max_level = ?,"
                                + " barbarian_walls_rebuild = ?")
                .params(enabled, startDays, maxLevel, rebuild)
                .update();
        worldService.reload();
    }

    private void setWall(long villageId, int level) {
        jdbc.sql("update village set b_wall = ? where id = ?").params(level, villageId).update();
    }

    private int wall(long villageId) {
        return villages.findById(villageId).orElseThrow().getBuildings().get(BuildingType.WALL);
    }

    /** The world "opened" at this time ({@code initialized_at}); null puts it back to nothing. */
    private void setInitializedAt(Instant at) {
        jdbc.sql("update world_settings set initialized_at = ?")
                .param(at == null ? null : Timestamp.from(at))
                .update();
        worldService.reload();
    }

    private Instant initializedAtOrNull() {
        Timestamp stored =
                jdbc.sql("select initialized_at from world_settings")
                        .query(Timestamp.class)
                        .optional()
                        .orElse(null);
        return stored == null ? null : stored.toInstant();
    }

    /** One growth round due at {@code due}, as the event worker runs it. */
    private void runGrowthRound(Instant due) {
        jdbc.sql("delete from game_event").update();
        scheduleGrowth(due);
        clock.set(due);
        processor.processDue(due);
    }

    private void setMines(long villageId, int wood, int clay, int iron) {
        jdbc.sql(
                        "update village set b_woodcutter = ?, b_clay_pit = ?, b_iron_mine = ? where"
                                + " id = ?")
                .params(wood, clay, iron, villageId)
                .update();
    }

    private int mineSum(long villageId) {
        Village village = villages.findById(villageId).orElseThrow();
        return Barbarians.MINES.stream().mapToInt(village.getBuildings()::get).sum();
    }

    private void scheduleGrowth(Instant due) {
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.BARBARIAN_GROWTH, null, due));
    }

    private List<Instant> pendingGrowthDueTimes() {
        return jdbc
                .sql(
                        "select due_at from game_event where kind = 'BARBARIAN_GROWTH'"
                                + " and status = 'PENDING' order by due_at")
                .query(Timestamp.class)
                .list()
                .stream()
                .map(Timestamp::toInstant)
                .toList();
    }

    private Instant initializedAt() {
        return jdbc.sql("select initialized_at from world_settings")
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private void setBarbariansPerNewPlayer(int count) {
        jdbc.sql("update world_settings set barbarians_per_new_player = ?").param(count).update();
        worldService.reload();
    }

    private void endProtection(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    private void insertStack(long home, long location, int spearmen) {
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman)"
                                + " values (?, ?, ?)")
                .params(home, location, spearmen)
                .update();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private static Duration day() {
        return Duration.ofHours(24);
    }
}
