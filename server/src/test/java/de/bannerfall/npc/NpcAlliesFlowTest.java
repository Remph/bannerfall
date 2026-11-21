package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.report.TradeReport;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.victory.FrozenTime;
import de.bannerfall.victory.VictoryService;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
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
 * NPC lords as allies in a solo world (2.1 S2b, 12-solo-and-allies §4 to §7) end to end: trust from
 * real battles and reports, the invitation, the lord's answer by letter in its round, leaving on
 * low trust and at once on an attack, the court and its notice, the diplomacy page. The scene lies
 * far off the map (x ≥ 2000) like {@link NpcWarfareFlowTest}'s and is removed afterwards.
 */
@IntegrationTest
class NpcAlliesFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AtomicInteger AREA = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired UnitStackRepository stacks;
    @Autowired GuildService guildService;
    @Autowired ReportService reports;
    @Autowired NpcTrust trust;
    @Autowired VictoryService victory;
    @Autowired FrozenTime frozenTime;
    @Autowired SentMessages sent;
    @Autowired NpcBrainRepository brains;
    @Autowired GameData gameData;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2000;
    private final List<long[]> movedHumans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        """
                        update world_settings set barbarians_per_new_player = 0,
                            npc_difficulty = 'NORMAL', speed = 1.0, solo_mode = true,
                            guilds_enabled = true, npc_guilds_enabled = true,
                            guild_max_members = 5, court_guild_id = null
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
                            victory_leader = null, victory_leader_since = null
                        """)
                .update();
        guildService.setInviteInterval(Duration.ofMinutes(1));
        worldService.reload();
    }

    // ---- joining (12 §5) ----

    @Test
    void anInvitedLordWithEnoughTrustJoinsInItsNextRoundAndWritesALetter() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        long guild = guildOf(human);
        setTrust(lord, human.playerId, 55);

        human.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lords[0].playerId").value(lord))
                .andExpect(jsonPath("$.lords[0].invitationId").isNumber())
                // not met yet: the bar would give the personality away (security review 2.1)
                .andExpect(jsonPath("$.lords[0].bar").value(nullValue()));
        assertThat(guildIdOf(lord)).isNull(); // it answers in its round, not at once

        round(lord);

        assertThat(guildIdOf(lord)).isEqualTo(guild);
        assertThat(openInvitations(lord)).isZero();
        human.browser
                .get("/api/messages/with/" + lord)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value(""))
                .andExpect(jsonPath("$[0].letter.kind").value("JOINED"))
                .andExpect(jsonPath("$[0].letter.guild").value("Testers"));
        human.browser
                .get("/api/messages/conversations")
                .andExpect(jsonPath("$[0].playerId").value(lord))
                .andExpect(jsonPath("$[0].npc").value(true))
                .andExpect(jsonPath("$[0].lastLetter.kind").value("JOINED"))
                .andExpect(jsonPath("$[0].unread").value(1));
        assertThat(sent.toPlayer(human.playerId))
                .anySatisfy(json -> assertThat(json).contains("MESSAGE_RECEIVED"));
        // joining counts as meeting the lord (09 §16.7)
        assertThat(contactSince(human.playerId, lord)).isNotNull();
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.allies[0].playerId").value(lord))
                .andExpect(jsonPath("$.allies[0].ally").value(true))
                .andExpect(jsonPath("$.allies[0].bar").value(nullValue()))
                .andExpect(jsonPath("$.lords").isEmpty());
    }

    @Test
    void aLordInALordGuildNeedsTenMoreAndLeavesItsGuildToJoin() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.DEFENSIVE);
        long lord = lords.get(0);
        long lordGuild = guildService.foundForLords(lord, lords.get(1), "Ordo Corvi", "CORVI");
        TestPlayer human = human(5, 0);
        long guild = guildOf(human);
        setTrust(lord, human.playerId, 35); // DEFENSIVE 30, +10 in a lord guild

        invite(human, lord);
        round(lord);
        assertThat(guildIdOf(lord)).isEqualTo(lordGuild);
        assertThat(letters(human, lord)).containsExactly("REFUSED_TRUST");

        clock.advance(Duration.ofDays(1));
        setTrust(lord, human.playerId, 40);
        invite(human, lord);
        round(lord);
        assertThat(guildIdOf(lord)).isEqualTo(guild);
        assertThat(letters(human, lord)).containsExactly("REFUSED_TRUST", "JOINED");
        // the lord guild goes on with its other member
        assertThat(guildIdOf(lords.get(1))).isEqualTo(lordGuild);
    }

    @Test
    void theBarShowsOnlyOnceTheHumanHasMetTheLord() throws Exception {
        long offensive = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        setTrust(offensive, human.playerId, 20);

        // before the first contact the bar (30/50/70, +10 in a lord guild) would name the
        // personality, which stays hidden (09 §16.7); the trust and the invitation stay
        human.browser
                .get("/api/diplomacy")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lords[0].playerId").value(offensive))
                .andExpect(jsonPath("$.lords[0].trust").value(20))
                .andExpect(jsonPath("$.lords[0].bar").value(nullValue()))
                .andExpect(jsonPath("$.lords[0].variant").value(nullValue()));
        invite(human, offensive);

        meet(human.playerId, offensive);

        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].bar").value(70))
                .andExpect(jsonPath("$.lords[0].variant.since").isString());
        // a multiplayer world with lord guilds shows no bar at all, met or not: nobody can invite
        // there (feature review finding 03)
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        TestPlayer stranger = human(7, 0);
        stranger.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].playerId").value(offensive))
                .andExpect(jsonPath("$.lords[0].bar").value(nullValue()));
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].bar").value(nullValue()))
                .andExpect(jsonPath("$.lords[0].variant.since").isString());
    }

    @Test
    void aNoMakesTheNextInvitationWaitADay() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        setTrust(lord, human.playerId, 20);

        invite(human, lord);
        round(lord);

        assertThat(guildIdOf(lord)).isNull();
        assertThat(letters(human, lord)).containsExactly("REFUSED_TRUST");
        meet(human.playerId, lord);
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].invitationId").value(nullValue()))
                .andExpect(jsonPath("$.lords[0].inviteAfter").isString())
                .andExpect(jsonPath("$.lords[0].bar").value(70));
        human.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LORD_NOT_READY"));

        clock.advance(Duration.ofHours(24).plusMinutes(1));
        human.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isOk());
    }

    @Test
    void aFullGuildGetsANoWithItsReason() throws Exception {
        jdbc.sql("update world_settings set guild_max_members = 2").update();
        worldService.reload();
        List<Long> lords = lords(2, NpcPersonality.DEFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        setTrust(lords.get(0), human.playerId, 80);
        setTrust(lords.get(1), human.playerId, 80);
        invite(human, lords.get(0));
        invite(human, lords.get(1));

        round(lords.get(0));
        round(lords.get(1));

        assertThat(letters(human, lords.get(0))).containsExactly("JOINED");
        assertThat(letters(human, lords.get(1))).containsExactly("REFUSED_FULL");
        human.browser
                .post("/api/diplomacy/lords/" + lords.get(1) + "/invite", "")
                .andExpect(status().isConflict());
    }

    @Test
    void lordsCannotBeInvitedOutsideASoloWorld() throws Exception {
        List<Long> both = lords(2, NpcPersonality.DEFENSIVE);
        long lord = both.get(0);
        // outside a solo world the points are the row's only figure: most points first
        jdbc.sql("update player set points = 900 where id = ?").param(lord).update();
        jdbc.sql("update player set points = 100 where id = ?").param(both.get(1)).update();
        TestPlayer human = human(5, 0);
        guildOf(human);
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();

        human.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_SOLO"));
        // the page itself is there in any world with lord guilds, with the lords but no trust
        // and no bar: nobody can win a lord there (review finding 03)
        meet(human.playerId, lord);
        setTrust(lord, human.playerId, 40);
        human.browser
                .get("/api/diplomacy")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloMode").value(false))
                .andExpect(jsonPath("$.lords[0].playerId").value(lord))
                .andExpect(jsonPath("$.lords[0].trust").value(nullValue()))
                .andExpect(jsonPath("$.lords[0].bar").value(nullValue()))
                .andExpect(jsonPath("$.lords[0].changes").isEmpty())
                .andExpect(jsonPath("$.lords[1].playerId").value(both.get(1)));
        jdbc.sql("update player set points = 1000 where id = ?").param(both.get(1)).update();
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].playerId").value(both.get(1)))
                .andExpect(jsonPath("$.lords[1].playerId").value(lord));
        jdbc.sql("update world_settings set npc_guilds_enabled = false").update();
        worldService.reload();
        human.browser.get("/api/diplomacy").andExpect(status().isConflict());
    }

    @Test
    void aLordCannotLeadAHumansGuild() throws Exception {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human);

        human.browser
                .post("/api/guilds/mine/leader", "{\"playerId\":" + lord + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aLeaderWithOnlyAlliesLeavesAndTheyAreLoneLordsAgain() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        long guild = guildOf(human);
        ally(lords.get(0), human);
        ally(lords.get(1), human);

        // review finding 07: no "hand the lead over" to a lord that can't take it
        human.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());

        assertThat(guildIdOf(human.playerId)).isNull();
        assertThat(guildIdOf(lords.get(0))).isNull();
        assertThat(guildIdOf(lords.get(1))).isNull();
        assertThat(
                        jdbc.sql("select count(*) from guild where id = ?")
                                .param(guild)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    void aLeaderWithAnotherHumanStillHandsTheLeadOverFirst() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer friend = human(8, 0);
        long guild = guildOf(human);
        ally(lord, human);
        joinGuild(friend, guild);

        human.browser
                .post("/api/guilds/mine/leave", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_MUST_TRANSFER"));
    }

    @Test
    void aGoneLeadersLeadGoesToTheOldestHumanNotToALord() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer friend = human(8, 0);
        long guild = guildOf(human);
        ally(lord, human); // the lord is the older member
        clock.advance(Duration.ofMinutes(1));
        joinGuild(friend, guild);

        // what the host's delete (or a ban for good) does to the guild
        guildService.removeBecauseGone(human.playerId);

        assertThat(roleOf(friend.playerId)).isEqualTo("LEADER");
        assertThat(roleOf(lord)).isEqualTo("MEMBER");
    }

    @Test
    void aGoneLeaderWithOnlyAlliesEndsTheGuild() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human);

        // what the host's delete (or a ban for good) does to the guild
        guildService.removeBecauseGone(human.playerId);

        assertThat(guildIdOf(lord)).isNull();
    }

    @Test
    void anotherHumansAllyCannotBeInvitedAndDeclinesAnInvitationLeftOver() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer other = human(8, 0);
        guildOf(human);
        ally(lord, human);
        jdbc.sql("update guild set name = 'Others', tag = 'OTH' where id = ?")
                .param(guildIdOf(human.playerId))
                .update();
        long otherGuild = guildOf(other);
        setTrust(lord, other.playerId, 100);

        // review finding 08: it would never answer
        other.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LORD_ALLIED"));

        // one from before it joined (or a race) is declined in its next round
        jdbc.sql(
                        "insert into guild_invitation (guild_id, player_id, invited_by, created_at)"
                                + " values (?, ?, ?, ?)")
                .params(otherGuild, lord, other.playerId, Timestamp.from(clock.instant()))
                .update();
        round(lord);
        assertThat(openInvitations(lord)).isZero();
        assertThat(guildIdOf(lord)).isEqualTo(guildIdOf(human.playerId));
    }

    // ---- leaving (12 §5) ----

    @Test
    void anAllyBelowZeroTrustLeavesInItsNextRoundWithALetter() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human);
        setTrust(lord, human.playerId, 5);

        round(lord);
        assertThat(guildIdOf(lord)).isNotNull();

        setTrust(lord, human.playerId, -3);
        round(lord);

        assertThat(guildIdOf(lord)).isNull();
        // at 5 it warned him first (S4)
        assertThat(letters(human, lord)).containsExactly("WARNING", "LEFT_TRUST");
    }

    @Test
    void aFriendWhoJoinsTheGuildGetsNoWarningAndHisScoutingKeepsTheAlly() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer leader = human(3, 0);
        TestPlayer friend = human(6, 0);
        long guild = guildOf(leader);
        ally(lord, leader);
        setTrust(lord, leader.playerId, 60);
        joinGuild(friend, guild);

        // review finding 02: trust 0 towards a stranger is no reason for a warning
        round(lord);
        assertThat(letters(friend, lord)).isEmpty();

        // his scouting takes him below 0, but only the leader's trust keeps the ally
        units(friend.villageId, Map.of(UnitType.SCOUT, 5));
        friend.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"SCOUT\":1}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());
        assertThat(shownTrust(lord, friend.playerId)).isEqualTo(-10);
        round(lord);
        assertThat(guildIdOf(lord)).isEqualTo(guild);
        assertThat(letters(friend, lord)).isEmpty();
        assertThat(letters(leader, lord)).isEmpty();

        // once his trust stood high, a fall towards 0 earns him a warning too
        setTrust(lord, friend.playerId, 40);
        round(lord);
        setTrust(lord, friend.playerId, 15);
        round(lord);
        assertThat(letters(friend, lord)).containsExactly("WARNING");
    }

    @Test
    void anAllyLeavesWhenItsTrustInTheLeaderFallsBelowZero() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer leader = human(3, 0);
        TestPlayer friend = human(6, 0);
        long guild = guildOf(leader);
        ally(lord, leader);
        joinGuild(friend, guild);
        setTrust(lord, friend.playerId, 80);
        setTrust(lord, leader.playerId, -2);

        round(lord);

        assertThat(guildIdOf(lord)).isNull();
        assertThat(letters(leader, lord)).containsExactly("LEFT_TRUST");
        assertThat(letters(friend, lord)).containsExactly("LEFT_TRUST");
    }

    @Test
    void anAttackOnAnAllyMakesItLeaveAtOnceAndCostsItsGuildMatesToo() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        long lord = lords.get(0);
        long mate = lords.get(1);
        TestPlayer human = human(3, 0);
        long guild = guildOf(human);
        ally(lord, human);
        ally(mate, human);
        setTrust(lord, human.playerId, 90);
        setTrust(mate, human.playerId, 50);
        units(human.villageId, Map.of(UnitType.AXEMAN, 50));
        units(villageOf(lord), Map.of());

        human.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"AXEMAN\":10}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());

        assertThat(guildIdOf(lord)).isNull();
        assertThat(guildIdOf(mate)).isEqualTo(guild);
        assertThat(letters(human, lord)).containsExactly("LEFT_ATTACKED");
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(50); // 90 − 40
        assertThat(shownTrust(mate, human.playerId)).isEqualTo(35); // 50 − 15
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.lords[0].playerId").value(lord))
                .andExpect(jsonPath("$.lords[0].changes[0].reason").value("ATTACKED"))
                .andExpect(jsonPath("$.lords[0].changes[0].delta").value(-40))
                .andExpect(jsonPath("$.allies[0].changes[0].reason").value("MATE_ATTACKED"));
    }

    @Test
    void aScoutingOfAnAllyCostsTenButKeepsItInTheGuild() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(3, 0);
        long guild = guildOf(human);
        ally(lord, human);
        setTrust(lord, human.playerId, 60);
        units(human.villageId, Map.of(UnitType.SCOUT, 5));

        human.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"SCOUT\":1}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());

        // 12 §5: only an attack ends the alliance at once; a scouting costs trust like elsewhere
        assertThat(guildIdOf(lord)).isEqualTo(guild);
        assertThat(letters(human, lord)).isEmpty();
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(50);
    }

    // ---- what moves trust (12 §5) ----

    @Test
    void aScoutingCostsTenAndAnAttackForty() throws Exception {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(3, 0);
        units(human.villageId, Map.of(UnitType.SCOUT, 5, UnitType.AXEMAN, 5));

        human.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"SCOUT\":1}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(-10);

        human.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"AXEMAN\":5}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(-50);
    }

    @Test
    void aSupportThatStandsInTheLordsVillageWhenAForeignAttackArrivesGivesTenAtMostTwentyADay()
            throws Exception {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer helper = human(3, 0);
        TestPlayer enemy = human(6, 0);
        supportStack(helper.villageId, villageOf(lord), Map.of(UnitType.SPEARMAN, 500));
        units(enemy.villageId, Map.of(UnitType.AXEMAN, 30));

        for (int i = 0; i < 3; i++) {
            enemy.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"AXEMAN\":5}")
                    .andExpect(status().isCreated());
            clock.advance(Duration.ofHours(2));
            processor.processDue(clock.instant());
        }

        assertThat(shownTrust(lord, helper.playerId)).isEqualTo(20);
        assertThat(shownTrust(lord, enemy.playerId)).isEqualTo(-100);
        // a new day, a new cap
        clock.advance(Duration.ofDays(1));
        enemy.command("ATTACK", coordinatesOf(villageOf(lord)), "{\"AXEMAN\":5}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());
        // 20, a day and a bit of drift (2 a day), + 10
        assertThat(shownTrust(lord, helper.playerId)).isBetween(27, 28);
    }

    @Test
    void shipmentsGiveOnePerThousandAtMostTenADay() {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(3, 0);

        delivered(human, lord, 2500);
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(2);
        delivered(human, lord, 600);
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(3);
        delivered(human, lord, 20_000);
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(10);
        assertThat(trust.row(lord, human.playerId).orElseThrow().changes())
                .extracting(NpcTrustRules.Change::reason)
                .containsOnly(TrustReason.SHIPPED);
    }

    @Test
    void aWinAgainstSomebodyTheLordHatesGivesFiveOncePerGrudge() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(3, 0);
        TestPlayer hated = human(5, 0);
        units(human.villageId, Map.of(UnitType.AXEMAN, 100));
        Instant until = clock.instant().plus(Duration.ofHours(30));
        grudge(lord, hated.playerId, until);

        for (int i = 0; i < 2; i++) {
            human.command("ATTACK", coordinatesOf(hated.villageId), "{\"AXEMAN\":10}")
                    .andExpect(status().isCreated());
            clock.advance(Duration.ofHours(1));
            processor.processDue(clock.instant());
        }
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(5);

        // a new attack renews the grudge: one more credit
        grudge(lord, hated.playerId, clock.instant().plus(Duration.ofHours(48)));
        human.command("ATTACK", coordinatesOf(hated.villageId), "{\"AXEMAN\":10}")
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(1));
        processor.processDue(clock.instant());
        assertThat(shownTrust(lord, human.playerId)).isEqualTo(10);
    }

    @Test
    void aConquestSendsTrustStraightToTheBottom() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(3, 0);
        setTrust(lord, human.playerId, 60);
        transaction.executeWithoutResult(
                status ->
                        reports.create(
                                ReportKind.CONQUEST,
                                clock.instant(),
                                new ConquestReport(
                                        ref(villageOf(lord)), ref(human.villageId), "Taken"),
                                human.playerId));

        assertThat(shownTrust(lord, human.playerId)).isEqualTo(-100);
        assertThat(trust.row(lord, human.playerId).orElseThrow().changes().getFirst())
                .isEqualTo(new NpcTrustRules.Change(TrustReason.CONQUERED, -160, "Lord village"));
    }

    @Test
    void trustDriftsAndTheWorldsClockMovesItsTimes() {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(3, 0);
        setTrust(lord, human.playerId, 30);
        Instant at = clock.instant();

        assertThat(trust.trust(lord, human.playerId, at.plus(Duration.ofDays(5)))).isEqualTo(20);
        transaction.executeWithoutResult(
                status -> frozenTime.shift(Duration.ofDays(5), clock.instant()));
        // asleep for five days: no drift in its sleep
        assertThat(trust.trust(lord, human.playerId, at.plus(Duration.ofDays(5)))).isEqualTo(30);
    }

    // ---- the court (12 §6) ----

    @Test
    void theStrongestLordGuildIsTheCourtAndAnotherTakingItsPlaceIsAnnounced() throws Exception {
        jdbc.sql("update world_settings set victory_mode = 'NONE'").update();
        worldService.reload();
        List<Long> lords = lords(4, NpcPersonality.BALANCED);
        long ravens = guildService.foundForLords(lords.get(0), lords.get(1), "Ordo Corvi", "CORVI");
        long bears =
                guildService.foundForLords(lords.get(2), lords.get(3), "Ordo Ursi II", "URSI2");
        TestPlayer human = human(5, 0);
        long mine = guildOf(human);
        lordPoints(lords.get(0), 5000);
        check();

        assertThat(courtGuildId()).isEqualTo(ravens);
        human.browser
                .get("/api/world/victory")
                .andExpect(
                        jsonPath("$.sides[?(@.id == %d)].court.key".formatted(ravens))
                                .value("RAVEN"));
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.court.id").value(ravens))
                .andExpect(jsonPath("$.court.court.key").value("RAVEN"))
                .andExpect(jsonPath("$.mine.id").value(mine));
        assertThat(sent.toPlayer(human.playerId)).noneMatch(json -> json.contains("COURT_CHANGED"));

        lordPoints(lords.get(3), 9000);
        check();

        assertThat(courtGuildId()).isEqualTo(bears);
        assertThat(sent.toPlayer(human.playerId))
                .filteredOn(json -> json.contains("COURT_CHANGED"))
                .singleElement()
                .satisfies(
                        json ->
                                assertThat(json)
                                        .contains("\"key\":\"BEAR\"")
                                        .contains("\"numeral\":\"II\"")
                                        .contains("\"key\":\"RAVEN\""));
    }

    @Test
    void theVictoryClockNoticeNamesTheCourt() {
        jdbc.sql(
                        "update world_settings set victory_mode = 'DOMINATION',"
                                + " domination_percent = 1, victory_hold_hours = 48")
                .update();
        worldService.reload();
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        long ravens = guildService.foundForLords(lords.get(0), lords.get(1), "Ordo Corvi", "CORVI");
        TestPlayer human = human(5, 0);
        TestPlayer met = human(8, 0);
        jdbc.sql("insert into npc_contact (player_id, lord_id, since) values (?, ?, ?)")
                .params(met.playerId, lords.get(0), Timestamp.from(clock.instant()))
                .update();
        for (int i = 0; i < 40; i++) {
            addVillage(lords.get(0), 10 + i, 20);
        }
        check();

        // the taunt: in the lord's own voice only to a human who has met it (review finding 03)
        assertThat(tauntVoice(human)).isEqualTo("STEADY");
        assertThat(tauntVoice(met)).isEqualTo("BOLD");

        assertThat(sent.toPlayer(human.playerId))
                .filteredOn(json -> json.contains("VICTORY_CLOCK"))
                .last()
                .satisfies(
                        json ->
                                assertThat(json)
                                        .contains("\"id\":" + ravens)
                                        .contains("\"court\":{\"key\":\"RAVEN\""));
    }

    @Test
    void outsideASoloWorldThereIsNoCourt() throws Exception {
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        guildService.foundForLords(lords.get(0), lords.get(1), "Ordo Corvi", "CORVI");
        TestPlayer human = human(5, 0);
        check();

        assertThat(courtGuildId()).isNull();
        human.browser.get("/api/diplomacy").andExpect(jsonPath("$.court").value(nullValue()));
    }

    // ---- helpers ----

    private void check() {
        transaction.executeWithoutResult(status -> victory.check(clock.instant()));
    }

    private Long courtGuildId() {
        return jdbc.sql("select court_guild_id from world_settings")
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private void invite(TestPlayer human, long lord) throws Exception {
        human.browser
                .post("/api/diplomacy/lords/" + lord + "/invite", "")
                .andExpect(status().isOk());
    }

    /**
     * Puts the lord into the human's guild directly (its invitation answered yes; like the lord's
     * own yes, a fall of its trust in the inviter earns a warning).
     */
    private void ally(long lord, TestPlayer human) {
        setTrust(lord, human.playerId, 100);
        long invitation = guildService.inviteLord(human.playerId, lord);
        assertThat(guildService.acceptForLord(lord, invitation)).isTrue();
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

    /** A second human joins the guild as a plain member. */
    private void joinGuild(TestPlayer human, long guild) {
        jdbc.sql(
                        "insert into guild_member (player_id, guild_id, role, joined_at) values (?,"
                                + " ?, 'MEMBER', ?)")
                .params(human.playerId, guild, Timestamp.from(clock.instant()))
                .update();
    }

    /** The human's guild, founded on first use. */
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

    private String tauntVoice(TestPlayer human) {
        return jdbc.sql(
                        """
                        select letter ->> 'voice' from private_message
                        where recipient_id = ? and letter ->> 'kind' = 'COURT_TAUNT'
                        """)
                .param(human.playerId)
                .query(String.class)
                .single();
    }

    private String roleOf(long playerId) {
        return jdbc.sql("select role from guild_member where player_id = ?")
                .param(playerId)
                .query(String.class)
                .single();
    }

    private Long guildIdOf(long playerId) {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private int openInvitations(long playerId) {
        return jdbc.sql("select count(*) from guild_invitation where player_id = ?")
                .param(playerId)
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

    private Instant contactSince(long playerId, long lord) {
        return jdbc.sql("select since from npc_contact where player_id = ? and lord_id = ?")
                .params(playerId, lord)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    /** The first contact without a battle (NpcContacts writes it from a report). */
    private void meet(long player, long lord) {
        jdbc.sql(
                        "insert into npc_contact (player_id, lord_id, since) values (?, ?, ?)"
                                + " on conflict do nothing")
                .params(player, lord, Timestamp.from(clock.instant()))
                .update();
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

    private int shownTrust(long lord, long player) {
        return NpcTrustRules.shown(trust.trust(lord, player, clock.instant()));
    }

    private void grudge(long lord, long target, Instant until) {
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    draft.grudges.put(target, new NpcMemory.Grudge(until, 0, false, 0));
                    brain.setMemory(draft.build());
                });
    }

    private void delivered(TestPlayer human, long lord, int amount) {
        int each = amount / 3;
        ResourceAmounts resources = new ResourceAmounts(each, each, amount - 2 * each);
        transaction.executeWithoutResult(
                status ->
                        reports.create(
                                ReportKind.TRADE_ARRIVED,
                                clock.instant(),
                                new TradeReport(
                                        ref(human.villageId),
                                        ref(villageOf(lord)),
                                        resources,
                                        ResourceAmounts.ZERO),
                                lord));
    }

    private VillageRefDto ref(long villageId) {
        return transaction.execute(
                status -> VillageRefDto.of(villages.findById(villageId).orElseThrow()));
    }

    private Coordinates coordinatesOf(long villageId) {
        Village village = villages.findById(villageId).orElseThrow();
        return new Coordinates(village.getX(), village.getY());
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

    /** {@code count} lords, their villages side by side in the scene, named "Lord village". */
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

    private void addVillage(long playerId, int dx, int dy) {
        transaction.executeWithoutResult(
                status -> {
                    Player player = players.findById(playerId).orElseThrow();
                    var levels = new BuildingLevels();
                    for (BuildingType type : BuildingType.values()) {
                        levels.set(type, gameData.building(type).startLevel());
                    }
                    int points = BuildRules.villagePoints(gameData, levels.toMap());
                    Village village =
                            villages.save(
                                    new Village(
                                            player,
                                            "Extra",
                                            new Coordinates(baseX + dx, baseY + dy),
                                            500,
                                            levels,
                                            points,
                                            clock.instant()));
                    stacks.save(UnitStack.homeOf(village));
                    player.addVillage(points);
                });
    }

    private void lordPoints(long lord, int points) {
        jdbc.sql("update player set points = ? where id = ?").params(points, lord).update();
        jdbc.sql("update village set points = ? where player_id = ?").params(points, lord).update();
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
}
