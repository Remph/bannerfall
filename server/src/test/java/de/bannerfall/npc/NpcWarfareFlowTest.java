package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

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
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.rules.BuildRules;
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
 * NPC lords at war (M12c, 09-npc-lords §4, §6, §7) end to end: real rounds through the event
 * worker, real commands. Every test builds its scene far off the map (x ≥ 2000, where no other test
 * puts a village and the lords see nothing else in their radius) and removes it afterwards.
 */
@IntegrationTest
class NpcWarfareFlowTest {

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
    @Autowired UnitStackRepository stacks;
    @Autowired GameData gameData;
    @Autowired TestRandomSource random;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2000;

    /** Human villages moved into the scene, with their old fields: put back afterwards. */
    private final List<long[]> movedHumans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set barbarians_per_new_player = 0,"
                                + " npc_difficulty = 'NORMAL', speed = 1.0")
                .update();
        worldService.reload();
        clock.set(nextBerlin(LocalTime.NOON));
        baseX = 2000 + 200 * AREA.getAndIncrement();
    }

    @AfterEach
    void cleanUp() {
        random.reset();
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
        jdbc.sql("delete from player where npc").update();
        // barbarian villages of the scene (and villages conquered by a lord, now barbarian-less)
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        "update world_settings set npc_lord_count = 0, npc_difficulty = 'NORMAL',"
                                + " npc_custom = null, speed = 1.0, barbarian_initial_count = 30,"
                                + " barbarians_per_new_player = 0")
                .update();
        worldService.reload();
    }

    // ---- who is attacked (§6.4, §7) ----

    @Test
    void anOffensiveLordRaidsAClearlyWeakerHumanWithFreshIntel() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 50));
        TestPlayer human = human(3, 0, true);
        knows(lord, human.villageId, emptyIntel(clock.instant(), human.playerId));

        round(lord);

        assertThat(attacksTo(lord, human.villageId)).hasSize(1);
        assertThat(memory(lord).attacksSent())
                .containsExactly(new NpcMemory.SentAttack(human.playerId, clock.instant()));
    }

    @Test
    void aDefensiveLordNeverAttacksVillagesAboveHalfOfItsOwn() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 200, UnitType.LIGHT_CAVALRY, 20));
        points(village, 1000);
        TestPlayer weak = human(3, 0, true);
        TestPlayer strong = human(0, 4, true);
        points(weak.villageId, 499);
        points(strong.villageId, 500);
        knows(lord, weak.villageId, emptyIntel(clock.instant(), weak.playerId));
        knows(lord, strong.villageId, emptyIntel(clock.instant(), strong.playerId));

        round(lord);

        assertThat(attacksTo(lord, weak.villageId)).hasSize(1);
        assertThat(attacksTo(lord, strong.villageId)).isEmpty();
    }

    @Test
    void noAttackOnAPlayerUnderBeginnerProtection() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 50, UnitType.SCOUT, 10));
        TestPlayer human = human(3, 0, false);
        knows(lord, human.villageId, emptyIntel(clock.instant(), human.playerId));

        round(lord);

        assertThat(attacksTo(lord, human.villageId)).isEmpty();
    }

    @Test
    void theAttackCapsCountTheLast24HoursForOneLordAndForAllLords() {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        long lord = lords.get(0);
        long other = lords.get(1);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 50));
        TestPlayer human = human(3, 0, true);
        Instant now = clock.instant();

        // NORMAL: 4 commands to one player a day; four within the window: none more
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, emptyIntel(now, human.playerId));
                    for (int i = 0; i < 4; i++) {
                        m.attacksSent.add(
                                new NpcMemory.SentAttack(human.playerId, now.minusSeconds(3600)));
                    }
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();

        // four exactly 24 hours old no longer count
        clock.advance(Duration.ofMinutes(1));
        Instant later = clock.instant();
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    for (int i = 0; i < 4; i++) {
                        m.attacksSent.add(
                                new NpcMemory.SentAttack(
                                        human.playerId, later.minus(Duration.ofHours(24))));
                    }
                    m.intel.put(human.villageId, emptyIntel(later, human.playerId));
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).hasSize(1);

        // all lords together: 8 a day per human on NORMAL, the other lord sent them
        deleteMovementsOf(lord);
        clock.advance(Duration.ofHours(2)); // past the farm cooldown
        Instant third = clock.instant();
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(third, human.playerId));
                });
        remember(
                other,
                m -> {
                    for (int i = 0; i < 8; i++) {
                        m.attacksSent.add(
                                new NpcMemory.SentAttack(human.playerId, third.minusSeconds(60)));
                    }
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
    }

    @Test
    void noCommandArrivesAtAHumanVillageInTheQuietHours() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        TestPlayer human = human(3, 0, true);
        // 3 fields with axemen: 54 minutes

        // arriving 22:59: sent
        clock.set(nextBerlin(LocalTime.of(22, 5)));
        freshRaidSetup(lord, village, human);
        round(lord);
        assertThat(attacksTo(lord, human.villageId))
                .singleElement()
                .satisfies(at -> assertThat(berlinTime(at)).isEqualTo(LocalTime.of(22, 59)));

        // arriving 23:00: not sent
        clock.set(nextBerlin(LocalTime.of(22, 6)));
        freshRaidSetup(lord, village, human);
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();

        // arriving 07:00: sent
        clock.set(nextBerlin(LocalTime.of(6, 6)));
        freshRaidSetup(lord, village, human);
        round(lord);
        assertThat(attacksTo(lord, human.villageId))
                .singleElement()
                .satisfies(at -> assertThat(berlinTime(at)).isEqualTo(LocalTime.of(7, 0)));
    }

    @Test
    void scoutsBeforeAnAttackWhenTheIntelIsTooOld() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 50, UnitType.SCOUT, 10));
        TestPlayer human = human(3, 0, true);
        // 7 hours old at speed 1: older than the 6 hours fresh intel may be
        knows(
                lord,
                human.villageId,
                emptyIntel(clock.instant().minus(Duration.ofHours(7)), human.playerId));

        round(lord);

        List<Map<String, Object>> sent = movementsTo(lord, human.villageId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst()).containsEntry("u_scout", 3).containsEntry("u_axeman", 0);

        // with fresh intel it raids without scouting
        deleteMovementsOf(lord);
        clock.advance(Duration.ofMinutes(1));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                });
        round(lord);
        sent = movementsTo(lord, human.villageId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst()).containsEntry("u_scout", 0);
        assertThat((Integer) sent.getFirst().get("u_axeman")).isPositive();
    }

    @Test
    void anOffensiveLordRetaliatesAgainstTheVillageThatAttackedIt() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 100));
        // 30 fields: beyond the farm radius, only a retaliation goes there
        TestPlayer human = human(30, 0, true);
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, emptyIntel(now, human.playerId));
                    m.grudges.put(
                            human.playerId,
                            new NpcMemory.Grudge(
                                    now.plus(Duration.ofHours(10)), human.villageId, false, 0));
                });

        round(lord);

        assertThat(attacksTo(lord, human.villageId)).hasSize(1);
        assertThat(memory(lord).grudges().get(human.playerId).retaliated()).isTrue();
    }

    @Test
    void noScoutingAgainstAVillageWhoseScoutsTheLordKnowsItCannotBeat() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 10));
        TestPlayer human = human(30, 0, true);
        Instant until = clock.instant().plus(Duration.ofHours(10));
        // 46 scouts came home from scouting the lord: its 10 can't beat them
        remember(
                lord,
                m ->
                        m.grudges.put(
                                human.playerId,
                                new NpcMemory.Grudge(until, human.villageId, false, 46)));

        round(lord);

        assertThat(movementsTo(lord, human.villageId)).isEmpty();

        // 4 came home: 2 × 4 + 1 of its 10 are enough
        remember(
                lord,
                m ->
                        m.grudges.put(
                                human.playerId,
                                new NpcMemory.Grudge(until, human.villageId, false, 4)));
        clock.advance(Duration.ofMinutes(1));
        round(lord);

        List<Map<String, Object>> sent = movementsTo(lord, human.villageId);
        assertThat(sent).singleElement().satisfies(m -> assertThat(m).containsEntry("u_scout", 9));
    }

    // ---- defence (§6.5) ----

    @Test
    void aDefensiveLordSupportsAVillageUnderAttackIgnoresScoutsAndWithdrawsAfterwards() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long helper = villageOf(lord);
        long attacked = addVillage(lord, 3, 0);
        units(helper, Map.of(UnitType.SPEARMAN, 100));
        TestPlayer human = human(3, 10, false); // 10 fields from the attacked village
        Instant now = clock.instant();

        // only scouts: 10 fields in 76 minutes (7.6 min/field, scouts under a Haste banner; with
        // banners on, anything slower than 8.075 could be hasted cavalry, D5)
        long scouts =
                incomingAttack(
                        human, attacked, now.minusSeconds(16 * 60), now.plusSeconds(60 * 60));
        round(lord);
        assertThat(supportsFrom(helper)).isEmpty();
        jdbc.sql("delete from movement where id = ?").param(scouts).update();

        // an army at 18 min/field arriving in an hour: half of the spearmen go (54 min away)
        clock.advance(Duration.ofMinutes(1));
        now = clock.instant();
        long attack =
                incomingAttack(
                        human, attacked, now.minusSeconds(120 * 60), now.plusSeconds(60 * 60));
        round(lord);
        assertThat(supportsFrom(helper))
                .singleElement()
                .satisfies(
                        s ->
                                assertThat(s)
                                        .containsEntry("u_spearman", 50)
                                        .containsEntry("target_village_id", attacked));
        // once per attack and village
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(supportsFrom(helper)).hasSize(1);

        // the attack is over and the support stands there: it goes home
        jdbc.sql("delete from movement where id = ?").param(attack).update();
        deleteMovementsOf(lord);
        transaction.executeWithoutResult(
                status -> {
                    UnitStack stack =
                            new UnitStack(
                                    villages.findById(helper).orElseThrow(),
                                    villages.findById(attacked).orElseThrow());
                    stack.getUnits().set(UnitType.SPEARMAN, 50);
                    stacks.save(stack);
                });
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(helper, attacked)).isEmpty();
        assertThat(
                        jdbc.sql(
                                        "select count(*) from movement where kind = 'RETURN' and"
                                            + " source_village_id = ? and target_village_id = ?")
                                .param(attacked)
                                .param(helper)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void aStringOfFakeAttacksCannotDrainAHelpersArmyBeyondItsShare() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long helper = villageOf(lord);
        long attacked = addVillage(lord, 3, 0);
        units(helper, Map.of(UnitType.SPEARMAN, 100));
        TestPlayer human = human(3, 10, false);
        Instant now = clock.instant();

        // the first attack gets the share (half), as always
        incomingAttack(human, attacked, now.minusSeconds(120 * 60), now.plusSeconds(60 * 60));
        round(lord);
        assertThat(supportsFrom(helper))
                .singleElement()
                .satisfies(s -> assertThat(s).containsEntry("u_spearman", 50));

        // two more fakes at the same village, one after the other: nothing more leaves
        for (int i = 0; i < 2; i++) {
            clock.advance(Duration.ofMinutes(1));
            Instant at = clock.instant();
            incomingAttack(human, attacked, at.minusSeconds(120 * 60), at.plusSeconds(60 * 60));
            round(lord);
        }
        assertThat(supportsFrom(helper)).hasSize(1);
        assertThat(
                        jdbc.sql(
                                        "select u_spearman from unit_stack where home_village_id ="
                                                + " ? and location_village_id = ?")
                                .param(helper)
                                .param(helper)
                                .query(Integer.class)
                                .single())
                .isEqualTo(50);
    }

    @Test
    void theSupportCountsTheUnitsRecruitedSinceTheVillageWasLastCaughtUp() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long helper = villageOf(lord);
        long attacked = addVillage(lord, 3, 0);
        units(helper, Map.of(UnitType.SPEARMAN, 60));
        Instant now = clock.instant();
        // 40 spearmen finished an hour ago, not yet in the home stack (nobody looked since)
        jdbc.sql(
                        """
                        insert into recruit_order (village_id, building, unit, count,
                            completed_count, seconds_per_unit, start_at, end_at, unit_cost_wood,
                            unit_cost_clay, unit_cost_iron, unit_pop, created_at)
                        values (?, 'BARRACKS', 'SPEARMAN', 40, 0, 60, ?, ?, 0, 0, 0, 1, ?)
                        """)
                .params(
                        helper,
                        Timestamp.from(now.minusSeconds(7200)),
                        Timestamp.from(now.minusSeconds(3600)),
                        Timestamp.from(now.minusSeconds(7200)))
                .update();
        TestPlayer human = human(3, 10, false);
        incomingAttack(human, attacked, now.minusSeconds(120 * 60), now.plusSeconds(60 * 60));

        round(lord);

        // half of all 100 spearmen, not half of the 60 counted before the catch-up
        assertThat(supportsFrom(helper))
                .singleElement()
                .satisfies(s -> assertThat(s).containsEntry("u_spearman", 50));
    }

    // ---- conquest (§6.6, §7) ----

    @Test
    void aLordConquersABarbarianVillageWithNoblesAfterScoutingIt() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1, BuildingType.STABLE, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 10, UnitType.NOBLE, 4));
        long barbarian = barbarian(3, 0, 0);
        // the highest loyalty hit (35 per noble): four nobles take the village in one train
        random.use(FixedRandomSource.constant(0.99));

        round(lord);
        // first the scouts
        assertThat(movementsTo(lord, barbarian))
                .singleElement()
                .satisfies(m -> assertThat(m).containsEntry("u_scout", 5));

        for (int minute = 0; minute < 8 * 60 && ownerOf(barbarian) == null; minute++) {
            clock.advance(Duration.ofMinutes(1));
            processor.processDue(clock.instant());
        }

        assertThat(ownerOf(barbarian)).isEqualTo(lord);
        assertThat(
                        jdbc.sql(
                                        "select count(*) from report where player_id = ? and kind ="
                                                + " 'CONQUEST'")
                                .param(lord)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void anAcademyGoesForATargetItCanBeatInsteadOfWaitingForTheBestScoredOne() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4));
        // the best score by far, but 1,000 spearmen stand there
        TestPlayer human = human(3, 0, true);
        addVillage(human.playerId, 60, 60); // not his last village
        points(human.villageId, 5000);
        knows(
                lord,
                human.villageId,
                new Intel(
                        clock.instant(),
                        human.playerId,
                        Map.of(UnitType.SPEARMAN, 1000),
                        0,
                        null,
                        null,
                        null,
                        false,
                        BattleOutcome.DEFENDER_WON,
                        0,
                        null,
                        null));
        long barbarian = barbarian(0, 4, 0);
        remember(
                lord,
                m ->
                        m.intel.put(
                                barbarian,
                                new Intel(
                                        clock.instant(),
                                        null,
                                        Map.of(),
                                        0,
                                        null,
                                        null,
                                        null,
                                        false,
                                        BattleOutcome.ATTACKER_WON,
                                        0,
                                        null,
                                        null)));

        round(lord);

        assertThat(memory(lord).conquest().get(village).villageId()).isEqualTo(barbarian);
        assertThat(movementsTo(lord, barbarian))
                .anySatisfy(m -> assertThat(m).containsEntry("u_noble", 4));
        assertThat(movementsTo(lord, human.villageId)).isEmpty();
    }

    @Test
    void aHumansLastVillageGetsNoNoblesOnNormalButOnHard() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4));
        TestPlayer human = human(3, 0, true);
        knows(lord, human.villageId, emptyIntel(clock.instant(), human.playerId));

        round(lord);
        assertThat(movementsTo(lord, human.villageId))
                .allSatisfy(m -> assertThat(m).containsEntry("u_noble", 0));

        deleteMovementsOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4));
        setDifficulty("HARD");
        clock.advance(Duration.ofMinutes(1));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                });
        round(lord);
        assertThat(movementsTo(lord, human.villageId))
                .anySatisfy(m -> assertThat(m).containsEntry("u_noble", 4));
    }

    @Test
    void aBrakedLordSendsNoTrain() {
        setDifficulty("HARD");
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4));
        TestPlayer human = human(3, 0, true);
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                    m.stance = new NpcMemory.Stance(false, true);
                });

        round(lord);

        assertThat(movementsTo(lord, human.villageId))
                .allSatisfy(m -> assertThat(m).containsEntry("u_noble", 0));
        assertThat(memory(lord).conquest()).isEmpty();
    }

    @Test
    void aTrainIsSentWholeOrNotAtAllAndCountsOnceAgainstTheCaps() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        TestPlayer human = human(3, 0, true);
        addVillage(human.playerId, 60, 60); // not his last village
        var defended =
                new Intel(
                        clock.instant(),
                        human.playerId,
                        Map.of(UnitType.SPEARMAN, 10),
                        1,
                        null,
                        null,
                        null,
                        false,
                        BattleOutcome.DEFENDER_WON,
                        0,
                        null,
                        null);

        // 22:00 Berlin: the clearing attack would arrive 22:54, the nobles 23:45 – quiet hours
        clock.set(nextBerlin(LocalTime.of(22, 0)));
        units(village, Map.of(UnitType.AXEMAN, 200, UnitType.NOBLE, 4));
        knows(lord, human.villageId, withAt(defended, clock.instant()));
        round(lord);
        assertThat(movementsTo(lord, human.villageId)).isEmpty();

        // noon: both go, the clearing attack first
        clock.set(nextBerlin(LocalTime.NOON));
        remember(lord, m -> m.intel.put(human.villageId, withAt(defended, clock.instant())));
        round(lord);
        List<Map<String, Object>> train = movementsTo(lord, human.villageId);
        assertThat(train).hasSize(2);
        assertThat(train.get(0)).containsEntry("u_noble", 0);
        assertThat(train.get(1)).containsEntry("u_noble", 4);
        assertThat(memory(lord).attacksSent()).hasSize(1);
    }

    // ---- reports (§4) ----

    @Test
    void theLordReadsItsScoutReportIntoIntel() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.SCOUT, 10));
        long barbarian = barbarian(3, 0, 1);

        round(lord); // scouts go
        clock.advance(Duration.ofMinutes(30)); // 27 minutes there
        processor.processDue(clock.instant());
        clock.advance(Duration.ofMinutes(1));
        round(lord);

        Intel intel = memory(lord).intel().get(barbarian);
        assertThat(intel).isNotNull();
        assertThat(intel.units()).isEmpty();
        assertThat(intel.wall()).isEqualTo(1);
        assertThat(intel.resources()).isNotNull();
        assertThat(intel.buildings()).containsEntry(BuildingType.WAREHOUSE, 5);
    }

    // ---- helpers: the scene ----

    /** One lord of the personality, its village moved to the scene's origin. */
    private long lord(NpcPersonality personality) {
        return lords(1, personality).getFirst();
    }

    /**
     * {@code count} lords; the first one's village at the origin, the others where they spawned.
     */
    private List<Long> lords(int count, NpcPersonality personality) {
        jdbc.sql(
                        "update world_settings set initialized_at = null, barbarian_initial_count ="
                                + " 0, npc_lord_count = ?")
                .param(count)
                .update();
        worldService.reload();
        initializer.initialize();
        List<Long> ids =
                players.findAll().stream()
                        .filter(Player::isNpc)
                        .map(Player::getId)
                        .sorted(Comparator.naturalOrder())
                        .toList();
        for (long id : ids) {
            jdbc.sql("update npc_brain set personality = ?, template = ? where player_id = ?")
                    .param(personality.name())
                    .param(personality.name())
                    .param(id)
                    .update();
        }
        place(villageOf(ids.getFirst()), 0, 0);
        jdbc.sql("delete from game_event").update();
        return ids;
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

    /** A registered human with his village moved into the scene. */
    private TestPlayer human(int dx, int dy, boolean protectionOver) {
        TestPlayer human;
        try {
            human = TestPlayer.register(mockMvc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Village village = villages.findById(human.villageId).orElseThrow();
        movedHumans.add(new long[] {human.villageId, village.getX(), village.getY()});
        place(human.villageId, dx, dy);
        if (protectionOver) {
            jdbc.sql("update player set protected_until = ? where id = ?")
                    .param(java.sql.Timestamp.from(clock.instant().minusSeconds(3600)))
                    .param(human.playerId)
                    .update();
        }
        return human;
    }

    /** Another village for the player at the given place in the scene. */
    private long addVillage(long playerId, int dx, int dy) {
        return transaction.execute(
                status -> {
                    Player player = players.findById(playerId).orElseThrow();
                    BuildingLevels levels = startLevels();
                    int points = BuildRules.villagePoints(gameData, levels.toMap());
                    Village village =
                            villages.save(
                                    new Village(
                                            player,
                                            "Second",
                                            new Coordinates(baseX + dx, baseY + dy),
                                            500,
                                            levels,
                                            points,
                                            clock.instant()));
                    stacks.save(UnitStack.homeOf(village));
                    player.addVillage(points);
                    return village.getId();
                });
    }

    /** A barbarian village at the given place, start levels, the given wall, no units. */
    private long barbarian(int dx, int dy, int wall) {
        return transaction.execute(
                status -> {
                    BuildingLevels levels = startLevels();
                    levels.set(BuildingType.WAREHOUSE, 5);
                    levels.set(BuildingType.WALL, wall);
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

    private BuildingLevels startLevels() {
        var levels = new BuildingLevels();
        for (BuildingType type : BuildingType.values()) {
            levels.set(type, gameData.building(type).startLevel());
        }
        return levels;
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

    private void points(long villageId, int points) {
        jdbc.sql("update village set points = ? where id = ?")
                .param(points)
                .param(villageId)
                .update();
    }

    private void setDifficulty(String difficulty) {
        jdbc.sql("update world_settings set npc_difficulty = ?").param(difficulty).update();
        worldService.reload();
    }

    /** A foreign attack on the way to {@code villageId} (only the row: it never arrives). */
    private long incomingAttack(
            TestPlayer from, long villageId, Instant departAt, Instant arriveAt) {
        return jdbc.sql(
                        """
                        insert into movement (kind, home_village_id, source_village_id,
                            target_village_id, owner_player_id, u_spearman, u_swordsman,
                            u_axeman, u_scout, u_light_cavalry, u_heavy_cavalry, u_ram,
                            u_catapult, u_noble, depart_at, arrive_at, created_at)
                        values ('ATTACK', ?, ?, ?, ?, 0, 0, 10, 0, 0, 0, 0, 0, 0, ?, ?, ?)
                        returning id
                        """)
                .param(from.villageId)
                .param(from.villageId)
                .param(villageId)
                .param(from.playerId)
                .param(java.sql.Timestamp.from(departAt))
                .param(java.sql.Timestamp.from(arriveAt))
                .param(java.sql.Timestamp.from(departAt))
                .query(Long.class)
                .single();
    }

    // ---- helpers: memory ----

    private NpcMemory memory(long lord) {
        return brains.findById(lord).orElseThrow().getMemory();
    }

    private void remember(long lord, java.util.function.Consumer<NpcMemory.Draft> change) {
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    change.accept(draft);
                    brain.setMemory(draft.build());
                });
    }

    private void knows(long lord, long villageId, Intel intel) {
        remember(lord, m -> m.intel.put(villageId, intel));
    }

    /** Fresh intel of a raid target that showed nobody at home, the attack on it reset. */
    private void freshRaidSetup(long lord, long village, TestPlayer human) {
        deleteMovementsOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 50));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                });
    }

    private static Intel emptyIntel(Instant at, long ownerId) {
        return new Intel(
                at,
                ownerId,
                Map.of(),
                0,
                null,
                null,
                null,
                false,
                BattleOutcome.ATTACKER_WON,
                0,
                null,
                null);
    }

    private static Intel withAt(Intel intel, Instant at) {
        return new Intel(
                at,
                intel.ownerId(),
                intel.units(),
                intel.wall(),
                intel.buildings(),
                intel.resources(),
                intel.loot(),
                intel.lootFull(),
                intel.outcome(),
                intel.scoutFailures(),
                intel.leftAloneUntil(),
                null);
    }

    // ---- helpers: rounds and what they sent ----

    /** One round of the lord now, through the event worker. */
    private void round(long lord) {
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lord, clock.instant()));
        processor.processDue(clock.instant());
    }

    private List<Map<String, Object>> movementsTo(long lord, long villageId) {
        return jdbc.sql(
                        """
                        select kind, u_scout, u_axeman, u_noble, arrive_at from movement
                        where owner_player_id = ? and target_village_id = ? and kind = 'ATTACK'
                        order by arrive_at, id
                        """)
                .param(lord)
                .param(villageId)
                .query()
                .listOfRows();
    }

    private List<Instant> attacksTo(long lord, long villageId) {
        return movementsTo(lord, villageId).stream()
                .map(m -> ((java.sql.Timestamp) m.get("arrive_at")).toInstant())
                .toList();
    }

    private List<Map<String, Object>> supportsFrom(long villageId) {
        return jdbc.sql(
                        """
                        select target_village_id, u_spearman from movement
                        where home_village_id = ? and kind = 'SUPPORT'
                        """)
                .param(villageId)
                .query()
                .listOfRows();
    }

    private void deleteMovementsOf(long lord) {
        List<Long> ids =
                jdbc.sql("select id from movement where owner_player_id = ?")
                        .param(lord)
                        .query(Long.class)
                        .list();
        for (long id : ids) {
            jdbc.sql("delete from game_event where kind = 'MOVEMENT_ARRIVE' and ref_id = ?")
                    .param(id)
                    .update();
            jdbc.sql("delete from movement where id = ?").param(id).update();
        }
    }

    private Long ownerOf(long villageId) {
        return jdbc.sql("select player_id from village where id = ?")
                .param(villageId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private static LocalTime berlinTime(Instant at) {
        return at.atZone(BERLIN).toLocalTime();
    }

    /** The next moment (not before now) at which the clock in Berlin shows {@code time}. */
    private Instant nextBerlin(LocalTime time) {
        LocalDateTime candidate = clock.instant().atZone(BERLIN).toLocalDate().atTime(time);
        Instant at = candidate.atZone(BERLIN).toInstant();
        return at.isBefore(clock.instant()) ? candidate.plusDays(1).atZone(BERLIN).toInstant() : at;
    }
}
