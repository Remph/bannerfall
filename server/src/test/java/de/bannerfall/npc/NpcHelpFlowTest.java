package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.victory.FrozenTime;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Help requests from allied lords in a solo world (2.1 S4, 12-solo-and-allies §9) end to end: an
 * attack the lord can't hold brings a request with its letter (in the lord's voice) and the human's
 * API; support that stands there at the landing answers it in time (+15) and the lord thanks with a
 * shipment; "Not this time" (−5); ignored until the deadline (−10); an attack that turns back calls
 * it off; a shortage that held the wall for 12 hours asks for resources, which a delivery answers;
 * the daily cap; the warning letter; nothing outside a solo world. The scene lies far off the map
 * (x ≥ 2000) like {@link NpcOrdersFlowTest}'s and is removed afterwards.
 */
@IntegrationTest
class NpcHelpFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AtomicInteger AREA = new AtomicInteger();

    /** A few defenders seen: an attack wins easily, but it is no raid target. */
    private static final Map<UnitType, Integer> FEW = Map.of(UnitType.SPEARMAN, 5);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired GuildService guildService;
    @Autowired NpcBrainRepository brains;
    @Autowired CommandService commands;
    @Autowired FrozenTime frozenTime;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NpcHelpSettings settings;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2000;
    private final List<long[]> movedHumans = new ArrayList<>();
    private final List<long[]> handedOver = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        """
                        update world_settings set barbarians_per_new_player = 0,
                            npc_difficulty = 'NORMAL', speed = 1.0, solo_mode = true,
                            guilds_enabled = true, npc_guilds_enabled = true,
                            guild_max_members = 5, court_guild_id = null,
                            night_bonus_enabled = false
                        """)
                .update();
        worldService.reload();
        guildService.setInviteInterval(Duration.ZERO);
        clock.set(
                LocalDate.now(clock).plusDays(1).atTime(LocalTime.NOON).atZone(BERLIN).toInstant());
        baseX = 2000 + 200 * AREA.getAndIncrement();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from npc_order").update();
        jdbc.sql("delete from npc_help_request").update();
        String scene = "select id from village where x >= 2000";
        jdbc.sql(
                        ("delete from trade_transport where source_village_id in (%1$s)"
                                        + " or target_village_id in (%1$s)")
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
        for (long[] village : handedOver) {
            jdbc.sql("update village set player_id = ? where id = ?")
                    .params(village[1], village[0])
                    .update();
            jdbc.sql(
                            """
                            update player set village_count = (select count(*) from village
                                where player_id = player.id)
                            where id in (?, ?)
                            """)
                    .params(village[1], village[2])
                    .update();
        }
        for (long[] moved : movedHumans) {
            jdbc.sql("update village set x = ?, y = ? where id = ?")
                    .params(moved[1], moved[2], moved[0])
                    .update();
        }
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from unit_stack where home_village_id in (%1$s) or location_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from movement where owner_player_id in (select id from player where npc)")
                .update();
        jdbc.sql(
                        "delete from trade_transport where source_village_id in (%s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from report where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from guild").update();
        jdbc.sql("delete from player where npc").update();
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        """
                        update world_settings set npc_lord_count = 0, speed = 1.0,
                            barbarian_initial_count = 30, barbarians_per_new_player = 0,
                            npc_guilds_enabled = false, guilds_enabled = false,
                            solo_mode = false, court_guild_id = null, guild_max_members = 10,
                            victory_leader = null, victory_leader_since = null,
                            night_bonus_enabled = false
                        """)
                .update();
        guildService.setInviteInterval(Duration.ofMinutes(1));
        worldService.reload();
    }

    // ---- support ----

    @Test
    void anAttackTheLordCannotHoldBringsARequestWithItsLetter() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 300, UnitType.SWORDSMAN, 100));
        Instant lands = threat(lord, enemy);

        round(lord);

        assertThat(requestState(lord)).isEqualTo("OPEN");
        assertThat(letters(human, lord)).containsExactly("HELP_SUPPORT");
        assertThat(letterField(human, lord, "voice")).isEqualTo("\"STEADY\"");
        assertThat(letterField(human, lord, "attacker")).contains(enemyName(enemy));
        int wanted = Integer.parseInt(letterField(human, lord, "count"));
        assertThat(wanted).isBetween(settings.minDefenders(), settings.maxDefenders());
        assertThat(wanted % NpcHelpRules.STEP).isZero();
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("SUPPORT"))
                .andExpect(jsonPath("$[0].lordId").value(lord))
                .andExpect(jsonPath("$[0].village.id").value(villageOf(lord)))
                .andExpect(jsonPath("$[0].defenders").value(wanted))
                .andExpect(jsonPath("$[0].deadline").value(lands.toString()))
                .andExpect(jsonPath("$[0].letter.kind").value("HELP_SUPPORT"))
                .andExpect(jsonPath("$[0].from.id").value(human.villageId))
                .andExpect(jsonPath("$[0].fill.SPEARMAN").isNumber())
                .andExpect(jsonPath("$[0].seconds").isNumber())
                .andExpect(jsonPath("$[0].answered").value(15))
                .andExpect(jsonPath("$[0].ignored").value(-10));
        // one open request per ally: the next round doesn't ask again
        round(lord);
        assertThat(requests(lord)).isEqualTo(1);
        // the enemy doesn't see it
        enemy.browser.get("/api/diplomacy/requests").andExpect(jsonPath("$").isEmpty());
        assertThat(failedEvents()).isZero();
    }

    @Test
    void theSheetSendsTheFastestDefendersThatLandInTimeFromAVillageThatHasThem() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        // the nearest village has no defenders at home (UI review 2.1, finding 01)
        TestPlayer human = human(3, 0);
        units(human.villageId, Map.of(UnitType.AXEMAN, 500));
        // the second one is farther: spearmen (18 min a field) land in time, swordsmen (22) not
        long second = secondVillage(human, 9, 0);
        units(second, Map.of(UnitType.SPEARMAN, 40, UnitType.SWORDSMAN, 1000));
        TestPlayer enemy = human(-10, 0);
        guildOf(human);
        ally(lord, human, 60);
        Instant lands = threat(lord, enemy);
        round(lord);

        assertThat(requestState(lord)).isEqualTo("OPEN");
        String body =
                human.browser
                        .get("/api/diplomacy/requests")
                        .andExpect(jsonPath("$[0].from.id").value(second))
                        .andExpect(jsonPath("$[0].fill.SPEARMAN").value(40))
                        .andExpect(jsonPath("$[0].fill.SWORDSMAN").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long seconds = Long.parseLong(body.replaceAll("(?s).*\"seconds\":(\\d+).*", "$1"));
        assertThat(clock.instant().plusSeconds(seconds)).isBefore(lands);

        // with no spearmen either, the swordsmen go: the sheet then says it is too late
        units(second, Map.of(UnitType.SWORDSMAN, 1000));
        String late =
                human.browser
                        .get("/api/diplomacy/requests")
                        .andExpect(jsonPath("$[0].from.id").value(second))
                        .andExpect(jsonPath("$[0].fill.SWORDSMAN").isNumber())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long lateSeconds = Long.parseLong(late.replaceAll("(?s).*\"seconds\":(\\d+).*", "$1"));
        assertThat(clock.instant().plusSeconds(lateSeconds)).isAfter(lands);

        // no village with defenders at home: no village and no time
        units(second, Map.of());
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(jsonPath("$[0].from").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].fill").isEmpty())
                .andExpect(jsonPath("$[0].seconds").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void anAttackItCanHoldOrAnUnseenSmallAttackerBringsNoRequest() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 60);
        threat(lord, enemy);
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));

        round(lord);
        assertThat(requests(lord)).isZero();

        // without intel the lord only fears an attacker with more points than itself
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    draft.intel.remove(enemy.villageId);
                    brain.setMemory(draft.build());
                });
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 10));
        jdbc.sql("update player set points = 1 where id = ?").param(enemy.playerId).update();
        jdbc.sql("update player set points = 500 where id = ?").param(lord).update();
        round(lord);
        assertThat(requests(lord)).isZero();
    }

    @Test
    void supportStandingThereAtTheLandingAnswersInTimeAndTheLordThanks() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 40);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        rich(villageOf(lord), 20_000);
        rich(human.villageId, 0);
        Instant lands = threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        support(human, villageOf(lord), Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());

        assertThat(requestState(lord)).isEqualTo("ANSWERED");
        assertThat(changes(lord, human)).contains("HELP_ANSWERED");
        // 40 + 10 for the support that stood there (S2b) + 15 for the request
        assertThat(trustOf(lord, human)).isEqualTo(65);
        human.browser.get("/api/diplomacy/requests").andExpect(jsonPath("$").isEmpty());

        round(lord);
        assertThat(letters(human, lord)).containsExactly("HELP_SUPPORT", "THANKS");
        assertThat(letterField(human, lord, "resources")).isNotNull();
        assertThat(shipmentsFrom(lord, human.villageId)).isEqualTo(1);
        assertThat(
                        jdbc.sql("select thanks_due from npc_help_request where lord_id = ?")
                                .param(lord)
                                .query(Boolean.class)
                                .single())
                .isFalse();
        assertThat(failedEvents()).isZero();
    }

    @Test
    void anotherBattleBeforeTheAskedAttackLeavesTheRequestOpen() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        TestPlayer raider = human(-7, 0);
        guildOf(human);
        ally(lord, human, 40);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        units(raider.villageId, Map.of(UnitType.AXEMAN, 10));
        Instant lands = threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        support(human, villageOf(lord), Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        Instant raid = attack(raider, villageOf(lord), Map.of(UnitType.AXEMAN, 5));
        assertThat(supportArrival(human)).isBefore(raid);
        assertThat(raid).isBefore(lands);

        // review finding 09: his troops fought, but not the attack the lord asked about
        clock.set(raid.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(requestState(lord)).isEqualTo("OPEN");

        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(requestState(lord)).isEqualTo("ANSWERED");
        assertThat(failedEvents()).isZero();
    }

    @Test
    void anErrorInTheHelpOrContactListenerDoesNotRollTheBattleBack() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 40);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        Instant lands = threat(lord, enemy);
        round(lord);
        support(human, villageOf(lord), Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));

        // review finding 11: both listeners fail in the database (the worst case: Postgres aborts
        // the transaction); their savepoints keep the battle
        jdbc.sql(
                        "alter table npc_help_request add constraint review_11 check (state <>"
                                + " 'ANSWERED') not valid")
                .update();
        jdbc.sql("alter table npc_contact add constraint review_11 check (lord_id < 0) not valid")
                .update();
        try {
            clock.set(lands.plusSeconds(1));
            processor.processDue(clock.instant());
        } finally {
            jdbc.sql("alter table npc_help_request drop constraint review_11").update();
            jdbc.sql("alter table npc_contact drop constraint review_11").update();
        }

        assertThat(failedEvents()).isZero();
        assertThat(
                        jdbc.sql(
                                        "select count(*) from report where player_id = ? and kind"
                                                + " = 'DEFENSE'")
                                .param(lord)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(requestState(lord)).isEqualTo("OPEN");
    }

    @Test
    void supportStandingThereWhenOnlyScoutsComeAnswersInTimeToo() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-20, 0);
        guildOf(human);
        ally(lord, human, 40);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        units(enemy.villageId, Map.of(UnitType.AXEMAN, 4000, UnitType.SCOUT, 50));
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 10));
        seen(lord, enemy, Map.of(UnitType.AXEMAN, 2000));
        // the lord can't see what comes: an attack of scouts alone fights no main battle, so the
        // supporters get no report of their own (the simulation found requests ending late)
        Instant lands = attack(enemy, villageOf(lord), Map.of(UnitType.SCOUT, 5));
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        support(human, villageOf(lord), Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000));
        Instant arrives = supportArrival(human);
        assertThat(arrives).isBefore(lands);
        clock.set(arrives.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(requestState(lord)).isEqualTo("OPEN");
        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());

        assertThat(requestState(lord)).isEqualTo("ANSWERED");
        assertThat(changes(lord, human)).contains("HELP_ANSWERED");
        assertThat(failedEvents()).isZero();
    }

    @Test
    void supportLeftThereBeforeTheRequestDoesNotAnswerIt() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 40);
        // a few of his spearmen stood there long before: the lord asks for more all the same
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman)"
                                + " values (?, ?, 20)")
                .params(human.villageId, villageOf(lord))
                .update();
        Instant lands = threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());
        round(lord);

        assertThat(requestState(lord)).isEqualTo("IGNORED");
        assertThat(changes(lord, human)).contains("HELP_IGNORED").doesNotContain("HELP_ANSWERED");
        assertThat(failedEvents()).isZero();
    }

    @Test
    void notThisTimeCostsFiveAndASecondAnswerIsRefused() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 50);
        threat(lord, enemy);
        round(lord);
        long id = requestId(lord);

        enemy.browser
                .post("/api/diplomacy/requests/" + id + "/decline", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_CLOSED"));
        human.browser
                .post("/api/diplomacy/requests/" + id + "/decline", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(requestState(lord)).isEqualTo("DECLINED");
        assertThat(trustOf(lord, human)).isEqualTo(45);
        human.browser
                .post("/api/diplomacy/requests/" + id + "/decline", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELP_REQUEST_CLOSED"));
    }

    @Test
    void ignoredUntilTheDeadlineCostsTenAndTooLateCostsNothing() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 50);
        Instant lands = threat(lord, enemy);
        round(lord);

        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());
        round(lord);

        assertThat(requestState(lord)).isEqualTo("IGNORED");
        assertThat(changes(lord, human)).contains("HELP_IGNORED");
        assertThat(trustOf(lord, human)).isEqualTo(40);

        // a second attack: the human's support is on the way but lands after it
        Instant second = threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");
        units(human.villageId, Map.of(UnitType.SWORDSMAN, 100));
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .params(baseX + 39, baseY + 40 + 39, human.villageId)
                .update();
        support(human, villageOf(lord), Map.of(UnitType.SWORDSMAN, 100));
        clock.set(second.plusSeconds(1));
        processor.processDue(clock.instant());
        round(lord);
        assertThat(requestStates(lord)).containsExactly("IGNORED", "LATE");
        assertThat(trustOf(lord, human)).isEqualTo(40);
    }

    @Test
    void aSupportThatLandsTooLateBeforeTheLordsNextRoundEndsLateNotIgnored() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 50);
        Instant lands = threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        // sent from far away: it lands after the attack, but before the lord looks again
        units(human.villageId, Map.of(UnitType.SWORDSMAN, 100));
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .params(baseX + 39, baseY + 40 + 39, human.villageId)
                .update();
        support(human, villageOf(lord), Map.of(UnitType.SWORDSMAN, 100));
        Instant arrives = supportArrival(human);
        assertThat(arrives).isAfter(lands);
        clock.set(arrives.plusSeconds(1));
        processor.processDue(clock.instant());
        round(lord);

        assertThat(requestState(lord)).isEqualTo("LATE");
        assertThat(trustOf(lord, human)).isEqualTo(50);
        assertThat(failedEvents()).isZero();
    }

    @Test
    void aRequestOutOfTheHumansReachCostsNothingIgnoredOrDeclined() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        // his spearmen would need far longer than the enemy's axemen
        TestPlayer human = human(39, 39);
        TestPlayer enemy = human(-3, 0);
        guildOf(human);
        ally(lord, human, 50);
        units(human.villageId, Map.of(UnitType.SPEARMAN, 300));
        Instant lands = threat(lord, enemy);
        round(lord);

        // the lord still writes; the sheet says a no costs nothing
        assertThat(requestState(lord)).isEqualTo("OPEN");
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(jsonPath("$[0].declined").value(0))
                .andExpect(jsonPath("$[0].ignored").value(0))
                .andExpect(jsonPath("$[0].answered").value(15));

        clock.set(lands.plusSeconds(1));
        processor.processDue(clock.instant());
        round(lord);
        assertThat(requestState(lord)).isEqualTo("IGNORED");
        assertThat(trustOf(lord, human)).isEqualTo(50);

        // a second one, declined: nothing either
        threat(lord, enemy);
        round(lord);
        human.browser
                .post("/api/diplomacy/requests/" + requestId(lord) + "/decline", "")
                .andExpect(status().isOk());
        assertThat(requestState(lord)).isEqualTo("DECLINED");
        assertThat(trustOf(lord, human)).isEqualTo(50);
    }

    @Test
    void anAttackCancelledOnItsWayCallsTheRequestOffBeforeItIsHome() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 50);
        threat(lord, enemy);
        round(lord);
        assertThat(requestState(lord)).isEqualTo("OPEN");

        // turned round a minute out: the same movement now heads home for another minute
        clock.set(clock.instant().plusSeconds(60));
        long attack =
                jdbc.sql("select id from movement where owner_player_id = ? and kind = 'ATTACK'")
                        .param(enemy.playerId)
                        .query(Long.class)
                        .single();
        enemy.browser.post("/api/movements/" + attack + "/cancel", "").andExpect(status().isOk());
        assertThat(
                        jdbc.sql("select kind from movement where id = ?")
                                .param(attack)
                                .query(String.class)
                                .single())
                .isEqualTo("RETURN");
        round(lord);

        assertThat(requestState(lord)).isEqualTo("CANCELLED");
        assertThat(trustOf(lord, human)).isEqualTo(50);
    }

    @Test
    void anAttackThatTurnsBackCallsTheRequestOff() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 50);
        threat(lord, enemy);
        round(lord);

        long attack =
                jdbc.sql("select id from movement where owner_player_id = ? and kind = 'ATTACK'")
                        .param(enemy.playerId)
                        .query(Long.class)
                        .single();
        enemy.browser.post("/api/movements/" + attack + "/cancel", "").andExpect(status().isOk());
        round(lord);

        assertThat(requestState(lord)).isEqualTo("CANCELLED");
        assertThat(trustOf(lord, human)).isEqualTo(50);
    }

    @Test
    void allAlliesTogetherAskAHumanAtMostThreeTimesADay() throws Exception {
        List<Long> lords = lords(4, NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        for (int i = 0; i < lords.size(); i++) {
            long lord = lords.get(i);
            ally(lord, human, 60);
            // the lords close to the human, so his spearmen could reach each in time
            jdbc.sql("update village set x = ?, y = ? where id = ?")
                    .params(baseX + 6 + i, baseY + 40 + 3, villageOf(lord))
                    .update();
            threat(lord, human(6 + i, 3 + 8));
        }

        for (long lord : lords) {
            round(lord);
        }

        assertThat(
                        jdbc.sql("select count(*) from npc_help_request where player_id = ?")
                                .param(human.playerId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(settings.perDay());
    }

    // ---- resources ----

    @Test
    void aShortageThatHeldTheWallForTwelveHoursAsksForResourcesAndADeliveryAnswersIt()
            throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 60);
        long village = villageOf(lord);
        builtUpToTheWall(village);
        rich(human.villageId, 20_000);

        round(lord); // the shortage starts
        assertThat(requests(lord)).isZero();
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    NpcMemory.Shortage shortage = draft.shortages.get(village);
                    assertThat(shortage).isNotNull();
                    assertThat(shortage.goal()).isEqualTo("WALL:1");
                    draft.shortages.put(
                            village,
                            new NpcMemory.Shortage(
                                    shortage.goal(),
                                    clock.instant().minus(Duration.ofHours(13)),
                                    shortage.missing()));
                    brain.setMemory(draft.build());
                });
        round(lord);

        assertThat(requestState(lord)).isEqualTo("OPEN");
        assertThat(letters(human, lord)).containsExactly("HELP_RESOURCES");
        assertThat(letterField(human, lord, "goal")).isEqualTo("\"WALL\"");
        int wood =
                jdbc.sql("select wood from npc_help_request where lord_id = ?")
                        .param(lord)
                        .query(Integer.class)
                        .single();
        int clay =
                jdbc.sql("select clay from npc_help_request where lord_id = ?")
                        .param(lord)
                        .query(Integer.class)
                        .single();
        int iron =
                jdbc.sql("select iron from npc_help_request where lord_id = ?")
                        .param(lord)
                        .query(Integer.class)
                        .single();
        assertThat(wood + clay + iron).isPositive();
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(jsonPath("$[0].kind").value("RESOURCES"))
                .andExpect(jsonPath("$[0].goal").value("WALL"))
                .andExpect(jsonPath("$[0].resources.wood").value(wood))
                .andExpect(jsonPath("$[0].seconds").isNumber());

        // the sheet sends from the nearest village with a free merchant (UI review 2.1, 01)
        long second = secondVillage(human, 15, 0);
        jdbc.sql("update village set b_market = 0 where id = ?").param(human.villageId).update();
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(jsonPath("$[0].from").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].seconds").value(org.hamcrest.Matchers.nullValue()));
        rich(second, 0);
        human.browser
                .get("/api/diplomacy/requests")
                .andExpect(jsonPath("$[0].from.id").value(second))
                .andExpect(jsonPath("$[0].seconds").isNumber());
        rich(human.villageId, 20_000);

        Village to = villages.findById(village).orElseThrow();
        human.browser
                .post(
                        "/api/villages/" + human.villageId + "/transports",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":%d,\"clay\":%d,\"iron\":%d}"
                                .formatted(to.getX(), to.getY(), wood, clay, iron))
                .andExpect(status().isCreated());
        Instant arrives =
                jdbc.sql("select max(arrive_at) from trade_transport where target_village_id = ?")
                        .param(village)
                        .query(Timestamp.class)
                        .single()
                        .toInstant();
        clock.set(arrives.plusSeconds(1));
        processor.processDue(clock.instant());

        assertThat(requestState(lord)).isEqualTo("ANSWERED");
        assertThat(changes(lord, human)).contains("HELP_ANSWERED");
    }

    // ---- letters and worlds ----

    @Test
    void anAllyWhoseTrustFallsTowardsZeroWarnsOnce() throws Exception {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 8);

        round(lord);
        round(lord);
        assertThat(letters(human, lord)).containsExactly("WARNING");
        assertThat(letterField(human, lord, "voice")).isEqualTo("\"CAREFUL\"");

        setTrust(lord, human.playerId, 40);
        round(lord);
        setTrust(lord, human.playerId, 5);
        round(lord);
        assertThat(letters(human, lord)).containsExactly("WARNING", "WARNING");
    }

    @Test
    void noRequestsOutsideASoloWorld() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(-8, 0);
        guildOf(human);
        ally(lord, human, 60);
        threat(lord, enemy);
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();

        round(lord);

        assertThat(requests(lord)).isZero();
    }

    // ---- helpers ----

    /**
     * The enemy's attack of axemen on the lord's village, which the lord saw holding 2,000 axemen
     * and has 10 spearmen against; its arrival.
     */
    private Instant threat(long lord, TestPlayer enemy) {
        units(enemy.villageId, Map.of(UnitType.AXEMAN, 4000));
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 10));
        seen(lord, enemy, Map.of(UnitType.AXEMAN, 2000));
        return attack(enemy, villageOf(lord));
    }

    private Instant attack(TestPlayer from, long villageId) {
        return attack(from, villageId, Map.of(UnitType.AXEMAN, 1000));
    }

    /** When the human's support on the way lands. */
    private Instant supportArrival(TestPlayer human) {
        return jdbc.sql(
                        "select arrive_at from movement where owner_player_id = ? and kind ="
                                + " 'SUPPORT'")
                .param(human.playerId)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private Instant attack(TestPlayer from, long villageId, Map<UnitType, Integer> units) {
        Village to = villages.findById(villageId).orElseThrow();
        return transaction.execute(
                status ->
                        commands.send(
                                        from.villageId,
                                        from.playerId,
                                        new CommandRequest(
                                                CommandKind.ATTACK,
                                                new Coordinates(to.getX(), to.getY()),
                                                units,
                                                null))
                                .arriveAt());
    }

    private void support(TestPlayer human, long villageId, Map<UnitType, Integer> units) {
        Village to = villages.findById(villageId).orElseThrow();
        transaction.executeWithoutResult(
                status ->
                        commands.send(
                                human.villageId,
                                human.playerId,
                                new CommandRequest(
                                        CommandKind.SUPPORT,
                                        new Coordinates(to.getX(), to.getY()),
                                        units,
                                        null)));
    }

    /** Every step of the BALANCED template before its wall stands; no stock. */
    private void builtUpToTheWall(long villageId) {
        jdbc.sql(
                        """
                        update village set b_woodcutter = 8, b_clay_pit = 7, b_iron_mine = 7,
                            b_headquarters = 5, b_farm = 6, b_warehouse = 6, b_barracks = 2,
                            b_smithy = 2, b_hiding_place = 2, b_market = 2, b_wall = 0,
                            wood = 0, clay = 0, iron = 0, resources_updated_at = ?
                        where id = ?
                        """)
                .params(Timestamp.from(clock.instant()), villageId)
                .update();
    }

    private String enemyName(TestPlayer enemy) {
        return players.findById(enemy.playerId).orElseThrow().getName();
    }

    private String requestState(long lord) {
        return jdbc.sql(
                        "select state from npc_help_request where lord_id = ? order by id desc"
                                + " limit 1")
                .param(lord)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private List<String> requestStates(long lord) {
        return jdbc.sql("select state from npc_help_request where lord_id = ? order by id")
                .param(lord)
                .query(String.class)
                .list();
    }

    private long requestId(long lord) {
        return jdbc.sql(
                        "select id from npc_help_request where lord_id = ? order by id desc limit"
                                + " 1")
                .param(lord)
                .query(Long.class)
                .single();
    }

    private int requests(long lord) {
        return jdbc.sql("select count(*) from npc_help_request where lord_id = ?")
                .param(lord)
                .query(Integer.class)
                .single();
    }

    private int trustOf(long lord, TestPlayer human) {
        return transaction.execute(
                status ->
                        NpcTrustRules.shown(
                                jdbc.sql(
                                                "select trust from npc_trust where lord_id = ? and"
                                                        + " player_id = ?")
                                        .params(lord, human.playerId)
                                        .query(Double.class)
                                        .single()));
    }

    private String changes(long lord, TestPlayer human) {
        return jdbc.sql("select changes::text from npc_trust where lord_id = ? and player_id = ?")
                .params(lord, human.playerId)
                .query(String.class)
                .single();
    }

    private int shipmentsFrom(long lord, long to) {
        return jdbc.sql(
                        """
                        select count(*) from trade_transport t join village s
                            on s.id = t.source_village_id
                        where s.player_id = ? and t.target_village_id = ?
                        """)
                .params(lord, to)
                .query(Integer.class)
                .single();
    }

    /** The human's attack on the target, sent now; its arrival. */
    private Instant humanAttack(TestPlayer human, TestPlayer target) {
        Village to = villages.findById(target.villageId).orElseThrow();
        return transaction.execute(
                status ->
                        commands.send(
                                        human.villageId,
                                        human.playerId,
                                        new CommandRequest(
                                                CommandKind.ATTACK,
                                                new Coordinates(to.getX(), to.getY()),
                                                Map.of(UnitType.AXEMAN, 50),
                                                null))
                                .arriveAt());
    }

    /** The lord saw the target a minute ago with these defenders, wall 0. */
    private void seen(long lord, TestPlayer target, Map<UnitType, Integer> defenders) {
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    draft.intel.put(
                            target.villageId,
                            new NpcMemory.Intel(
                                    clock.instant().minusSeconds(60),
                                    target.playerId,
                                    defenders,
                                    0,
                                    Map.of(),
                                    null,
                                    null,
                                    false,
                                    null,
                                    0,
                                    null,
                                    null));
                    brain.setMemory(draft.build());
                });
    }

    private String orderState(long lord) {
        return jdbc.sql("select state from npc_order where lord_id = ?")
                .param(lord)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private Instant departAt(long lord) {
        return jdbc.sql("select depart_at from npc_order where lord_id = ?")
                .param(lord)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private Instant pending(GameEventKind kind, long refId) {
        return jdbc.sql(
                        "select due_at from game_event where kind = ? and ref_id = ? and status ="
                                + " 'PENDING'")
                .params(kind.name(), refId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    private int failedEvents() {
        return jdbc.sql("select count(*) from game_event where status = 'FAILED'")
                .query(Integer.class)
                .single();
    }

    private List<Long> attacksFrom(long lord) {
        return jdbc.sql(
                        "select target_village_id from movement where owner_player_id = ? and kind"
                                + " = 'ATTACK' order by id")
                .param(lord)
                .query(Long.class)
                .list();
    }

    private List<Long> supportsFrom(long lord) {
        return jdbc.sql(
                        "select target_village_id from movement where owner_player_id = ? and kind"
                                + " = 'SUPPORT' order by id")
                .param(lord)
                .query(Long.class)
                .list();
    }

    private Instant arrivalOfAttack(long lord) {
        return jdbc.sql(
                        "select arrive_at from movement where owner_player_id = ? and kind ="
                                + " 'ATTACK'")
                .param(lord)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private int sentUnits(long lord, UnitType type) {
        return sent(lord, type, "ATTACK");
    }

    private int sentSupport(long lord, UnitType type) {
        return sent(lord, type, "SUPPORT");
    }

    private int sent(long lord, UnitType type, String kind) {
        return jdbc.sql(
                        "select coalesce(sum(u_%s), 0) from movement where owner_player_id = ? and"
                                        .formatted(type.name().toLowerCase(Locale.ROOT))
                                + " kind = ?")
                .params(lord, kind)
                .query(Integer.class)
                .single();
    }

    private int standing(long home, long location) {
        return jdbc.sql(
                        "select coalesce(sum(u_spearman), 0) from unit_stack where"
                                + " home_village_id = ? and location_village_id = ?")
                .params(home, location)
                .query(Integer.class)
                .single();
    }

    /** The kinds of the lord's letters to the human, oldest first. */
    private List<String> letters(TestPlayer human, long lord) {
        return jdbc.sql(
                        """
                        select letter ->> 'kind' from private_message
                        where sender_id = ? and recipient_id = ? and letter is not null
                        order by id
                        """)
                .params(lord, human.playerId)
                .query(String.class)
                .list();
    }

    /** One field of the lord's last letter to the human, as JSON text. */
    private String letterField(TestPlayer human, long lord, String field) {
        return jdbc.sql(
                        """
                        select (letter -> ?)::text from private_message
                        where sender_id = ? and recipient_id = ? and letter is not null
                        order by id desc limit 1
                        """)
                .params(field, lord, human.playerId)
                .query(String.class)
                .optional()
                .filter(json -> !"null".equals(json))
                .orElse(null);
    }

    /** Puts the lord into the human's guild with this trust in him. */
    private void ally(long lord, TestPlayer human, double trustValue) {
        setTrust(lord, human.playerId, 100);
        long invitation = guildService.inviteLord(human.playerId, lord);
        assertThat(guildService.acceptForLord(lord, invitation)).isTrue();
        setTrust(lord, human.playerId, trustValue);
        // like the lord's own yes: a fall of its trust in the inviter earns a warning
        jdbc.sql("update npc_trust set warned = false where lord_id = ? and player_id = ?")
                .params(lord, human.playerId)
                .update();
        // the join is a first contact, as in the lord's own yes
        jdbc.sql(
                        "insert into npc_contact (player_id, lord_id, since) values (?, ?, ?) on"
                                + " conflict do nothing")
                .params(human.playerId, lord, Timestamp.from(clock.instant()))
                .update();
    }

    private long guildOf(TestPlayer human) throws Exception {
        Long id = guildIdOf(human.playerId);
        if (id != null) {
            return id;
        }
        human.browser
                .post("/api/guilds", "{\"name\":\"Testers\",\"tag\":\"TST\"}")
                .andExpect(status().isCreated());
        return guildIdOf(human.playerId);
    }

    private Long guildIdOf(long playerId) {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private void setTrust(long lord, long player, double value) {
        jdbc.sql(
                        """
                        insert into npc_trust (lord_id, player_id, trust, trust_at)
                        values (?, ?, ?, ?)
                        on conflict (lord_id, player_id) do update
                            set trust = excluded.trust, trust_at = excluded.trust_at
                        """)
                .params(lord, player, value, Timestamp.from(clock.instant()))
                .update();
    }

    private void round(long lord) {
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lord, clock.instant()));
        processor.processDue(clock.instant());
    }

    private long lord(NpcPersonality personality) {
        return lords(1, personality).getFirst();
    }

    /** {@code count} lords, their villages side by side in the scene. */
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
        int i = 0;
        for (long id : ids) {
            jdbc.sql("update npc_brain set personality = ?, template = ? where player_id = ?")
                    .params(personality.name(), personality.name(), id)
                    .update();
            long village = villageOf(id);
            jdbc.sql("update village set x = ?, y = ?, name = 'Lord village' where id = ?")
                    .params(baseX + 40 * i, baseY + 40, village)
                    .update();
            i++;
        }
        jdbc.sql("delete from game_event").update();
        return ids;
    }

    private long villageOf(long playerId) {
        return villages.findIdsByPlayerId(playerId).getFirst();
    }

    /** A registered human, his village moved into the scene and out of protection. */
    private TestPlayer human(int dx, int dy) {
        TestPlayer human;
        try {
            human = TestPlayer.register(mockMvc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Village village = villages.findById(human.villageId).orElseThrow();
        movedHumans.add(new long[] {human.villageId, village.getX(), village.getY()});
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .params(baseX + dx, baseY + 40 + dy, human.villageId)
                .update();
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(3600)), human.playerId)
                .update();
        return human;
    }

    /**
     * A second village for {@code human}: another player's village moved into the scene and handed
     * over (it goes back in {@link #cleanUp}).
     */
    private long secondVillage(TestPlayer human, int dx, int dy) {
        TestPlayer other = human(dx, dy);
        jdbc.sql("update village set player_id = ?, version = version + 1 where id = ?")
                .params(human.playerId, other.villageId)
                .update();
        jdbc.sql(
                        """
                        update player set village_count = (select count(*) from village
                            where player_id = player.id), version = version + 1
                        where id in (?, ?)
                        """)
                .params(human.playerId, other.playerId)
                .update();
        handedOver.add(new long[] {other.villageId, other.playerId, human.playerId});
        return other.villageId;
    }

    /** A village with a market of 10 and {@code each} of every resource in a big warehouse. */
    private void rich(long villageId, int each) {
        jdbc.sql(
                        """
                        update village set b_market = 10, b_warehouse = 25, wood = ?, clay = ?,
                            iron = ?, resources_updated_at = ? where id = ?
                        """)
                .params(each, each, each, Timestamp.from(clock.instant()), villageId)
                .update();
    }

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
        jdbc.sql(sql.toString()).params(villageId, villageId).update();
    }
}
