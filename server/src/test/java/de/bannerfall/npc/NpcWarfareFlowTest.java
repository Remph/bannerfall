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
import de.bannerfall.guild.GuildService;
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
    @Autowired GuildService guildService;
    @Autowired NpcGuilds npcGuilds;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2000;

    /** Human villages moved into the scene, with their old fields: put back afterwards. */
    private final List<long[]> movedHumans = new ArrayList<>();

    private boolean guildsWereOn;

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
        guildsWereOn = worldService.settings().guildsEnabled();
    }

    @AfterEach
    void cleanUp() {
        random.reset();
        jdbc.sql("delete from game_event").update();
        String scene = "select id from village where x >= 2000";
        jdbc.sql(
                        "delete from trade_transport where source_village_id in (%1$s) or target_village_id in (%1$s)"
                                .formatted(scene))
                .update();
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
        jdbc.sql("delete from report where player_id in (select id from player where npc)")
                .update();
        jdbc.sql(
                        "delete from guild where id in (select guild_id from guild_member where"
                                + " player_id in (select id from player where npc))")
                .update();
        jdbc.sql("delete from player where npc").update();
        // barbarian villages of the scene (and villages conquered by a lord, now barbarian-less)
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        "update world_settings set npc_lord_count = 0, npc_difficulty = 'NORMAL',"
                                + " npc_custom = null, speed = 1.0, barbarian_initial_count = 30,"
                                + " barbarians_per_new_player = 0, npc_guilds_enabled = false,"
                                + " night_bonus_enabled = false, night_bonus_start_hour = 23,"
                                + " night_bonus_end_hour = 7, npc_banners_enabled = false,"
                                + " npc_shipments_enabled = true,"
                                + " guilds_enabled = ?")
                .param(guildsWereOn)
                .update();
        worldService.reload();
    }

    private void nightBonus(int start, int end) {
        jdbc.sql(
                        "update world_settings set night_bonus_enabled = true,"
                                + " night_bonus_start_hour = ?, night_bonus_end_hour = ?,"
                                + " night_bonus_factor = 2.0")
                .params(start, end)
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

        // all lords together: 8 a day per human on NORMAL. The other lord sent 7: one more fits
        clock.advance(Duration.ofHours(2)); // past the farm cooldown
        Instant third = clock.instant();
        freshStart(lord, human);
        remember(
                other,
                m -> {
                    for (int i = 0; i < 7; i++) {
                        m.attacksSent.add(
                                new NpcMemory.SentAttack(human.playerId, third.minusSeconds(60)));
                    }
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).hasSize(1);

        // the other lord sent 8: none more
        freshStart(lord, human);
        remember(other, m -> m.attacksSent.add(new NpcMemory.SentAttack(human.playerId, third)));
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
    }

    /**
     * The lord as before its first command to the human: none under way, the axemen (who left with
     * the last one) home again, nothing sent or farmed in its memory, fresh intel of the target.
     */
    private void freshStart(long lord, TestPlayer human) {
        deleteMovementsOf(lord);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 50));
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(now, human.playerId));
                });
    }

    @Test
    void noCommandArrivesAtAHumanVillageInTheQuietHours() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        TestPlayer human = human(3, 0, true);
        // 3 fields with axemen: 54 minutes
        // NORMAL without the farm rest (§6.2), which would hold the 06:06 raid back: only the
        // quiet hours are tested here
        jdbc.sql(
                        """
                        update world_settings set npc_difficulty = 'CUSTOM', npc_custom =
                          '{"productionFactor":0.9,"boostBelow":0.5,"boostFactor":1.25,
                            "leadMargin":1.25,"attacksPerPlayerPerDay":4,"attacksPerHumanPerDay":8,
                            "quietHours":true,"spareLastVillage":true,"buildQuietFrom":"23:00",
                            "buildQuietTo":"07:00","farmLootShare":0.5,"farmRest":false}'::jsonb
                        """)
                .update();
        worldService.reload();

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
    void theLordCountsTheNightBonusAtTheArrival() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        // 3 axemen, 1 of them the home guard: 2 (A = 80) against an empty village, D = 20 by
        // day, a loss of 0.125; 40 at night, a loss of 0.354, above farm-max-loss 0.25. 3 fields:
        // they arrive at 12:54.
        units(village, Map.of(UnitType.AXEMAN, 3));
        TestPlayer human = human(3, 0, true);

        // the night (1.4, 02 §8.1) set to 15:00–16:00: by day at the arrival, sent
        nightBonus(15, 16);
        knows(lord, human.villageId, emptyIntel(clock.instant(), human.playerId));
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).hasSize(1);

        // set to 12:00–14:00: the raid would land in it, so the lord keeps its axemen home
        nightBonus(12, 14);
        deleteMovementsOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 3));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
    }

    @Test
    void theLordsHasteBannerCountsForTheNightArrival() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        // the raid of the test above, 3.6 fields away: 64.9 minutes, at 13:05 by day; under a
        // tier III Haste banner (15 %) 55.2 minutes, at 12:55, in a night of 12:00–13:00
        units(village, Map.of(UnitType.AXEMAN, 3));
        TestPlayer human = human(3, 2, true);
        nightBonus(12, 13);
        knows(lord, human.villageId, emptyIntel(clock.instant(), human.playerId));
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).hasSize(1);

        // the same raid with the banner flying (lord banners on, 1.4): the lord keeps them home
        jdbc.sql("update world_settings set banners_enabled = true, npc_banners_enabled = true")
                .update();
        worldService.reload();
        buildings(village, Map.of(BuildingType.BANNER_HALL, 3));
        jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, 'HASTE', 3, ?, now(), 'GRANTED', now())
                        """)
                .params(lord, village)
                .update();
        deleteMovementsOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 3));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.intel.put(human.villageId, emptyIntel(clock.instant(), human.playerId));
                });
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
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
    void anAcademyScoutsAPlayerVillageBeforeItSavesForNoblesForIt() {
        // balancing round 3 (Realm 1, 2026-10-20): a lord picked the biggest human village it
        // had never seen as its target and saved for nobles at the noble troop share, its army gone
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1, BuildingType.STABLE, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 10));
        TestPlayer human = human(3, 0, true);
        addVillage(human.playerId, 60, 60); // not his last village
        points(human.villageId, 5000);

        round(lord);

        // no target yet, so the village keeps building and recruiting at its stage's share
        assertThat(memory(lord).conquest()).doesNotContainKey(village);
        assertThat(movementsTo(lord, human.villageId))
                .singleElement()
                .satisfies(m -> assertThat(m).containsEntry("u_scout", 5));
    }

    @Test
    void aPlayerVillageSeenTooStrongStaysNoTargetUntilTheLordForgetsIt() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4, UnitType.SCOUT, 10));
        TestPlayer human = human(3, 0, true);
        addVillage(human.playerId, 60, 60); // not his last village
        points(human.villageId, 5000);
        // seen long ago (no longer fresh), but still the latest the lord knows
        knows(
                lord,
                human.villageId,
                strongIntel(clock.instant().minus(Duration.ofHours(12)), human.playerId));

        round(lord);

        // no target, and not scouted again either: every scouting counts against the caps
        assertThat(memory(lord).conquest()).doesNotContainKey(village);
        assertThat(movementsTo(lord, human.villageId)).isEmpty();

        // once the lord has forgotten that look (7 days ÷ speed), its scouts go to look again
        clock.advance(Duration.ofMinutes(1));
        knows(
                lord,
                human.villageId,
                strongIntel(
                        clock.instant().minus(Duration.ofDays(7)).minusSeconds(60),
                        human.playerId));

        round(lord);

        assertThat(memory(lord).conquest()).doesNotContainKey(village);
        assertThat(movementsTo(lord, human.villageId))
                .singleElement()
                .satisfies(
                        m -> {
                            assertThat(((Number) m.get("u_scout")).intValue()).isPositive();
                            assertThat(m).containsEntry("u_noble", 0);
                        });

        // seen empty, just as long ago: a target again, and the scouts go to look once more
        deleteMovementsOf(lord);
        clock.advance(Duration.ofMinutes(1));
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.intel.put(
                            human.villageId,
                            emptyIntel(
                                    clock.instant().minus(Duration.ofHours(12)), human.playerId));
                });
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 4, UnitType.SCOUT, 10));

        round(lord);

        assertThat(memory(lord).conquest().get(village).villageId()).isEqualTo(human.villageId);
        // scouts only (the intel is too old to send the train on): the train waits for their look
        assertThat(movementsTo(lord, human.villageId))
                .singleElement()
                .satisfies(
                        m -> {
                            assertThat(((Number) m.get("u_scout")).intValue()).isPositive();
                            assertThat(m).containsEntry("u_noble", 0);
                        });
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

    // ---- v1.3: better NPC logic ----

    @Test
    void aRetaliationTooStrongForTheUnitsAtHomeWaitsForTheWholeArmyOrIsLetGo() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        long other = addVillage(lord, -10, 0); // farther from the attacker
        units(village, Map.of(UnitType.AXEMAN, 10));
        // 90 axemen of the village stand in support in the lord's other village
        supportStack(village, other, Map.of(UnitType.AXEMAN, 90));
        TestPlayer human = human(30, 0, true);
        long barbarian = barbarian(3, 0, 0);
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, spearmenIntel(now, human.playerId, 30));
                    m.intel.put(barbarian, barbarianIntel(now));
                    m.grudges.put(
                            human.playerId,
                            new NpcMemory.Grudge(
                                    now.plus(Duration.ofHours(10)), human.villageId, false, 0));
                });

        // the 10 at home can't beat 30 spearmen, all 100 can: it waits, and gathers its axemen
        // instead of sending them farming
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
        assertThat(memory(lord).grudges().get(human.playerId).retaliated()).isFalse();
        assertThat(movementsTo(lord, barbarian)).isEmpty();

        // (the support was sent home meanwhile: no attack is due in the other village)
        // without those 90 the whole army is too weak: the lord lets it go and farms again
        jdbc.sql("delete from unit_stack where home_village_id = ? and location_village_id = ?")
                .param(village)
                .param(other)
                .update();
        deleteMovementsOf(lord);
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(attacksTo(lord, human.villageId)).isEmpty();
        assertThat(memory(lord).grudges().get(human.playerId).retaliated()).isTrue();
        assertThat(movementsTo(lord, barbarian))
                .singleElement()
                .satisfies(m -> assertThat((Integer) m.get("u_axeman")).isPositive());
    }

    @Test
    void aNearestVillageWithoutAttackUnitsLeavesTheRetaliationToTheOthers() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        long other = addVillage(lord, 0, 5); // farther from the attacker
        units(village, Map.of(UnitType.SPEARMAN, 20)); // no attack units at all
        units(other, Map.of(UnitType.AXEMAN, 100));
        TestPlayer human = human(30, 0, true);
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, spearmenIntel(now, human.playerId, 30));
                    m.grudges.put(
                            human.playerId,
                            new NpcMemory.Grudge(
                                    now.plus(Duration.ofHours(10)), human.villageId, false, 0));
                });

        // the nearest village has nothing to decide with: it doesn't let the grudge go, so the
        // other village's 100 axemen retaliate
        round(lord);

        assertThat(attacksTo(lord, human.villageId)).hasSize(1);
        assertThat(memory(lord).grudges().get(human.playerId).retaliated()).isTrue();
    }

    @Test
    void aVillageUnderAttackKeepsItsSupportUnitsOffTheFarms() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 100));
        long barbarian = barbarian(3, 0, 0);
        TestPlayer human = human(0, 10, false);
        Instant now = clock.instant();
        knows(lord, barbarian, barbarianIntel(now));
        // an army at 18 min/field, an hour away
        long attack =
                incomingAttack(
                        human, village, now.minusSeconds(120 * 60), now.plusSeconds(60 * 60));

        round(lord);
        assertThat(farmRaidsTo(lord, barbarian)).isEmpty();

        // the attack is gone: the spearmen farm again
        jdbc.sql("delete from movement where id = ?").param(attack).update();
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(farmRaidsTo(lord, barbarian))
                .singleElement()
                .satisfies(spearmen -> assertThat(spearmen).isPositive());
    }

    @Test
    void aLordRestsFromFarmingAtNightAndOnceItsFarmBudgetIsSpent() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 100));
        long barbarian = barbarian(3, 0, 0);

        // NORMAL rests in its build quiet hours (23:00–07:00 Berlin)
        clock.set(nextBerlin(LocalTime.of(1, 0)));
        knows(lord, barbarian, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, barbarian)).isEmpty();

        clock.set(nextBerlin(LocalTime.NOON));
        knows(lord, barbarian, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, barbarian)).hasSize(1);

        // far more than half a day's production looted in the last 24 hours: budget spent
        deleteMovementsOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 100));
        clock.advance(Duration.ofMinutes(1));
        Instant now = clock.instant();
        lootReport(lord, now.minus(Duration.ofHours(23)), 30_000);
        remember(
                lord,
                m -> {
                    m.farmed.clear();
                    m.intel.put(barbarian, barbarianIntel(now));
                });
        round(lord);
        assertThat(farmRaidsTo(lord, barbarian)).isEmpty();

        // an hour later that loot is older than 24 hours
        clock.advance(Duration.ofHours(1));
        knows(lord, barbarian, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, barbarian)).hasSize(1);
    }

    @Test
    void noScoutingForARaidWhileTheFarmBudgetIsSpent() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 50, UnitType.SCOUT, 10));
        TestPlayer human = human(3, 0, true);
        knows(
                lord,
                human.villageId,
                emptyIntel(clock.instant().minus(Duration.ofHours(7)), human.playerId));
        lootReport(lord, clock.instant().minus(Duration.ofHours(1)), 30_000);

        round(lord);

        // the raid wouldn't be sent, so the scouting (which counts against the caps) isn't either
        assertThat(movementsTo(lord, human.villageId)).isEmpty();
    }

    @Test
    void whatTheFarmsOnTheWayCanCarryCountsTowardsThePace() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 100));
        long first = barbarian(3, 0, 0);
        long second = barbarian(0, 3, 0);
        clock.set(nextBerlin(LocalTime.NOON));
        knows(lord, first, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, first)).hasSize(1);

        // fresh spearmen at home and a second target, but the raid on the way can carry far more
        // than the 2 hours' share of a starting village's budget: no loot reported yet, no farm
        units(village, Map.of(UnitType.SPEARMAN, 100));
        clock.advance(Duration.ofMinutes(1));
        knows(lord, second, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, second)).isEmpty();

        // with the first raid gone, the same round farms the second target
        deleteMovementsOf(lord);
        clock.advance(Duration.ofMinutes(1));
        knows(lord, second, barbarianIntel(clock.instant()));
        round(lord);
        assertThat(farmRaidsTo(lord, second)).hasSize(1);
    }

    @Test
    void theNoblesAreCountedForTheLoyaltyAtTheirArrival() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 2));
        long barbarian = barbarian(3, 0, 0);
        knows(lord, barbarian, barbarianIntel(clock.instant()));
        // 3 fields at 35 min/field: 105 minutes, 1.75 loyalty on the way (1 an hour at speed 1)
        loyalty(barbarian, 49); // 2 nobles now, 50.75 at arrival needs 3

        round(lord);
        assertThat(movementsTo(lord, barbarian))
                .allSatisfy(m -> assertThat(m).containsEntry("u_noble", 0));

        deleteMovementsOf(lord);
        remember(
                lord,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                });
        loyalty(barbarian, 47); // 48.75 at arrival: 2 are enough
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(movementsTo(lord, barbarian))
                .anySatisfy(m -> assertThat(m).containsEntry("u_noble", 2));
    }

    @Test
    void theSupportsInANewlyConqueredVillageStayUntilItsLoyaltyIsBackAtHalf() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        long conquered = addVillage(lord, 3, 0);
        supportStack(village, conquered, Map.of(UnitType.AXEMAN, 40));
        loyalty(conquered, 25); // just taken

        round(lord);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(village, conquered)).isPresent();

        loyalty(conquered, 50);
        clock.advance(Duration.ofMinutes(1));
        round(lord);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(village, conquered)).isEmpty();
    }

    // ---- lord guilds (§13) ----

    @Test
    void neighbourLordsFoundAGuildInTheirRoundAndAFarOneJoinsOnceItIsNear() {
        List<Long> lords = lords(3, NpcPersonality.BALANCED);
        long a = lords.get(0);
        long b = lords.get(1);
        long c = lords.get(2);
        place(villageOf(b), 8, 0);
        place(villageOf(c), 40, 0);
        lords.forEach(id -> lordPoints(id, 900));
        lordGuildsOn();

        round(a);

        Long guild = guildOf(a);
        assertThat(guild).isNotNull();
        assertThat(guildOf(b)).isEqualTo(guild);
        assertThat(roleOf(a)).isEqualTo("LEADER");
        assertThat(guildOf(c)).isNull();
        round(c);
        assertThat(guildOf(c)).isNull(); // 32 fields from b, nobody near: never alone

        place(villageOf(c), 20, 0); // 12 from b
        lordPoints(c, 900);
        clock.advance(Duration.ofMinutes(1));
        round(c);
        assertThat(guildOf(c)).isEqualTo(guild);
    }

    @Test
    void guildMatesNeverRaidEachOther() {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        long a = lords.get(0);
        long b = lords.get(1);
        long target = villageOf(b);
        place(target, 3, 0);
        units(villageOf(a), Map.of(UnitType.AXEMAN, 50));
        unprotect(b);
        knows(a, target, emptyIntel(clock.instant(), b));
        lordGuildsOn();
        guildService.foundForLords(a, b, "Ordo Testium", "TEST");

        round(a);
        assertThat(attacksTo(a, target)).isEmpty();

        // no guild mates any more: the same village is a raid target
        guildService.removeLord(b);
        clock.advance(Duration.ofMinutes(1));
        knows(a, target, emptyIntel(clock.instant(), b));
        round(a);
        assertThat(attacksTo(a, target)).hasSize(1);
    }

    @Test
    void aLordSupportsAGuildMatesVillageUnderAttackAndWithdrawsAfterwards() {
        List<Long> lords = lords(2, NpcPersonality.DEFENSIVE);
        long a = lords.get(0);
        long b = lords.get(1);
        long helper = villageOf(a);
        long attacked = villageOf(b);
        place(attacked, 3, 0);
        units(helper, Map.of(UnitType.SPEARMAN, 100));
        TestPlayer human = human(3, 10, false);
        lordGuildsOn();
        guildService.foundForLords(a, b, "Ordo Testium", "TEST");
        Instant now = clock.instant();
        long attack =
                incomingAttack(
                        human, attacked, now.minusSeconds(120 * 60), now.plusSeconds(60 * 60));

        round(a);

        assertThat(supportsFrom(helper))
                .singleElement()
                .satisfies(
                        s ->
                                assertThat(s)
                                        .containsEntry("u_spearman", 50)
                                        .containsEntry("target_village_id", attacked));

        // the attack is over, the village loyal: the support goes home
        jdbc.sql("delete from movement where id = ?").param(attack).update();
        deleteMovementsOf(a);
        supportStack(helper, attacked, Map.of(UnitType.SPEARMAN, 50));
        loyalty(attacked, 100);
        clock.advance(Duration.ofMinutes(1));
        round(a);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(helper, attacked)).isEmpty();

        // while an attack is due it stays, unless the lord is no guild mate any more
        deleteMovementsOf(a);
        supportStack(helper, attacked, Map.of(UnitType.SPEARMAN, 50));
        now = clock.instant();
        incomingAttack(human, attacked, now.minusSeconds(60 * 60), now.plusSeconds(60 * 60));
        clock.advance(Duration.ofMinutes(1));
        round(a);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(helper, attacked)).isPresent();

        guildService.removeLord(b);
        clock.advance(Duration.ofMinutes(1));
        round(a);
        assertThat(stacks.findByHomeVillageIdAndLocationVillageId(helper, attacked)).isEmpty();
    }

    @Test
    void aGuildMateTakesOverTheGrudgeAndRetaliates() {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        long a = lords.get(0);
        long b = lords.get(1);
        place(villageOf(b), 45, 0);
        units(villageOf(a), Map.of(UnitType.AXEMAN, 100));
        // 20 fields from a: within its conquest radius (25)
        TestPlayer human = human(20, 0, true);
        Instant until = clock.instant().plus(Duration.ofHours(10));
        remember(
                b,
                m ->
                        m.grudges.put(
                                human.playerId,
                                new NpcMemory.Grudge(until, human.villageId, false, 0)));
        knows(a, human.villageId, emptyIntel(clock.instant(), human.playerId));
        lordGuildsOn();
        guildService.foundForLords(a, b, "Ordo Testium", "TEST");

        round(a);

        assertThat(attacksTo(a, human.villageId)).hasSize(1);
        assertThat(memory(a).grudges().get(human.playerId))
                .satisfies(
                        g -> {
                            assertThat(g.shared()).isTrue();
                            assertThat(g.retaliated()).isTrue();
                            assertThat(g.until()).isEqualTo(until);
                        });
    }

    @Test
    void theGuildBrakeComparesPointsPerMember() {
        // §13.5, per member since 1.6: two lords of 900 and 1500 are 1200 a member
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        lordPoints(lords.get(0), 900);
        lordPoints(lords.get(1), 1500);
        lordGuildsOn();
        guildService.foundForLords(lords.get(0), lords.get(1), "Ordo Testium", "TEST");
        assertThat(npcGuilds.guildAverage(lords.get(0))).contains(1200.0);

        // far above every other human in the test world: a guild of 9 and 1 million is 5 million
        // a member, so a lone human with 6 million is the strongest side (as sums the guild was)
        TestPlayer strong = human(30, 0, false);
        TestPlayer weak = human(33, 0, false);
        TestPlayer loner = human(36, 0, false);
        String unique = Long.toString(System.nanoTime(), 36);
        long guild =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .params("Humans " + unique, unique.substring(unique.length() - 4))
                        .query(Long.class)
                        .single();
        jdbc.sql(
                        "insert into guild_member (player_id, guild_id, role, joined_at)"
                                + " values (?, ?, 'LEADER', now()), (?, ?, 'MEMBER', now())")
                .params(strong.playerId, guild, weak.playerId, guild)
                .update();
        try {
            humanPoints(strong, 9_000_000);
            humanPoints(weak, 1_000_000);
            humanPoints(loner, 6_000_000);
            assertThat(npcGuilds.strongestHumanSide()).isEqualTo(6_000_000.0);

            humanPoints(loner, 0);
            assertThat(npcGuilds.strongestHumanSide()).isEqualTo(5_000_000.0);
        } finally {
            jdbc.sql("delete from guild where id = ?").param(guild).update();
            for (TestPlayer human : List.of(strong, weak, loner)) {
                humanPoints(human, 0);
            }
        }
    }

    private void humanPoints(TestPlayer human, int points) {
        jdbc.sql("update player set points = ? where id = ?")
                .param(points)
                .param(human.playerId)
                .update();
    }

    @Test
    void aLordSendsNoTrainWhereAGuildMatesNoblesAreOnTheWay() {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        long a = lords.get(0);
        long b = lords.get(1);
        long village = villageOf(a);
        place(villageOf(b), 40, 0);
        buildings(village, Map.of(BuildingType.ACADEMY, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 2));
        long barbarian = barbarian(3, 0, 0);
        knows(a, barbarian, barbarianIntel(clock.instant()));
        loyalty(barbarian, 40);
        lordGuildsOn();
        guildService.foundForLords(a, b, "Ordo Testium", "TEST");
        long train = nobleAttack(b, barbarian);

        round(a);
        assertThat(movementsTo(a, barbarian))
                .allSatisfy(m -> assertThat(m).containsEntry("u_noble", 0));

        // the mate's train is gone (it arrived): the village is a target again (unbraked: the
        // humans of other tests may be weaker than the two lords together)
        jdbc.sql("delete from movement where id = ?").param(train).update();
        deleteMovementsOf(a);
        remember(
                a,
                m -> {
                    m.attacksSent.clear();
                    m.farmed.clear();
                    m.stance = NpcMemory.Stance.NONE;
                });
        clock.advance(Duration.ofMinutes(1));
        round(a);
        assertThat(movementsTo(a, barbarian))
                .anySatisfy(m -> assertThat(m).containsEntry("u_noble", 2));
    }

    // ---- shipments (§5.5, 1.6) ----

    @Test
    void aVillageThatWouldOverflowShipsToTheLordsVillageThatStillBuilds() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long receiver = addVillage(lord, 5, 0);
        int capacity = shipmentScene(donor, receiver);

        round(lord);

        List<Map<String, Object>> sent = transportsFrom(donor);
        assertThat(sent).hasSize(1);
        Map<String, Object> transport = sent.getFirst();
        assertThat(transport.get("target_village_id")).isEqualTo(receiver);
        int wood = (Integer) transport.get("wood");
        int clay = (Integer) transport.get("clay");
        int iron = (Integer) transport.get("iron");
        // the market's 10 merchants are full, evenly from each resource
        assertThat(transport.get("merchants")).isEqualTo(10);
        assertThat(wood + clay + iron).isBetween(9_990, 10_000);
        assertThat(List.of(wood, clay, iron))
                .allSatisfy(n -> assertThat(n).isBetween(3_300, 3_334));
        // the donor keeps at least 70 % of its warehouse of each
        Village after = villages.findById(donor).orElseThrow();
        assertThat(after.getWood()).isGreaterThanOrEqualTo(0.7 * capacity);
        assertThat(after.getClay()).isGreaterThanOrEqualTo(0.7 * capacity);
        assertThat(after.getIron()).isGreaterThanOrEqualTo(0.7 * capacity);

        // the merchants arrive and fill the receiver's warehouse
        clock.set(((Timestamp) transport.get("arrive_at")).toInstant());
        processor.processDue(clock.instant());
        assertThat(villages.findById(receiver).orElseThrow().getWood()).isGreaterThan(3_300);
    }

    @Test
    void whatIsOnTheWayCountsSoTheNextRoundDoesNotOverfillTheReceiver() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long receiver = addVillage(lord, 5, 0);
        int capacity = shipmentScene(donor, receiver);
        buildings(donor, Map.of(BuildingType.MARKET, 15)); // 35 merchants: enough for two trips
        buildings(receiver, Map.of(BuildingType.WAREHOUSE, 10));
        int fill = (int) (0.8 * de.bannerfall.rules.Storage.warehouseCapacity(10));

        round(lord);
        assertThat(transportsFrom(donor)).hasSize(1);
        // the donor is full again, the merchants are still on the way
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(capacity, capacity, capacity, donor)
                .update();
        round(lord);

        List<Map<String, Object>> sent = transportsFrom(donor);
        for (String resource : List.of("wood", "clay", "iron")) {
            assertThat(sent.stream().mapToInt(t -> (Integer) t.get(resource)).sum())
                    .isLessThanOrEqualTo(fill);
        }
    }

    @Test
    void aStockBelowNinetyPercentIsNotShipped() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long receiver = addVillage(lord, 5, 0);
        int capacity = shipmentScene(donor, receiver);
        int below = (int) (0.89 * capacity);
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(below, below, below, donor)
                .update();

        round(lord);

        assertThat(transportsFrom(donor)).isEmpty();
    }

    @Test
    void aVillageWithAnAcademyGetsNoShipment() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long receiver = addVillage(lord, 5, 0);
        shipmentScene(donor, receiver);
        buildings(receiver, Map.of(BuildingType.ACADEMY, 1));

        round(lord);

        assertThat(transportsFrom(donor)).isEmpty();
    }

    @Test
    void aVillageUnderAttackGetsNoShipmentButTheNextVillageDoes() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long attacked = addVillage(lord, 3, 0);
        long other = addVillage(lord, 8, 0);
        shipmentScene(donor, attacked);
        buildings(other, Map.of(BuildingType.WAREHOUSE, 15));
        jdbc.sql("update village set wood = 0, clay = 0, iron = 0 where id = ?")
                .param(other)
                .update();
        TestPlayer human = human(13, 0, true);
        incomingAttack(
                human,
                attacked,
                clock.instant().minus(Duration.ofHours(1)),
                clock.instant().plus(Duration.ofHours(1)));

        round(lord);

        assertThat(transportsFrom(donor))
                .extracting(t -> t.get("target_village_id"))
                .containsExactly(other);
    }

    @Test
    void noShipmentsWithTheSwitchOffOrInTheBuildQuietHours() {
        long lord = lord(NpcPersonality.BALANCED);
        long donor = villageOf(lord);
        long receiver = addVillage(lord, 5, 0);
        shipmentScene(donor, receiver);
        jdbc.sql("update world_settings set npc_shipments_enabled = false").update();
        worldService.reload();

        round(lord);
        assertThat(transportsFrom(donor)).isEmpty();

        jdbc.sql("update world_settings set npc_shipments_enabled = true").update();
        worldService.reload();
        clock.set(nextBerlin(LocalTime.of(1, 0))); // NORMAL: no new build orders 23:00-07:00
        shipmentScene(donor, receiver);

        round(lord);
        assertThat(transportsFrom(donor)).isEmpty();
    }

    /**
     * The donor: warehouse 15 full of each resource, market 10, a farm full of spearmen (so its own
     * step can neither recruit nor afford much); the receiver: start levels with warehouse 15,
     * empty.
     *
     * @return the warehouse capacity of both
     */
    private int shipmentScene(long donor, long receiver) {
        buildings(
                donor,
                Map.of(BuildingType.WAREHOUSE, 15, BuildingType.MARKET, 10, BuildingType.FARM, 1));
        units(donor, Map.of(UnitType.SPEARMAN, 1000));
        buildings(receiver, Map.of(BuildingType.WAREHOUSE, 15));
        int capacity = de.bannerfall.rules.Storage.warehouseCapacity(15);
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(capacity, capacity, capacity, Timestamp.from(clock.instant()), donor)
                .update();
        jdbc.sql(
                        "update village set wood = 0, clay = 0, iron = 0, resources_updated_at = ?"
                                + " where id = ?")
                .params(Timestamp.from(clock.instant()), receiver)
                .update();
        return capacity;
    }

    private List<Map<String, Object>> transportsFrom(long villageId) {
        return jdbc.sql("select * from trade_transport where source_village_id = ? order by id")
                .param(villageId)
                .query()
                .listOfRows();
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

    /** {@code units} of {@code homeId} standing in support in {@code locationId}. */
    private void supportStack(long homeId, long locationId, Map<UnitType, Integer> units) {
        transaction.executeWithoutResult(
                status -> {
                    UnitStack stack =
                            new UnitStack(
                                    villages.findById(homeId).orElseThrow(),
                                    villages.findById(locationId).orElseThrow());
                    units.forEach(stack.getUnits()::set);
                    stacks.save(stack);
                });
    }

    private void loyalty(long villageId, double loyalty) {
        jdbc.sql("update village set loyalty = ?, loyalty_updated_at = ? where id = ?")
                .param(loyalty)
                .param(Timestamp.from(clock.instant()))
                .param(villageId)
                .update();
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

    /** Intel of a village with 1,000 spearmen behind a level 10 wall. */
    private static Intel strongIntel(Instant at, long ownerId) {
        return new Intel(
                at,
                ownerId,
                Map.of(UnitType.SPEARMAN, 1000),
                10,
                null,
                null,
                null,
                false,
                BattleOutcome.DEFENDER_WON,
                0,
                null,
                null);
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

    private static Intel spearmenIntel(Instant at, long ownerId, int spearmen) {
        return new Intel(
                at,
                ownerId,
                Map.of(UnitType.SPEARMAN, spearmen),
                0,
                null,
                null,
                null,
                false,
                BattleOutcome.DEFENDER_WON,
                0,
                null,
                null);
    }

    /** Fresh intel of a barbarian village that showed nobody at home. */
    private static Intel barbarianIntel(Instant at) {
        return new Intel(
                at,
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

    /** The spearmen of each of the lord's attacks on the village. */
    private List<Integer> farmRaidsTo(long lord, long villageId) {
        return jdbc.sql(
                        """
                        select u_spearman from movement
                        where owner_player_id = ? and target_village_id = ? and kind = 'ATTACK'
                        """)
                .param(lord)
                .param(villageId)
                .query(Integer.class)
                .list();
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

    /** An ATTACK report of the player with {@code each} wood, clay and iron as loot. */
    private void lootReport(long playerId, Instant at, int each) {
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at)
                        values (?, 'ATTACK', ?, ?::jsonb, ?)
                        """)
                .params(
                        playerId,
                        Timestamp.from(at),
                        "{\"loot\":{\"wood\":%1$d,\"clay\":%1$d,\"iron\":%1$d}}".formatted(each),
                        Timestamp.from(at))
                .update();
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

    // ---- helpers: lord guilds ----

    private void lordGuildsOn() {
        jdbc.sql("update world_settings set guilds_enabled = true, npc_guilds_enabled = true")
                .update();
        worldService.reload();
    }

    /** The lord's points and those of each of its villages. */
    private void lordPoints(long lord, int points) {
        jdbc.sql("update player set points = ? where id = ?").param(points).param(lord).update();
        jdbc.sql("update village set points = ? where player_id = ?")
                .param(points)
                .param(lord)
                .update();
    }

    private void unprotect(long playerId) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .param(Timestamp.from(clock.instant().minusSeconds(3600)))
                .param(playerId)
                .update();
    }

    /** A noble attack of the lord on the way to {@code villageId} (only the row). */
    private long nobleAttack(long lord, long villageId) {
        long from = villageOf(lord);
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.sql(
                        """
                        insert into movement (kind, home_village_id, source_village_id,
                            target_village_id, owner_player_id, u_spearman, u_swordsman,
                            u_axeman, u_scout, u_light_cavalry, u_heavy_cavalry, u_ram,
                            u_catapult, u_noble, depart_at, arrive_at, created_at)
                        values ('ATTACK', ?, ?, ?, ?, 0, 0, 10, 0, 0, 0, 0, 0, 1, ?, ?, ?)
                        returning id
                        """)
                .param(from)
                .param(from)
                .param(villageId)
                .param(lord)
                .param(now)
                .param(Timestamp.from(clock.instant().plus(Duration.ofHours(5))))
                .param(now)
                .query(Long.class)
                .single();
    }

    private Long guildOf(long playerId) {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private String roleOf(long playerId) {
        return jdbc.sql("select role from guild_member where player_id = ?")
                .param(playerId)
                .query(String.class)
                .single();
    }
}
