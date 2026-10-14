package de.bannerfall.guild;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.world.WorldService;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Guilds (02-game-rules §16): founding, inviting, joining, leaving, kicking, handing the lead over,
 * disbanding, what the host's delete does, and where the tag shows up (ranking, map, profile).
 */
@IntegrationTest
class GuildFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @Autowired GuildService guildService;
    @Autowired WorldService worldService;
    @Autowired SentMessages sent;

    private TestPlayer leader;
    private TestPlayer member;
    private int maxMembersBefore;

    @BeforeEach
    void setUp() throws Exception {
        maxMembersBefore = worldService.settings().guildMaxMembers();
        setWorld(true, 10);
        // The one-invitation-a-minute limit has its own test; the others invite as often as they
        // like.
        guildService.setInviteInterval(Duration.ZERO);
        leader = TestPlayer.register(mockMvc);
        member = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        // The guilds of these tests go (members and invitations with them, on delete cascade).
        jdbc.sql("delete from guild").update();
        setWorld(false, maxMembersBefore);
        guildService.setInviteInterval(Duration.ofMinutes(1));
    }

    private void setWorld(boolean guildsEnabled, int maxMembers) {
        jdbc.sql("update world_settings set guilds_enabled = ?, guild_max_members = ?")
                .param(guildsEnabled)
                .param(maxMembers)
                .update();
        worldService.reload();
    }

    private static String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    /** Founds a guild with a name and tag nobody else has; returns the new guild's id. */
    private long found(TestPlayer founder, String name, String tag) throws Exception {
        String body =
                body(
                        founder.browser
                                .post(
                                        "/api/guilds",
                                        "{\"name\":\"" + name + "\",\"tag\":\"" + tag + "\"}")
                                .andExpect(status().isCreated()));
        return JsonPath.<Number>read(body, "$.guild.id").longValue();
    }

    private long foundAny(TestPlayer founder) throws Exception {
        String suffix = unique();
        return found(founder, "Wolves " + suffix, suffix.substring(suffix.length() - 4));
    }

    private ResultActions invite(TestPlayer by, String name) throws Exception {
        return by.browser.post("/api/guilds/mine/invitations", "{\"playerName\":\"" + name + "\"}");
    }

    private long invitationOf(TestPlayer invited) throws Exception {
        String mine = body(invited.browser.get("/api/guilds/mine").andExpect(status().isOk()));
        return JsonPath.<Number>read(mine, "$.invitations[0].id").longValue();
    }

    private String nameOf(TestPlayer player) {
        return jdbc.sql("select name from player where id = ?")
                .param(player.playerId)
                .query(String.class)
                .single();
    }

    /** Invites and accepts: the player is a member of the leader's guild afterwards. */
    private void join(TestPlayer owner, TestPlayer joiner) throws Exception {
        invite(owner, nameOf(joiner)).andExpect(status().isCreated());
        joiner.browser
                .post("/api/guilds/invitations/" + invitationOf(joiner) + "/accept", "")
                .andExpect(status().isOk());
    }

    // ---- founding ----

    @Test
    void aPlayerFoundsAGuildAndLeadsIt() throws Exception {
        leader.browser
                .post("/api/guilds", "{\"name\":\"Iron Wolves\",\"tag\":\"IW\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LEADER"))
                .andExpect(jsonPath("$.guild.name").value("Iron Wolves"))
                .andExpect(jsonPath("$.guild.tag").value("IW"))
                .andExpect(jsonPath("$.guild.members.length()").value(1))
                .andExpect(jsonPath("$.guild.members[0].playerId").value(leader.playerId))
                .andExpect(jsonPath("$.guild.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.guild.invitations").isEmpty())
                .andExpect(jsonPath("$.invitations").isEmpty());

        leader.browser
                .get("/api/guilds/mine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild.tag").value("IW"));
    }

    @Test
    void foundingNeedsGuildsSwitchedOn() throws Exception {
        setWorld(false, 10);

        leader.browser
                .post("/api/guilds", "{\"name\":\"Iron Wolves\",\"tag\":\"IW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILDS_DISABLED"));
    }

    @Test
    void badNamesAndTagsAreRefused() throws Exception {
        for (String json :
                List.of(
                        "{\"name\":\"ab\",\"tag\":\"AB\"}", // name too short
                        "{\"name\":\"" + "x".repeat(31) + "\",\"tag\":\"AB\"}", // too long
                        "{\"name\":\"Two  Spaces\",\"tag\":\"AB\"}",
                        "{\"name\":\" Padded\\t\",\"tag\":\"A\"}", // tag too short
                        "{\"name\":\"Wolves!\",\"tag\":\"AB\"}",
                        "{\"name\":\"Wolves\",\"tag\":\"ABCDEF\"}", // tag too long
                        "{\"name\":\"Wolves\",\"tag\":\"A B\"}",
                        "{\"name\":\"Wolves\",\"tag\":\"Ä1\"}", // ASCII only
                        "{\"name\":null,\"tag\":\"AB\"}",
                        "{\"tag\":\"AB\"}")) {
            leader.browser
                    .post("/api/guilds", json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        // nothing was created
        leader.browser.get("/api/guilds/mine").andExpect(jsonPath("$.guild").isEmpty());
    }

    @Test
    void nameAndTagAreUniqueIgnoringCase() throws Exception {
        String suffix = unique();
        String tag = suffix.substring(suffix.length() - 4);
        found(leader, "Wolves " + suffix, tag);

        member.browser
                .post(
                        "/api/guilds",
                        "{\"name\":\"WOLVES " + suffix.toUpperCase() + "\",\"tag\":\"ZZZ9\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_NAME_TAKEN"));
        member.browser
                .post(
                        "/api/guilds",
                        "{\"name\":\"Other " + suffix + "\",\"tag\":\"" + tag.toLowerCase() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_TAG_TAKEN"));
    }

    @Test
    void aMemberOfAGuildCannotFoundAnother() throws Exception {
        foundAny(leader);

        leader.browser
                .post("/api/guilds", "{\"name\":\"Second one\",\"tag\":\"S2\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_GUILD"));
    }

    @Test
    void foundingDropsThePlayersOpenInvitations() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());

        foundAny(member);

        assertThat(
                        jdbc.sql("select count(*) from guild_invitation where player_id = ?")
                                .param(member.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    // ---- inviting and joining ----

    @Test
    void aLeaderInvitesAndThePlayerJoins() throws Exception {
        long guildId = foundAny(leader);
        int before = sent.toPlayer(member.playerId).size();

        invite(leader, nameOf(member).toUpperCase())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guild.invitations.length()").value(1))
                .andExpect(jsonPath("$.guild.invitations[0].playerName").value(nameOf(member)));

        List<String> pushed = sent.toPlayer(member.playerId);
        assertThat(pushed).hasSize(before + 1);
        assertThat(JsonPath.<String>read(pushed.getLast(), "$.type")).isEqualTo("GUILD_INVITED");
        assertThat(JsonPath.<Number>read(pushed.getLast(), "$.guildId").longValue())
                .isEqualTo(guildId);

        member.browser
                .get("/api/guilds/mine")
                .andExpect(jsonPath("$.guild").isEmpty())
                .andExpect(jsonPath("$.invitations.length()").value(1))
                .andExpect(jsonPath("$.invitations[0].guildId").value(guildId));

        long leaderPushes = sent.toPlayer(leader.playerId).size();
        member.browser
                .post("/api/guilds/invitations/" + invitationOf(member) + "/accept", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.guild.members.length()").value(2))
                .andExpect(jsonPath("$.guild.invitations").isEmpty())
                .andExpect(jsonPath("$.invitations").isEmpty());

        // the leader's guild page is told to load again
        assertThat(sent.toPlayer(leader.playerId)).hasSize((int) leaderPushes + 1);
        assertThat(JsonPath.<String>read(sent.toPlayer(leader.playerId).getLast(), "$.type"))
                .isEqualTo("GUILD_CHANGED");
    }

    @Test
    void onlyTheLeaderInvitesAndSeesTheOpenInvitations() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);
        TestPlayer outsider = TestPlayer.register(mockMvc);

        invite(member, nameOf(outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GUILD_LEADER"));
        invite(outsider, nameOf(member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));

        invite(leader, nameOf(outsider)).andExpect(status().isCreated());
        leader.browser
                .get("/api/guilds/" + guildId)
                .andExpect(jsonPath("$.invitations.length()").value(1));
        member.browser.get("/api/guilds/" + guildId).andExpect(jsonPath("$.invitations").isEmpty());
        outsider.browser
                .get("/api/guilds/" + guildId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations").isEmpty());
    }

    @Test
    void inviteesMustExistAndBeFree() throws Exception {
        foundAny(leader);
        TestPlayer otherLeader = TestPlayer.register(mockMvc);
        foundAny(otherLeader);

        invite(leader, "nobody-" + unique())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        invite(leader, nameOf(otherLeader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_GUILD"));

        invite(leader, nameOf(member)).andExpect(status().isCreated());
        invite(leader, nameOf(member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_INVITED"));
    }

    @Test
    void anNpcLordCannotBeInvited() throws Exception {
        foundAny(leader);
        String name = "Lord " + unique();
        jdbc.sql(
                        "insert into player (name, npc, points, village_count, protected_until,"
                                + " created_at, production_factor, version)"
                                + " values (?, true, 0, 0, now(), now(), 1.0, 0)")
                .param(name)
                .update();

        invite(leader, name)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        jdbc.sql("delete from player where name = ?").param(name).update();
    }

    @Test
    void invitationsBelongToTheirPlayerAndTheirGuild() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        long invitationId = invitationOf(member);
        TestPlayer stranger = TestPlayer.register(mockMvc);
        TestPlayer otherLeader = TestPlayer.register(mockMvc);
        foundAny(otherLeader);

        stranger.browser
                .post("/api/guilds/invitations/" + invitationId + "/accept", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        stranger.browser
                .delete("/api/guilds/invitations/" + invitationId)
                .andExpect(status().isNotFound());
        otherLeader
                .browser
                .delete("/api/guilds/mine/invitations/" + invitationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void anInvitationCanBeDeclinedOrWithdrawn() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());

        member.browser
                .delete("/api/guilds/invitations/" + invitationOf(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations").isEmpty());

        invite(leader, nameOf(member)).andExpect(status().isCreated());
        leader.browser
                .delete("/api/guilds/mine/invitations/" + invitationOf(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild.invitations").isEmpty());
        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.invitations").isEmpty());
    }

    @Test
    void joiningDropsTheOtherInvitations() throws Exception {
        long first = foundAny(leader);
        TestPlayer otherLeader = TestPlayer.register(mockMvc);
        long second = foundAny(otherLeader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        invite(otherLeader, nameOf(member)).andExpect(status().isCreated());
        String mine = body(member.browser.get("/api/guilds/mine"));
        List<Number> guildsInvitedFrom = JsonPath.read(mine, "$.invitations[*].guildId");
        assertThat(guildsInvitedFrom).hasSize(2);
        List<Number> idsOfFirst =
                JsonPath.read(mine, "$.invitations[?(@.guildId == " + first + ")].id");
        long invitationOfFirst = idsOfFirst.get(0).longValue();

        member.browser
                .post("/api/guilds/invitations/" + invitationOfFirst + "/accept", "")
                .andExpect(status().isOk());

        assertThat(
                        jdbc.sql("select count(*) from guild_invitation where player_id = ?")
                                .param(member.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
        otherLeader
                .browser
                .get("/api/guilds/" + second)
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void aPlayerGetsOneInvitationPerMinute() throws Exception {
        guildService.setInviteInterval(Duration.ofMinutes(1));
        foundAny(leader);
        TestPlayer other = TestPlayer.register(mockMvc);

        // a refused invitation (unknown name) does not use up the slot
        invite(leader, "nobody" + unique()).andExpect(status().isNotFound());
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        leader.browser
                .delete("/api/guilds/mine/invitations/" + invitationOf(member))
                .andExpect(status().isOk());

        // invite, withdraw, invite again: refused, whoever asks
        invite(leader, nameOf(member))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INVITATION_RATE_LIMIT"));
        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.invitations").isEmpty());
        // somebody else is not affected
        invite(leader, nameOf(other)).andExpect(status().isCreated());

        guildService.setInviteInterval(Duration.ZERO);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
    }

    @Test
    void theLeaderHearsWhenAnInvitationIsDeclined() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        long pushes = sent.toPlayer(leader.playerId).size();

        member.browser
                .delete("/api/guilds/invitations/" + invitationOf(member))
                .andExpect(status().isOk());

        assertThat(sent.toPlayer(leader.playerId)).hasSize((int) pushes + 1);
        assertThat(JsonPath.<String>read(sent.toPlayer(leader.playerId).getLast(), "$.type"))
                .isEqualTo("GUILD_CHANGED");
    }

    @Test
    void theLeadersOfTheOtherInvitationsHearWhenThePlayerJoinsElsewhere() throws Exception {
        foundAny(leader);
        TestPlayer otherLeader = TestPlayer.register(mockMvc);
        foundAny(otherLeader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        invite(otherLeader, nameOf(member)).andExpect(status().isCreated());
        String mine = body(member.browser.get("/api/guilds/mine"));
        long invitationOfLeader =
                JsonPath.<List<Number>>read(
                                mine,
                                "$.invitations[?(@.invitedByName == '" + nameOf(leader) + "')].id")
                        .getFirst()
                        .longValue();
        long leaderPushes = sent.toPlayer(leader.playerId).size();
        long otherPushes = sent.toPlayer(otherLeader.playerId).size();

        member.browser
                .post("/api/guilds/invitations/" + invitationOfLeader + "/accept", "")
                .andExpect(status().isOk());

        // once each, the joined guild's leader too (his guild and the invitation changed at once)
        assertThat(sent.toPlayer(leader.playerId)).hasSize((int) leaderPushes + 1);
        assertThat(sent.toPlayer(otherLeader.playerId)).hasSize((int) otherPushes + 1);
        assertThat(JsonPath.<String>read(sent.toPlayer(otherLeader.playerId).getLast(), "$.type"))
                .isEqualTo("GUILD_CHANGED");
    }

    @Test
    void theInvitedPlayersHearWhenTheGuildIsDisbanded() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        long pushes = sent.toPlayer(member.playerId).size();

        leader.browser.delete("/api/guilds/mine").andExpect(status().isOk());

        assertThat(sent.toPlayer(member.playerId)).hasSize((int) pushes + 1);
        assertThat(JsonPath.<String>read(sent.toPlayer(member.playerId).getLast(), "$.type"))
                .isEqualTo("GUILD_CHANGED");
        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.invitations").isEmpty());
    }

    @Test
    void anInvitationTakenBackWhileTheAcceptWaitsForTheLockCannotBeAccepted() throws Exception {
        long guildId = foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        long invitationId = invitationOf(member);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection leadersRequest = dataSource.getConnection()) {
            leadersRequest.setAutoCommit(false);
            // What the leader's withdraw does first: lock the guild's row.
            try (Statement statement = leadersRequest.createStatement()) {
                statement.execute("select id from guild where id = " + guildId + " for update");
            }
            Future<Integer> accept = pool.submit(() -> acceptStatus(member, invitationId));
            awaitBlockedOnALock();
            try (Statement statement = leadersRequest.createStatement()) {
                statement.execute("delete from guild_invitation where id = " + invitationId);
            }
            leadersRequest.commit();
            assertThat(accept.get(10, TimeUnit.SECONDS)).isEqualTo(404);
        } finally {
            pool.shutdown();
        }
        assertThat(
                        jdbc.sql("select count(*) from guild_member where player_id = ?")
                                .param(member.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    /** Waits until a database session waits for a lock (the accept request, behind the leader). */
    private void awaitBlockedOnALock() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            long waiting =
                    jdbc.sql(
                                    "select count(*) from pg_stat_activity"
                                            + " where wait_event_type = 'Lock'"
                                            + " and datname = current_database()")
                            .query(Long.class)
                            .single();
            if (waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the accept request never waited for the guild's lock");
    }

    @Test
    void aFullGuildInvitesNoOneAndTakesNoOne() throws Exception {
        setWorld(true, 2);
        foundAny(leader);
        TestPlayer third = TestPlayer.register(mockMvc);
        TestPlayer fourth = TestPlayer.register(mockMvc);
        // two invitations while there is room for one more
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        invite(leader, nameOf(third)).andExpect(status().isCreated());

        member.browser
                .post("/api/guilds/invitations/" + invitationOf(member) + "/accept", "")
                .andExpect(status().isOk());

        third.browser
                .post("/api/guilds/invitations/" + invitationOf(third) + "/accept", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_FULL"));
        invite(leader, nameOf(fourth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_FULL"));
    }

    @Test
    void twoPlayersAcceptingTheLastPlaceAtOnceGetOnlyOne() throws Exception {
        setWorld(true, 2);
        foundAny(leader);
        TestPlayer third = TestPlayer.register(mockMvc);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        invite(leader, nameOf(third)).andExpect(status().isCreated());
        long forMember = invitationOf(member);
        long forThird = invitationOf(third);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> accepts = new ArrayList<>();
            accepts.add(() -> acceptStatus(member, forMember));
            accepts.add(() -> acceptStatus(third, forThird));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(accepts)) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.sql("select count(*) from guild_member").query(Long.class).single())
                .isEqualTo(2);
    }

    private int acceptStatus(TestPlayer player, long invitationId) throws Exception {
        return player.browser
                .post("/api/guilds/invitations/" + invitationId + "/accept", "")
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void joiningNeedsGuildsSwitchedOn() throws Exception {
        foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());
        long invitationId = invitationOf(member);
        setWorld(false, 10);

        member.browser
                .post("/api/guilds/invitations/" + invitationId + "/accept", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILDS_DISABLED"));
        // reading and leaving work all the same
        leader.browser.get("/api/guilds/mine").andExpect(status().isOk());
        leader.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());
    }

    // ---- leaving, kicking, leading ----

    @Test
    void aMemberLeavesAndTheLastOneTakesTheGuildDown() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);

        member.browser
                .post("/api/guilds/mine/leave", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild").isEmpty());
        leader.browser
                .get("/api/guilds/mine")
                .andExpect(jsonPath("$.guild.members.length()").value(1));

        leader.browser
                .post("/api/guilds/mine/leave", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild").isEmpty());
        leader.browser.get("/api/guilds/" + guildId).andExpect(status().isNotFound());
        leader.browser.post("/api/guilds/mine/leave", "").andExpect(status().isConflict());
    }

    @Test
    void theLeaderMustHandTheLeadOverBeforeLeaving() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);

        leader.browser
                .post("/api/guilds/mine/leave", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_MUST_TRANSFER"));

        leader.browser
                .post("/api/guilds/mine/leader", "{\"playerId\":" + member.playerId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.guild.members[0].playerId").value(member.playerId))
                .andExpect(jsonPath("$.guild.members[0].role").value("LEADER"));
        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.role").value("LEADER"));

        leader.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());
        member.browser
                .get("/api/guilds/" + guildId)
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void handingTheLeadOverNeedsAMemberOfTheGuild() throws Exception {
        foundAny(leader);
        TestPlayer outsider = TestPlayer.register(mockMvc);

        leader.browser
                .post("/api/guilds/mine/leader", "{\"playerId\":" + outsider.playerId + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUILD_MEMBER_NOT_FOUND"));
        leader.browser
                .post("/api/guilds/mine/leader", "{\"playerId\":" + leader.playerId + "}")
                .andExpect(status().isBadRequest());
        join(leader, member);
        member.browser
                .post("/api/guilds/mine/leader", "{\"playerId\":" + member.playerId + "}")
                .andExpect(status().isForbidden());
    }

    @Test
    void theLeaderKicksMembers() throws Exception {
        foundAny(leader);
        join(leader, member);
        TestPlayer outsider = TestPlayer.register(mockMvc);

        member.browser
                .delete("/api/guilds/mine/members/" + leader.playerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GUILD_LEADER"));
        leader.browser
                .delete("/api/guilds/mine/members/" + leader.playerId)
                .andExpect(status().isBadRequest());
        leader.browser
                .delete("/api/guilds/mine/members/" + outsider.playerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUILD_MEMBER_NOT_FOUND"));

        long pushes = sent.toPlayer(member.playerId).size();
        leader.browser
                .delete("/api/guilds/mine/members/" + member.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild.members.length()").value(1));

        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.guild").isEmpty());
        assertThat(sent.toPlayer(member.playerId)).hasSize((int) pushes + 1);
    }

    @Test
    void aLeaderOfAnotherGuildCannotKickMyMembers() throws Exception {
        foundAny(leader);
        join(leader, member);
        TestPlayer otherLeader = TestPlayer.register(mockMvc);
        foundAny(otherLeader);

        otherLeader
                .browser
                .delete("/api/guilds/mine/members/" + member.playerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUILD_MEMBER_NOT_FOUND"));
        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.guild").isNotEmpty());
    }

    @Test
    void theLeaderDisbandsTheGuildForEverybody() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);

        member.browser.delete("/api/guilds/mine").andExpect(status().isForbidden());
        leader.browser
                .delete("/api/guilds/mine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild").isEmpty());

        member.browser.get("/api/guilds/mine").andExpect(jsonPath("$.guild").isEmpty());
        member.browser.get("/api/guilds/" + guildId).andExpect(status().isNotFound());
        assertThat(jdbc.sql("select count(*) from guild_member").query(Long.class).single())
                .isZero();
    }

    @Test
    void theLeaderEditsTheDescription() throws Exception {
        foundAny(leader);
        join(leader, member);

        member.browser
                .put("/api/guilds/mine/description", "{\"description\":\"hi\"}")
                .andExpect(status().isForbidden());
        leader.browser
                .put(
                        "/api/guilds/mine/description",
                        "{\"description\":\"  We farm barbarians.\\n \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guild.description").value("We farm barbarians."));
        member.browser
                .get("/api/guilds/mine")
                .andExpect(jsonPath("$.guild.description").value("We farm barbarians."));
        leader.browser
                .put(
                        "/api/guilds/mine/description",
                        "{\"description\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ---- reading ----

    @Test
    void theGuildListShowsEveryGuildWithItsPoints() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);
        jdbc.sql("update player set points = 120 where id = ?").param(leader.playerId).update();
        jdbc.sql("update player set points = 30 where id = ?").param(member.playerId).update();

        String list = body(leader.browser.get("/api/guilds").andExpect(status().isOk()));

        assertThat(JsonPath.<List<Number>>read(list, "$[?(@.id == " + guildId + ")].points"))
                .containsExactly(150);
        assertThat(JsonPath.<List<Number>>read(list, "$[?(@.id == " + guildId + ")].memberCount"))
                .containsExactly(2);
        leader.browser
                .get("/api/guilds/" + guildId)
                .andExpect(jsonPath("$.points").value(150))
                .andExpect(jsonPath("$.members[0].points").value(120))
                .andExpect(jsonPath("$.members[1].name").value(nameOf(member)));
        leader.browser.get("/api/guilds/999999999").andExpect(status().isNotFound());
    }

    @Test
    void theTagShowsInTheRankingTheMapAndTheProfile() throws Exception {
        String suffix = unique();
        String tag = suffix.substring(suffix.length() - 4);
        long guildId = found(leader, "Tagged " + suffix, tag);
        int x =
                jdbc.sql("select x from village where id = ?")
                        .param(leader.villageId)
                        .query(Integer.class)
                        .single();
        int y =
                jdbc.sql("select y from village where id = ?")
                        .param(leader.villageId)
                        .query(Integer.class)
                        .single();
        // The ranking page holds the best 100 of all the players the suite registered.
        int leaderPoints = pointsOf(leader);
        int memberPoints = pointsOf(member);
        setPoints(leader, 700_000);
        setPoints(member, 699_999);
        try {
            String ranking =
                    body(member.browser.get("/api/ranking?size=100").andExpect(status().isOk()));
            assertThat(
                            JsonPath.<List<String>>read(
                                    ranking,
                                    "$.entries[?(@.playerId == " + leader.playerId + ")].guildTag"))
                    .containsExactly(tag);
            // a player in no guild has none
            assertThat(
                            JsonPath.<List<Object>>read(
                                    ranking,
                                    "$.entries[?(@.playerId == " + member.playerId + ")].guildTag"))
                    .containsExactly((Object) null);
        } finally {
            setPoints(leader, leaderPoints);
            setPoints(member, memberPoints);
        }

        String map = body(member.browser.get("/api/map?x=" + x + "&y=" + y + "&w=1&h=1"));
        assertThat(JsonPath.<String>read(map, "$[0].guildTag")).isEqualTo(tag);
        assertThat(JsonPath.<Number>read(map, "$[0].guildId").longValue()).isEqualTo(guildId);

        member.browser
                .get("/api/players/" + leader.playerId)
                .andExpect(jsonPath("$.guild.id").value(guildId))
                .andExpect(jsonPath("$.guild.tag").value(tag));
        leader.browser
                .get("/api/players/" + member.playerId)
                .andExpect(jsonPath("$.guild").isEmpty());
    }

    private int pointsOf(TestPlayer player) {
        return jdbc.sql("select points from player where id = ?")
                .param(player.playerId)
                .query(Integer.class)
                .single();
    }

    private void setPoints(TestPlayer player, int points) {
        jdbc.sql("update player set points = ? where id = ?")
                .param(points)
                .param(player.playerId)
                .update();
    }

    // ---- the host deletes a player ----

    @Test
    void whenTheHostDeletesTheLeaderTheLongestMemberLeads() throws Exception {
        long guildId = foundAny(leader);
        join(leader, member);
        TestPlayer newest = TestPlayer.register(mockMvc);
        join(leader, newest);

        new Host(mockMvc)
                .post("/api/admin/players/" + leader.playerId + "/delete", "")
                .andExpect(status().isOk());

        newest.browser
                .get("/api/guilds/" + guildId)
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].playerId").value(member.playerId))
                .andExpect(jsonPath("$.members[0].role").value("LEADER"));
        assertThat(
                        jdbc.sql(
                                        "select count(*) from guild_member where role = 'LEADER'"
                                                + " and guild_id = ?")
                                .param(guildId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void whenTheHostDeletesTheLastMemberTheGuildEndsAndInvitationsGo() throws Exception {
        long guildId = foundAny(leader);
        invite(leader, nameOf(member)).andExpect(status().isCreated());

        new Host(mockMvc)
                .post("/api/admin/players/" + member.playerId + "/delete", "")
                .andExpect(status().isOk());
        assertThat(
                        jdbc.sql("select count(*) from guild_invitation where player_id = ?")
                                .param(member.playerId)
                                .query(Long.class)
                                .single())
                .isZero();

        new Host(mockMvc)
                .post("/api/admin/players/" + leader.playerId + "/delete", "")
                .andExpect(status().isOk());
        assertThat(
                        jdbc.sql("select count(*) from guild where id = ?")
                                .param(guildId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void aPlayerBannedForGoodLeavesHisGuildATimedBanDoesNot() throws Exception {
        foundAny(leader);
        join(leader, member);
        Host host = new Host(mockMvc);

        host.post(
                        "/api/admin/players/" + member.playerId + "/ban",
                        "{\"until\":\"2099-01-01T00:00:00Z\",\"reason\":\"test\"}")
                .andExpect(status().isOk());
        assertThat(isMember(member)).isTrue();

        host.post("/api/admin/players/" + member.playerId + "/ban", "{\"reason\":\"test\"}")
                .andExpect(status().isOk());
        assertThat(isMember(member)).isFalse();
    }

    private boolean isMember(TestPlayer player) {
        return jdbc.sql("select count(*) from guild_member where player_id = ?")
                        .param(player.playerId)
                        .query(Long.class)
                        .single()
                == 1;
    }
}
