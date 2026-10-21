package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
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
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.report.SupportReport;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * NPC lords end to end (M12b, 09-npc-lords §2, §3, §5, §8): the spawn at world start, the decision
 * round with its transactions, the economy over simulated days, the build quiet hours, the
 * production factor and the rubber band. The test world has no NPC lords ({@code
 * TestWorldConfiguration}); these tests create theirs and delete them (and what belongs to them)
 * afterwards, because the database is shared.
 */
@IntegrationTest
class NpcLordFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired NpcLordService lordService;
    @Autowired NpcBrainRepository brains;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired UnitStackRepository stacks;
    @Autowired UnitService unitService;
    @Autowired ReportService reportService;
    @Autowired GameData gameData;
    @Autowired NpcTemplates templates;
    @Autowired NpcEconomy economy;
    @Autowired VillageLocker locker;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        // The clock is shared and only moves forward; start at noon Berlin, outside every quiet
        // window.
        clock.set(nextBerlin(LocalTime.NOON));
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        deleteAllLords();
        jdbc.sql(
                        "update world_settings set npc_lord_count = 0, npc_difficulty = 'NORMAL',"
                                + " npc_custom = null, speed = 1.0, barbarian_initial_count = 30,"
                                + " barbarians_per_new_player = 0")
                .update();
        worldService.reload();
    }

    // ---- spawn at world start (09 §2) ----

    @Test
    void theFirstStartSpawnsTheLordsOnceWithTheirBrainsAndFirstRounds() {
        spawnAtWorldStart(4);

        List<Player> lords = lords();
        assertThat(lords).hasSize(4);
        assertThat(lords).allSatisfy(lord -> assertThat(lord.isNpc()).isTrue());
        assertThat(lords).extracting(Player::getAccountId).containsOnlyNulls();
        // personalities: BALANCED, OFFENSIVE, DEFENSIVE, BALANCED
        assertThat(
                        lords.stream()
                                .map(
                                        l ->
                                                brains.findById(l.getId())
                                                        .orElseThrow()
                                                        .getPersonality()))
                .containsExactly(
                        NpcPersonality.BALANCED,
                        NpcPersonality.OFFENSIVE,
                        NpcPersonality.DEFENSIVE,
                        NpcPersonality.BALANCED);
        // names: from the list, unique, every one has a space (09 §2)
        assertThat(lords).extracting(Player::getName).doesNotHaveDuplicates();
        assertThat(lords)
                .allSatisfy(
                        lord -> {
                            assertThat(lord.getName()).contains(" ");
                            assertThat(NpcLordService.NAMES).contains(lord.getName());
                            // no beginner protection, the difficulty's production factor (NORMAL)
                            assertThat(lord.getProtectedUntil()).isEqualTo(lord.getCreatedAt());
                            assertThat(lord.getProductionFactor()).isEqualTo(0.9);
                            assertThat(lord.getVillageCount()).isEqualTo(1);
                            Village village =
                                    villages.findById(
                                                    villages.findIdsByPlayerId(lord.getId())
                                                            .getFirst())
                                            .orElseThrow();
                            assertThat(village.getBuildings().get(BuildingType.WOODCUTTER))
                                    .isEqualTo(1);
                            assertThat(village.getWood()).isEqualTo(500);
                        });
        // every lord has exactly one pending round, within one interval from now
        assertThat(pendingRounds()).hasSize(4);
        pendingRounds()
                .forEach(
                        due ->
                                assertThat(due)
                                        .isBetween(
                                                clock.instant(),
                                                clock.instant().plusSeconds(1200)));

        // a restart: no new lords, no second round
        clock.advance(Duration.ofMinutes(1));
        initializer.initialize();
        assertThat(lords()).hasSize(4);
        assertThat(pendingRounds()).hasSize(4);
    }

    @Test
    void aHumanSeesTheLordsVillageOnTheMapAsAnNpcLordWithoutThePersonality() throws Exception {
        spawnAtWorldStart(1);
        Player lord = lords().getFirst();
        Village village =
                villages.findById(villages.findIdsByPlayerId(lord.getId()).getFirst())
                        .orElseThrow();
        TestPlayer human = registerHuman();

        human.browser
                .get("/api/map?x=%d&y=%d&w=1&h=1".formatted(village.getX(), village.getY()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].playerId").value(lord.getId()))
                .andExpect(jsonPath("$[0].playerName").value(lord.getName()))
                .andExpect(jsonPath("$[0].npc").value(true))
                .andExpect(jsonPath("$[0].personality").doesNotExist());
    }

    @Test
    void barbarianVillagesAreSpawnedAroundEachLordLikeAroundAHuman() {
        jdbc.sql("update world_settings set barbarians_per_new_player = 3").update();
        long barbariansBefore = villages.findBarbarianIds().size();

        spawnAtWorldStart(2);

        assertThat(villages.findBarbarianIds().size() - barbariansBefore).isEqualTo(6);
        deleteBarbariansAfter(barbariansBefore);
    }

    @Test
    void aStartWithoutRoundsGetsThemBack() {
        spawnAtWorldStart(2);
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();

        transaction.executeWithoutResult(status -> lordService.ensureRounds());

        assertThat(pendingRounds()).hasSize(2);
        transaction.executeWithoutResult(status -> lordService.ensureRounds());
        assertThat(pendingRounds()).hasSize(2);
    }

    @Test
    void theNamesGoRoundWithRomanNumeralsWhenTheListIsUsedUp() {
        spawnAtWorldStart(NpcLordService.NAMES.size() + 2);

        List<String> names = lords().stream().map(Player::getName).toList();
        assertThat(names).doesNotHaveDuplicates();
        assertThat(names.stream().filter(n -> n.endsWith(" II"))).hasSize(2);
        assertThat(names).allSatisfy(n -> assertThat(n.length()).isLessThanOrEqualTo(24));
    }

    @Test
    void aWorldWithoutRoomSkipsTheLordsWithoutFailing() {
        jdbc.sql("update world_settings set map_size = 4").update();
        worldService.reload();
        try {
            spawnAtWorldStart(3);

            // the occupied map decides how many fit; the start doesn't fail
            assertThat(lords().size()).isLessThan(3);
            assertThat(
                            jdbc.sql("select count(*) from player where npc and village_count = 0")
                                    .query(Integer.class)
                                    .single())
                    .isZero();
        } finally {
            jdbc.sql("update world_settings set map_size = ?")
                    .param(TestWorldConfiguration.MAP_SIZE)
                    .update();
            worldService.reload();
        }
    }

    // ---- the round (09 §3) ----

    @Test
    void aRoundSchedulesTheNextOneWithJitterAndRecordsThinkTime() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        Instant start = clock.instant();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        List<Instant> next = pendingRounds();
        assertThat(next).hasSize(1);
        // 20 min ± 25 % after the moment the round ran
        assertThat(next.getFirst())
                .isBetween(clock.instant().plusSeconds(900), clock.instant().plusSeconds(1500));
        assertThat(brains.findById(lordId).orElseThrow().getLastThinkAt())
                .isEqualTo(clock.instant());
        assertThat(clock.instant()).isAfter(start);
    }

    @Test
    void theIntervalShrinksWithTheWorldSpeed() {
        jdbc.sql("update world_settings set speed = 3.0").update();
        worldService.reload();
        spawnAtWorldStart(1);
        clock.advance(Duration.ofMinutes(7));

        processor.processDue(clock.instant());

        // 20 min ÷ 3 = 400 s, ± 25 %
        assertThat(pendingRounds().getFirst())
                .isBetween(clock.instant().plusSeconds(300), clock.instant().plusSeconds(500));
    }

    @Test
    void aLordWithoutVillagesIsOutOfTheGameAndGetsNoNextRound() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        jdbc.sql("update player set village_count = 0 where id = ?").param(lordId).update();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        assertThat(pendingRounds()).isEmpty();
        assertThat(players.findById(lordId)).isPresent();
        assertThat(brains.findById(lordId)).isPresent();
    }

    @Test
    void aRoundForAMissingLordIsLoggedAndDropped() {
        schedule(999999, clock.instant());

        processor.processDue(clock.instant());

        assertThat(
                        jdbc.sql("select status from game_event where ref_id = 999999")
                                .query(String.class)
                                .single())
                .isEqualTo("DONE");
        assertThat(pendingRounds()).isEmpty();
    }

    @Test
    void aFailingVillageDoesNotStopTheRoundOrTheNextVillage() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long first = villages.findIdsByPlayerId(lordId).getFirst();
        long second = addVillage(lordId);
        // the first village has no home stack any more: its turn fails and is rolled back
        jdbc.sql("delete from unit_stack where home_village_id = ?").param(first).update();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        assertThat(buildOrders(first)).isZero();
        assertThat(buildOrders(second)).isGreaterThan(0);
        // the finish step ran and the next round is scheduled
        assertThat(brains.findById(lordId).orElseThrow().getLastThinkAt())
                .isEqualTo(clock.instant());
        assertThat(pendingRounds()).hasSize(1);
        // the event itself isn't failed
        assertThat(
                        jdbc.sql("select count(*) from game_event where status = 'FAILED'")
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    void aFailingReadOrFinishStepDoesNotFailTheEventOrLoseTheNextRound() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        // without its brain every step of the round throws (read, village, finish)
        jdbc.sql("delete from npc_brain where player_id = ?").param(lordId).update();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        // the event is done, not retried: the next round is a new one, after now
        assertThat(
                        jdbc.sql(
                                        "select count(*) from game_event where kind = 'NPC_THINK'"
                                                + " and (status = 'FAILED' or attempts > 0)")
                                .query(Integer.class)
                                .single())
                .isZero();
        assertThat(pendingRounds())
                .singleElement()
                .satisfies(due -> assertThat(due).isAfter(clock.instant()));
    }

    @Test
    void aVillageConqueredMeanwhileIsSkipped() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long village = villages.findIdsByPlayerId(lordId).getFirst();
        // the village belongs to a human now (as after a conquest), the lord still counts one
        TestPlayer human = registerHuman();
        jdbc.sql("update village set player_id = ? where id = ?")
                .param(human.playerId)
                .param(village)
                .update();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        assertThat(buildOrders(village)).isZero();
    }

    @Test
    void theRoundMarksTheLordsReportsReadAndRemembersTheNewest() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        var ref = new VillageRefDto(1, "A", 1, 1, 7L, "A");
        transaction.executeWithoutResult(
                status ->
                        reportService.create(
                                ReportKind.SUPPORT_ARRIVED,
                                clock.instant(),
                                new SupportReport(ref, ref, Map.of(UnitType.SPEARMAN, 1)),
                                lordId));
        assertThat(reportService.unreadCount(lordId)).isEqualTo(1);
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        assertThat(reportService.unreadCount(lordId)).isZero();
        assertThat(brains.findById(lordId).orElseThrow().getMemory().lastReportId()).isPositive();
    }

    // ---- the economy over days (09 §5) ----

    /**
     * Six days at speed 3 in one run (each simulated day costs about a thousand event rounds, so
     * the build and the army checks share it).
     */
    @Test
    void aLordBuildsAndRecruitsWithinTheRulesAndFollowsItsTemplateOverSimulatedDays() {
        jdbc.sql("update world_settings set speed = 3.0").update();
        worldService.reload();
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        int queueMax = worldService.settings().buildQueueMax();

        for (int i = 0; i < 6 * 24 * 12; i++) { // 6 days in 5-minute steps
            clock.advance(Duration.ofMinutes(5));
            processor.processDue(clock.instant());
            // never more than the queue holds, never a resource it didn't have
            assertThat(buildOrders(villageId)).isLessThanOrEqualTo(queueMax);
            assertThat(
                            jdbc.sql("select least(wood, clay, iron) from village where id = ?")
                                    .param(villageId)
                                    .query(Double.class)
                                    .single())
                    .isGreaterThanOrEqualTo(0);
        }

        Village village = villages.findById(villageId).orElseThrow();
        Map<BuildingType, Integer> levels = village.getBuildings().toMap();
        // it walked its template: the opening is done and it went on
        assertThat(levels.get(BuildingType.HEADQUARTERS)).isGreaterThanOrEqualTo(5);
        assertThat(levels.get(BuildingType.BARRACKS)).isGreaterThanOrEqualTo(1);
        assertThat(levels.get(BuildingType.WOODCUTTER)).isGreaterThanOrEqualTo(6);
        // the farm and the warehouse rules kicked in
        assertThat(levels.get(BuildingType.FARM)).isGreaterThan(1);
        assertThat(levels.get(BuildingType.WAREHOUSE)).isGreaterThan(1);
        assertThat(village.getPoints()).isPositive();
        assertThat(players.findById(lordId).orElseThrow().getPoints())
                .isEqualTo(village.getPoints());

        // the army and the ledger follow the template
        NpcMemory memory = brains.findById(lordId).orElseThrow().getMemory();
        NpcMemory.Ledger ledger = memory.ledgerOf(villageId);
        NpcTemplates.Template template =
                templates.of(brains.findById(lordId).orElseThrow().getPersonality());
        double share = template.stageFor(village.getPoints()).troopShare();
        double actual = ledger.troops() / (ledger.build() + ledger.troops());
        UnitCounts all =
                transaction.execute(status -> unitService.unitsOf(villageId, List.of()).all());

        assertThat(ledger.build()).isPositive();
        assertThat(actual).isCloseTo(share, within(0.12));
        assertThat(all.get(UnitType.SPEARMAN)).isPositive();
        assertThat(all.get(UnitType.AXEMAN)).isPositive();
        assertThat(all.get(UnitType.NOBLE)).isZero(); // nobles only with M12c's "wants nobles"
    }

    // ---- the paladin (09 §5.3, balancing round 2) ----

    @Test
    void fromTheSecondStageALordRecruitsOnePaladinForItsSlot() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();

        midStageRound(lordId, villageId, 1000);
        assertThat(recruitOrders(villageId, UnitType.PALADIN)).isEqualTo(1);

        // his one slot is taken: the next round orders no second paladin
        clock.advance(Duration.ofMinutes(30));
        midStageRound(lordId, villageId, 1000);
        assertThat(recruitOrders(villageId, UnitType.PALADIN)).isEqualTo(1);
    }

    @Test
    void inTheFirstStageALordRecruitsNoPaladin() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();

        midStageRound(lordId, villageId, 800);

        assertThat(recruitOrders(villageId, UnitType.PALADIN)).isZero();
        assertThat(recruitOrders(villageId, null)).isPositive(); // the template's units it does
    }

    // ---- archers (1.6, 09 §5.3) ----

    @Test
    void aDefensiveLordRecruitsArchersOnlyInAWorldWithThem() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        jdbc.sql(
                        "update npc_brain set personality = 'DEFENSIVE', template = 'DEFENSIVE'"
                                + " where player_id = ?")
                .param(lordId)
                .update();
        // the archer needs Smithy 5 next to the Barracks 5 of the round
        jdbc.sql("update village set b_smithy = 5 where id = ?").param(villageId).update();

        // without archers the mix is the one before 1.6: spearmen and swordsmen
        midStageRound(lordId, villageId, 1000);
        assertThat(recruitOrders(villageId, UnitType.ARCHER)).isZero();
        assertThat(recruitOrders(villageId, UnitType.SPEARMAN)).isPositive();

        setArchers(true);
        try {
            jdbc.sql("delete from recruit_order where village_id = ?").param(villageId).update();
            clock.advance(Duration.ofMinutes(30));
            midStageRound(lordId, villageId, 1000);
            assertThat(recruitOrders(villageId, UnitType.ARCHER)).isEqualTo(1);
        } finally {
            setArchers(false);
        }
    }

    private void setArchers(boolean enabled) {
        jdbc.sql("update world_settings set archers_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    /**
     * One round of a rich lord with Barracks 5 and a Banner Hall (where the paladin trains since
     * 1.4), the given village points and a ledger far behind on troops, so the round recruits.
     */
    private void midStageRound(long lordId, long villageId, int points) {
        makeRichAndEmptyTheQueue(villageId);
        jdbc.sql(
                        "update village set b_headquarters = greatest(b_headquarters, 8),"
                                + " b_barracks = 5, b_banner_hall = 1, points = ? where id = ?")
                .params(points, villageId)
                .update();
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lordId).orElseThrow();
                    NpcMemory.Draft memory = brain.getMemory().draft();
                    memory.ledger.put(villageId, new NpcMemory.Ledger(200_000, 0));
                    brain.setMemory(memory.build());
                    brains.save(brain);
                });
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
    }

    /** Recruit orders of one unit in a village; all units for null. */
    private int recruitOrders(long villageId, UnitType unit) {
        return jdbc.sql(
                        "select count(*) from recruit_order where village_id = ?"
                                + " and (cast(? as varchar) is null or unit = ?)")
                .params(
                        villageId,
                        unit == null ? null : unit.name(),
                        unit == null ? null : unit.name())
                .query(Integer.class)
                .single();
    }

    // ---- the rising noble price (02-game-rules §5.7) ----

    @Test
    void aLordWithManyVillagesPaysTheRisingNoblePrice() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        for (int i = 0; i < 5; i++) {
            addVillage(lordId);
        }
        jdbc.sql("delete from build_order where village_id = ?").param(villageId).update();
        jdbc.sql(
                        "update village set b_headquarters = 20, b_smithy = 20, b_market = 10,"
                                + " b_academy = 1, b_warehouse = 30, b_farm = 30, wood = 300000,"
                                + " clay = 300000, iron = 300000, resources_updated_at = ?"
                                + " where id = ?")
                .params(java.sql.Timestamp.from(clock.instant()), villageId)
                .update();

        transaction.executeWithoutResult(
                status -> {
                    Village village = locker.lock(villageId).getFirst();
                    economy.run(
                            village,
                            lordId,
                            clock.instant(),
                            templates.of(NpcPersonality.BALANCED),
                            NpcMemory.Ledger.ZERO,
                            true,
                            0,
                            NpcLearning.Lesson.NONE,
                            null);
                });

        // six villages and no noble: rank 2, one and a half times the price
        assertThat(
                        jdbc.sql(
                                        "select unit_cost_clay from recruit_order where village_id"
                                                + " = ? and unit = 'NOBLE'")
                                .param(villageId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(75_000);
    }

    // ---- build quiet hours (09 §5.1) ----

    @Test
    void duringTheBuildQuietHoursNothingNewIsQueuedAndAfterwardsItIs() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        // NORMAL: 23:00–07:00 Berlin; go to 01:00 Berlin
        clock.set(nextBerlin(LocalTime.of(1, 0)));
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());

        processor.processDue(clock.instant());

        assertThat(buildOrders(villageId)).isZero();

        // 07:00 Berlin: the window is over, the lord builds again in its next round
        clock.set(nextBerlin(LocalTime.of(7, 0)));
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());

        assertThat(buildOrders(villageId)).isGreaterThan(0);
    }

    @Test
    void ordersAlreadyQueuedKeepRunningThroughTheQuietHours() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        // a round just before 23:00 Berlin queues orders
        clock.set(nextBerlin(LocalTime.of(22, 50)));
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
        int queued = buildOrders(villageId);
        assertThat(queued).isGreaterThan(0);
        int levelsBefore = totalLevels(villageId);

        // the orders complete during the night; the lord's rounds queue nothing new
        for (int i = 0; i < 12; i++) {
            clock.advance(Duration.ofMinutes(20));
            processor.processDue(clock.instant());
        }

        assertThat(clock.instant().atZone(BERLIN).toLocalTime()).isBefore(LocalTime.of(7, 0));
        assertThat(totalLevels(villageId)).isGreaterThan(levelsBefore);
        assertThat(buildOrders(villageId)).isLessThan(queued);
    }

    @Test
    void onlyTheLastRoundBeforeTheQuietHoursFillsThePaidQueueSlots() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        int queueSize = worldService.settings().buildQueueSize();

        // a rich village: the stock and the farm would pay far more than five orders
        makeRichAndEmptyTheQueue(villageId);
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant()); // noon: an ordinary round
        processor.processDue(clock.instant());
        assertThat(buildOrders(villageId)).isEqualTo(queueSize);

        // 22:50 Berlin: the next round (20 minutes ± jitter) may come after 23:00, NORMAL's start
        clock.set(nextBerlin(LocalTime.of(22, 50)));
        makeRichAndEmptyTheQueue(villageId);
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
        assertThat(buildOrders(villageId)).isGreaterThan(queueSize);
    }

    // ---- the market exchange (v1.3, 09 §5.4) ----

    @Test
    void aLordShortOfOneResourceBuysItFromTheMarketToBuild() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        // plenty of wood and iron, no clay, a market: today it may get 1,000 clay
        makeRichAndEmptyTheQueue(villageId);
        jdbc.sql("update village set b_market = 1, clay = 0 where id = ?")
                .param(villageId)
                .update();
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());

        processor.processDue(clock.instant());

        assertThat(buildOrders(villageId)).isPositive();
        List<Map<String, Object>> trades =
                jdbc.sql(
                                "select give_resource, receive_resource, receive_amount from"
                                        + " market_exchange where village_id = ?")
                        .param(villageId)
                        .query()
                        .listOfRows();
        assertThat(trades)
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).containsEntry("receive_resource", "CLAY"));
        assertThat(trades.stream().mapToInt(t -> (Integer) t.get("receive_amount")).sum())
                .isBetween(1, 1000);

        // without a market it saves instead
        long before = trades.size();
        jdbc.sql("delete from build_order where village_id = ?").param(villageId).update();
        jdbc.sql("update village set b_market = 0, clay = 0 where id = ?")
                .param(villageId)
                .update();
        clock.advance(Duration.ofMinutes(30));
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
        assertThat(
                        jdbc.sql("select count(*) from market_exchange where village_id = ?")
                                .param(villageId)
                                .query(Long.class)
                                .single())
                .isEqualTo(before);
    }

    /** Warehouse and farm 20, 40,000 of each resource, no build orders. */
    private void makeRichAndEmptyTheQueue(long villageId) {
        jdbc.sql("delete from build_order where village_id = ?").param(villageId).update();
        jdbc.sql(
                        "update village set b_warehouse = 20, b_farm = 20, wood = 40000, clay ="
                                + " 40000, iron = 40000 where id = ?")
                .param(villageId)
                .update();
    }

    @Test
    void customReadsItsOwnQuietHoursAndIsNormalUntilSet() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        var normal = TestNpcData.balance().balance().difficulties().get(NpcDifficulty.NORMAL);
        var noQuiet =
                new NpcBalanceSettings.Difficulty(
                        normal.productionFactor(),
                        0,
                        1,
                        99,
                        4,
                        8,
                        true,
                        true,
                        "00:00",
                        "00:00",
                        0,
                        false);
        jdbc.sql("update world_settings set npc_difficulty = 'CUSTOM'").update();
        worldService.reload();
        clock.set(nextBerlin(LocalTime.of(1, 0)));
        jdbc.sql("delete from game_event").update();

        // CUSTOM without values = NORMAL: 01:00 is quiet
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
        assertThat(buildOrders(villageId)).isZero();

        // CUSTOM with its own window (from = to: none): the lord builds at 01:00
        jdbc.sql("update world_settings set npc_custom = cast(? as jsonb)")
                .param(new NpcDifficultyConverter().convertToDatabaseColumn(noQuiet))
                .update();
        worldService.reload();
        clock.advance(Duration.ofMinutes(1));
        jdbc.sql("delete from game_event").update();
        schedule(lordId, clock.instant());
        processor.processDue(clock.instant());
        assertThat(buildOrders(villageId)).isGreaterThan(0);
    }

    @Test
    void invalidCustomValuesCountAsNormal() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        // edited by hand: no build quiet times, a production factor of 0
        jdbc.sql(
                        "update world_settings set npc_difficulty = 'CUSTOM',"
                                + " npc_custom = cast('{\"productionFactor\": 0}' as jsonb)")
                .update();
        worldService.reload();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        // NORMAL: noon is no quiet time, the factor is NORMAL's (boosted if a human is far ahead)
        assertThat(buildOrders(villageId)).isGreaterThan(0);
        assertThat(players.findById(lordId).orElseThrow().getProductionFactor())
                .isIn(0.9, 0.9 * 1.25);
    }

    // ---- production factor and rubber band (09 §8) ----

    @Test
    void theProductionFactorFollowsTheDifficultyAndOnlyAppliesToTheLord() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        TestPlayer human = registerHuman();
        assertThat(players.findById(human.playerId).orElseThrow().getProductionFactor())
                .isEqualTo(1.0);

        jdbc.sql("update world_settings set npc_difficulty = 'HARD'").update();
        worldService.reload();
        clock.advance(Duration.ofMinutes(21));
        processor.processDue(clock.instant());

        // the humans' points are what they are (a fresh village: more than the lord's, no brake);
        // HARD = 1.1, the human stays at 1.0
        Player lord = players.findById(lordId).orElseThrow();
        assertThat(lord.getProductionFactor()).isIn(1.1, 1.1 * 1.5);
        assertThat(players.findById(human.playerId).orElseThrow().getProductionFactor())
                .isEqualTo(1.0);
    }

    @Test
    void aLordFarBehindAHumanProducesMoreAndOneFarAheadIsBraked() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        TestPlayer human = registerHuman();
        // the human is far ahead: the lord is below 0.5 × the average (NORMAL) → boosted
        jdbc.sql("update player set points = 1 where not npc")
                .update(); // the shared database has other humans
        jdbc.sql("update player set points = 100000 where id = ?").param(human.playerId).update();
        clock.advance(Duration.ofMinutes(21));

        processor.processDue(clock.instant());

        NpcMemory memory = brains.findById(lordId).orElseThrow().getMemory();
        assertThat(memory.stance().boosted()).isTrue();
        assertThat(memory.stance().braked()).isFalse();
        assertThat(players.findById(lordId).orElseThrow().getProductionFactor())
                .isCloseTo(0.9 * 1.25, within(1e-9));

        // the human falls far behind the lord: braked, no boost
        jdbc.sql("update player set points = 1 where not npc").update();
        clock.advance(Duration.ofMinutes(30));
        processor.processDue(clock.instant());

        memory = brains.findById(lordId).orElseThrow().getMemory();
        assertThat(memory.stance().braked()).isTrue();
        assertThat(memory.stance().boosted()).isFalse();
        assertThat(players.findById(lordId).orElseThrow().getProductionFactor()).isEqualTo(0.9);
    }

    @Test
    void theLedgerIsForgottenWhenAVillageIsLost() {
        spawnAtWorldStart(1);
        long lordId = lords().getFirst().getId();
        long villageId = villages.findIdsByPlayerId(lordId).getFirst();
        long second = addVillage(lordId);
        clock.advance(Duration.ofMinutes(21));
        processor.processDue(clock.instant());
        assertThat(brains.findById(lordId).orElseThrow().getMemory().ledger())
                .containsKeys(villageId, second);

        jdbc.sql("update village set player_id = null where id = ?").param(second).update();
        clock.advance(Duration.ofMinutes(30));
        processor.processDue(clock.instant());

        assertThat(brains.findById(lordId).orElseThrow().getMemory().ledger())
                .containsKey(villageId)
                .doesNotContainKey(second);
    }

    // ---- helpers ----

    /** Claims the world's initialization again and runs it with {@code count} lords. */
    private void spawnAtWorldStart(int count) {
        jdbc.sql(
                        "update world_settings set initialized_at = null, barbarian_initial_count ="
                                + " 0, npc_lord_count = ?")
                .param(count)
                .update();
        worldService.reload();
        initializer.initialize();
        // The start also schedules the victory check. These tests are about the lords: over
        // simulated days the check would read the standings of every village in the shared
        // database each hour, and on a nearly empty database a lord holding its few villages for
        // 48 hours would end the test world for every test after it.
        jdbc.sql("delete from game_event where kind in ('VICTORY_CHECK', 'WORLD_END')").update();
    }

    private List<Player> lords() {
        return players.findAll().stream()
                .filter(Player::isNpc)
                .sorted(java.util.Comparator.comparing(Player::getId))
                .toList();
    }

    private List<Instant> pendingRounds() {
        return jdbc.sql(
                        "select due_at from game_event where kind = 'NPC_THINK' and status ="
                                + " 'PENDING'")
                .query((rs, n) -> rs.getTimestamp(1).toInstant())
                .list();
    }

    private void schedule(long lordId, Instant due) {
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lordId, due));
    }

    private int buildOrders(long villageId) {
        return jdbc.sql("select count(*) from build_order where village_id = ?")
                .param(villageId)
                .query(Integer.class)
                .single();
    }

    private int totalLevels(long villageId) {
        return villages.findById(villageId).orElseThrow().getBuildings().toMap().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    /** The next moment (not before now) at which the clock in Berlin shows {@code time}. */
    private Instant nextBerlin(LocalTime time) {
        LocalDateTime candidate = clock.instant().atZone(BERLIN).toLocalDate().atTime(time);
        Instant at = candidate.atZone(BERLIN).toInstant();
        return at.isBefore(clock.instant()) ? candidate.plusDays(1).atZone(BERLIN).toInstant() : at;
    }

    private TestPlayer registerHuman() {
        try {
            return TestPlayer.register(mockMvc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Another village for the lord on a free field, with the start levels and a home stack. */
    private long addVillage(long lordId) {
        return transaction.execute(
                status -> {
                    Set<Coordinates> occupied = new HashSet<>(villages.findAllCoordinates());
                    Coordinates spot = null;
                    for (int x = 20; spot == null; x += 3) {
                        if (!occupied.contains(new Coordinates(x, 20))) {
                            spot = new Coordinates(x, 20);
                        }
                    }
                    var levels = new BuildingLevels();
                    for (BuildingType type : BuildingType.values()) {
                        levels.set(type, gameData.building(type).startLevel());
                    }
                    Player lord = players.findById(lordId).orElseThrow();
                    int points = BuildRules.villagePoints(gameData, levels.toMap());
                    Village village =
                            villages.save(
                                    new Village(
                                            lord,
                                            "Second",
                                            spot,
                                            500,
                                            levels,
                                            points,
                                            clock.instant()));
                    stacks.save(UnitStack.homeOf(village));
                    lord.addVillage(points);
                    return village.getId();
                });
    }

    private void deleteBarbariansAfter(long countBefore) {
        List<Long> ids = villages.findBarbarianIds();
        // the ones spawned around the lords are the highest ids
        for (long id : ids.subList((int) countBefore, ids.size())) {
            jdbc.sql("delete from unit_stack where home_village_id = ?").param(id).update();
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
    }

    /** Removes every NPC lord with its villages, stacks, orders and events. */
    private void deleteAllLords() {
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from unit_stack where home_village_id in ("
                                + lordVillages
                                + ") or location_village_id in ("
                                + lordVillages
                                + ")")
                .update();
        jdbc.sql("delete from movement where owner_player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from player where npc").update();
    }
}
