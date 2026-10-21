package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Storage;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Learning lords (1.6, 09-npc-lords §15) end to end: real human attacks on a lord become its
 * lessons, which change its next recruit and build orders; the human wins of the world; the switch.
 * Like {@code NpcBannersFlowTest}, every scene lies far off the map (x ≥ 2000) and is removed
 * after.
 */
@IntegrationTest
class NpcLearningFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AtomicInteger AREA = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired NpcBrainRepository brains;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NpcWorld npcWorld;
    @Autowired NpcAdminService adminService;
    @Autowired UnitStackRepository stacks;
    @Autowired GameData gameData;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2600;
    private final List<long[]> movedHumans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set barbarians_per_new_player = 0,"
                                + " npc_difficulty = 'NORMAL', speed = 1.0,"
                                + " npc_learning_enabled = true")
                .update();
        worldService.reload();
        clock.set(nextBerlin(LocalTime.NOON));
        baseX = 2000 + 200 * AREA.getAndIncrement();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        String scene = "select id from village where x >= 2000";
        jdbc.sql(
                        ("delete from movement where home_village_id in (%1$s)"
                                        + " or source_village_id in (%1$s)"
                                        + " or target_village_id in (%1$s)")
                                .formatted(scene))
                .update();
        jdbc.sql(
                        ("delete from unit_stack where location_village_id in (%1$s)"
                                        + " and home_village_id <> location_village_id")
                                .formatted(scene))
                .update();
        for (long[] moved : movedHumans) {
            jdbc.sql("update village set x = ?, y = ? where id = ?")
                    .param(moved[1])
                    .param(moved[2])
                    .param(moved[0])
                    .update();
            jdbc.sql(
                            "delete from report where player_id = (select player_id from village"
                                    + " where id = ?)")
                    .param(moved[0])
                    .update();
        }
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from movement where home_village_id in (%1$s) or target_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql(
                        "delete from unit_stack where home_village_id in (%1$s) or location_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from movement where owner_player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from report where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from player where npc").update();
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        "update world_settings set npc_lord_count = 0, npc_difficulty = 'NORMAL',"
                                + " npc_custom = null, speed = 1.0, barbarian_initial_count = 30,"
                                + " barbarians_per_new_player = 0, npc_learning_enabled = false")
                .update();
        worldService.reload();
    }

    @Test
    void cavalryAttacksOnALordMakeItRecruitSpearmenFirstAndTheAdminSeesWhy() throws Exception {
        long lord = lord();
        long village = villageOf(lord);
        recruitingVillage(village);
        TestPlayer human = human(4, 0);
        units(human.villageId, Map.of(UnitType.LIGHT_CAVALRY, 50));
        Instant arrival = null;
        for (int i = 0; i < 5; i++) {
            arrival = humanAttack(human, village, "{\"LIGHT_CAVALRY\":5}");
        }
        arriveAt(arrival);

        round(lord);

        NpcMemory.Tally seen = brains.findById(lord).orElseThrow().getMemory().lessons().seen();
        assertThat(seen.attacks()).isCloseTo(5, within(0.01));
        assertThat(seen.cavalry()).isPositive();
        assertThat(seen.infantry()).isZero();
        // with 150 spearmen and 250 axemen the template wants axemen; the lesson wants spearmen
        assertThat(firstRecruitOrder(village)).isEqualTo("SPEARMAN");

        NpcLordDto.Learning learning =
                adminService.lords().stream()
                        .filter(l -> l.playerId() == lord)
                        .findFirst()
                        .orElseThrow()
                        .learning();
        assertThat(learning).isNotNull();
        assertThat(learning.defenceWeight()).isCloseTo(0.35, within(0.01));
        assertThat(learning.changes().getFirst().unit()).isEqualTo(UnitType.SPEARMAN);
        assertThat(learning.changes().getFirst().points()).isPositive();
    }

    @Test
    void withLearningOffTheSameAttacksChangeNothing() throws Exception {
        jdbc.sql("update world_settings set npc_learning_enabled = false").update();
        worldService.reload();
        long lord = lord();
        long village = villageOf(lord);
        recruitingVillage(village);
        TestPlayer human = human(4, 0);
        units(human.villageId, Map.of(UnitType.LIGHT_CAVALRY, 50, UnitType.RAM, 10));
        Instant arrival = null;
        for (int i = 0; i < 5; i++) {
            arrival = humanAttack(human, village, "{\"LIGHT_CAVALRY\":5,\"RAM\":2}");
        }
        arriveAt(arrival);

        round(lord);

        // the memory keeps the tally, but the lord plays its template
        assertThat(brains.findById(lord).orElseThrow().getMemory().lessons().seen().attacks())
                .isPositive();
        assertThat(firstRecruitOrder(village)).isEqualTo("AXEMAN");
        assertThat(buildOrders(village)).doesNotContain("WALL");
        assertThat(
                        adminService.lords().stream()
                                .filter(l -> l.playerId() == lord)
                                .findFirst()
                                .orElseThrow()
                                .learning())
                .isNull();
    }

    @Test
    void aRamAttackMakesTheLordQueueItsWallFirst() throws Exception {
        long lord = lord();
        long village = villageOf(lord);
        recruitingVillage(village);
        TestPlayer human = human(4, 0);
        units(human.villageId, Map.of(UnitType.AXEMAN, 20, UnitType.RAM, 4));
        arriveAt(humanAttack(human, village, "{\"AXEMAN\":20,\"RAM\":4}"));

        round(lord);

        NpcMemory.Lessons lessons = brains.findById(lord).orElseThrow().getMemory().lessons();
        assertThat(NpcLearning.wallGoal(lessons, village, clock.instant())).isEqualTo(3);
        assertThat(buildOrders(village).getFirst()).isEqualTo("WALL");
    }

    @Test
    void theWinsOfTheWorldAreTheHumansWonAttacksOnOwnedVillagesOnly() throws Exception {
        long lord = lord();
        long village = villageOf(lord);
        TestPlayer human = human(4, 0);
        units(
                human.villageId,
                Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 20, UnitType.LIGHT_CAVALRY, 20));
        long barbarian = barbarian(8, 0);
        Instant since = clock.instant();
        // a won attack on the lord's empty village counts; scouts only and a barbarian farm don't
        humanAttack(human, village, "{\"AXEMAN\":30,\"LIGHT_CAVALRY\":10}");
        humanAttack(human, village, "{\"SCOUT\":5}");
        arriveAt(humanAttack(human, barbarian, "{\"AXEMAN\":30}"));
        // the same won battle as a lord's own ATTACK report, and as a copy the human shared with
        // the lord: neither is a human win
        String wonOnOwned =
                " from report where player_id = ? and kind = 'ATTACK'"
                        + " and payload ->> 'outcome' = 'ATTACKER_WON'"
                        + " and payload -> 'defender' ->> 'playerId' is not null";
        jdbc.sql(
                        "insert into report (player_id, kind, happened_at, read, payload,"
                                + " created_at) select ?, kind, happened_at, true, payload,"
                                + " created_at"
                                + wonOnOwned)
                .param(lord)
                .param(human.playerId)
                .update();
        jdbc.sql(
                        "insert into report (player_id, kind, happened_at, read, payload,"
                                + " created_at, shared_by_id, shared_by_name, view_of_id,"
                                + " view_of_name) select ?, kind, happened_at, true, payload,"
                                + " created_at, player_id, 'human', player_id, 'human'"
                                + wonOnOwned)
                .param(lord)
                .param(human.playerId)
                .update();

        List<NpcLearning.Win> wins = npcWorld.humanWins(since);
        // the human's own copy of the won attack only (scouts only drops out in winsOf below)
        assertThat(wins)
                .filteredOn(w -> w.units().containsKey(UnitType.AXEMAN))
                .singleElement()
                .satisfies(
                        w ->
                                assertThat(w.units())
                                        .containsOnly(
                                                Map.entry(UnitType.AXEMAN, 30),
                                                Map.entry(UnitType.LIGHT_CAVALRY, 10)));
        NpcLearning.Wins faded = NpcLearning.winsOf(wins, clock.instant(), 24, gameData);
        assertThat(faded.count()).isCloseTo(1, within(0.01));
        assertThat(faded.population()).containsOnlyKeys(UnitType.AXEMAN, UnitType.LIGHT_CAVALRY);
        assertThat(npcWorld.humanWins(clock.instant())).isEmpty();
    }

    // ---- helpers ----

    /**
     * A village that recruits this round: barracks and smithy for spear- and axemen, room in the
     * farm, a full warehouse, and an army of 150 spearmen and 250 axemen (37.5 % / 62.5 %, so the
     * template's mix asks for axemen next).
     */
    private void recruitingVillage(long village) {
        buildings(
                village,
                Map.of(
                        BuildingType.BARRACKS, 5,
                        BuildingType.SMITHY, 5,
                        BuildingType.FARM, 20,
                        BuildingType.WAREHOUSE, 15));
        int capacity = Storage.warehouseCapacity(15);
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .param(capacity)
                .param(capacity)
                .param(capacity)
                .param(Timestamp.from(clock.instant()))
                .param(village)
                .update();
        units(village, Map.of(UnitType.SPEARMAN, 150, UnitType.AXEMAN, 250));
    }

    private long lord() {
        jdbc.sql(
                        "update world_settings set initialized_at = null, barbarian_initial_count ="
                                + " 0, npc_lord_count = 1")
                .update();
        worldService.reload();
        initializer.initialize();
        long id =
                players.findAll().stream()
                        .filter(Player::isNpc)
                        .map(Player::getId)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
        jdbc.sql("update npc_brain set personality = ?, template = ? where player_id = ?")
                .param(NpcPersonality.OFFENSIVE.name())
                .param(NpcPersonality.OFFENSIVE.name())
                .param(id)
                .update();
        place(villageOf(id), 0, 0);
        jdbc.sql("delete from game_event").update();
        return id;
    }

    private long villageOf(long playerId) {
        return villages.findIdsByPlayerId(playerId).getFirst();
    }

    private void place(long villageId, int dx, int dy) {
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .param(baseX + dx)
                .param(baseY + dy)
                .param(villageId)
                .update();
    }

    /** A registered human, protection over, with his village moved into the scene. */
    private TestPlayer human(int dx, int dy) throws Exception {
        TestPlayer human = TestPlayer.register(mockMvc);
        Village village = villages.findById(human.villageId).orElseThrow();
        movedHumans.add(new long[] {human.villageId, village.getX(), village.getY()});
        place(human.villageId, dx, dy);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .param(Timestamp.from(clock.instant().minusSeconds(3600)))
                .param(human.playerId)
                .update();
        return human;
    }

    private long barbarian(int dx, int dy) {
        return transaction.execute(
                status -> {
                    BuildingLevels levels = new BuildingLevels();
                    for (BuildingType type : BuildingType.values()) {
                        levels.set(type, gameData.building(type).startLevel());
                    }
                    levels.set(BuildingType.WAREHOUSE, 5);
                    int points = BuildRules.villagePoints(gameData, levels.toMap());
                    Village village =
                            villages.save(
                                    new Village(
                                            null,
                                            null,
                                            new Coordinates(baseX + dx, baseY + dy),
                                            1000,
                                            levels,
                                            points,
                                            clock.instant()));
                    stacks.save(UnitStack.homeOf(village));
                    return village.getId();
                });
    }

    /** Sets the village's own units at home (all other types 0). */
    private void units(long villageId, Map<UnitType, Integer> units) {
        StringBuilder sql = new StringBuilder("update unit_stack set ");
        for (UnitType type : UnitType.values()) {
            sql.append("u_")
                    .append(type.name().toLowerCase(Locale.ROOT))
                    .append(" = ")
                    .append(units.getOrDefault(type, 0))
                    .append(", ");
        }
        sql.append("version = version + 1 where home_village_id = ? and location_village_id = ?");
        jdbc.sql(sql.toString()).param(villageId).param(villageId).update();
    }

    private void buildings(long villageId, Map<BuildingType, Integer> levels) {
        levels.forEach(
                (type, level) ->
                        jdbc.sql(
                                        "update village set b_%s = ? where id = ?"
                                                .formatted(type.name().toLowerCase(Locale.ROOT)))
                                .param(level)
                                .param(villageId)
                                .update());
    }

    private String firstRecruitOrder(long villageId) {
        return jdbc.sql("select unit from recruit_order where village_id = ? order by id limit 1")
                .param(villageId)
                .query(String.class)
                .optional()
                .orElse("none");
    }

    private List<String> buildOrders(long villageId) {
        return jdbc.sql("select building from build_order where village_id = ? order by id")
                .param(villageId)
                .query(String.class)
                .list();
    }

    /** One round of the lord now, through the event worker. */
    private void round(long lord) {
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lord, clock.instant()));
        processor.processDue(clock.instant());
    }

    private void arriveAt(Instant at) {
        clock.set(at.plus(Duration.ofSeconds(1)));
        processor.processDue(clock.instant());
    }

    private Instant humanAttack(TestPlayer human, long targetVillageId, String unitsJson)
            throws Exception {
        Village target = villages.findById(targetVillageId).orElseThrow();
        String sent =
                human.command("ATTACK", target.coordinates(), unitsJson)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return Instant.parse(JsonPath.read(sent, "$.arriveAt"));
    }

    /** The next moment (not before now) at which the clock in Berlin shows {@code time}. */
    private Instant nextBerlin(LocalTime time) {
        LocalDateTime candidate = clock.instant().atZone(BERLIN).toLocalDate().atTime(time);
        Instant at = candidate.atZone(BERLIN).toInstant();
        return at.isBefore(clock.instant()) ? candidate.plusDays(1).atZone(BERLIN).toInstant() : at;
    }
}
