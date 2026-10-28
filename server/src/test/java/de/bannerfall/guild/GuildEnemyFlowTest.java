package de.bannerfall.guild;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Enemy marks (02-game-rules §16.3): the leader marks guilds, players, NPC lords and villages, all
 * members read them, and a mark stops counting when its target is gone or became a guild mate.
 */
@IntegrationTest
class GuildEnemyFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired GuildService guildService;
    @Autowired WorldService worldService;

    private TestPlayer leader;
    private TestPlayer member;
    private TestPlayer foe;
    private TestPlayer foeLeader;
    private long foeGuildId;
    private final List<Long> lords = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        setGuildsEnabled(true);
        guildService.setInviteInterval(Duration.ZERO);
        leader = TestPlayer.register(mockMvc);
        member = TestPlayer.register(mockMvc);
        foe = TestPlayer.register(mockMvc);
        foeLeader = TestPlayer.register(mockMvc);
        found(leader);
        join(leader, member);
        foeGuildId = found(foeLeader);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from guild").update(); // marks, members, invitations: on delete cascade
        // other tests count the NPC lords of the world
        lords.forEach(id -> jdbc.sql("delete from player where id = ?").param(id).update());
        lords.clear();
        setGuildsEnabled(false);
        guildService.setInviteInterval(Duration.ofMinutes(1));
    }

    private void setGuildsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set guilds_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private long found(TestPlayer founder) throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String body =
                body(
                        founder.browser
                                .post(
                                        "/api/guilds",
                                        "{\"name\":\"Foes "
                                                + suffix
                                                + "\",\"tag\":\""
                                                + suffix.substring(suffix.length() - 4)
                                                + "\"}")
                                .andExpect(status().isCreated()));
        return JsonPath.<Number>read(body, "$.guild.id").longValue();
    }

    private void join(TestPlayer owner, TestPlayer joiner) throws Exception {
        String name =
                jdbc.sql("select name from player where id = ?")
                        .param(joiner.playerId)
                        .query(String.class)
                        .single();
        owner.browser
                .post("/api/guilds/mine/invitations", "{\"playerName\":\"" + name + "\"}")
                .andExpect(status().isCreated());
        String mine = body(joiner.browser.get("/api/guilds/mine").andExpect(status().isOk()));
        long invitation = JsonPath.<Number>read(mine, "$.invitations[0].id").longValue();
        joiner.browser
                .post("/api/guilds/invitations/" + invitation + "/accept", "")
                .andExpect(status().isOk());
    }

    private ResultActions mark(TestPlayer by, String kind, long targetId) throws Exception {
        return by.browser.put(
                "/api/guilds/mine/enemies",
                "{\"kind\":\"" + kind + "\",\"targetId\":" + targetId + "}");
    }

    private ResultActions unmark(TestPlayer by, String kind, long targetId) throws Exception {
        return by.browser.delete("/api/guilds/mine/enemies/" + kind + "/" + targetId);
    }

    private ResultActions enemies(TestPlayer by) throws Exception {
        return by.browser.get("/api/guilds/mine/enemies");
    }

    private void refused(String kind, long targetId) throws Exception {
        String answer = body(mark(leader, kind, targetId));
        assertThat(answer).as(kind + " " + targetId).contains("ENEMY_NOT_ALLOWED");
    }

    private long newLord() {
        long id =
                jdbc.sql(
                                "insert into player (name, npc, points, village_count,"
                                    + " protected_until, created_at, production_factor, version)"
                                    + " values (?, true, 0, 0, now(), now(), 1.0, 0) returning id")
                        .param("Lord " + System.nanoTime())
                        .query(Long.class)
                        .single();
        lords.add(id);
        return id;
    }

    @Test
    void theLeaderMarksAndEveryMemberReadsTheList() throws Exception {
        long lord = newLord();

        mark(leader, "GUILD", foeGuildId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guilds[0].id").value(foeGuildId));
        mark(leader, "PLAYER", foe.playerId).andExpect(status().isOk());
        mark(leader, "PLAYER", lord).andExpect(status().isOk());
        mark(leader, "VILLAGE", foe.villageId).andExpect(status().isOk());

        enemies(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guilds.length()").value(1))
                .andExpect(jsonPath("$.guilds[0].tag").exists())
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.players[0].id").value(foe.playerId))
                .andExpect(jsonPath("$.players[0].npc").value(false))
                .andExpect(jsonPath("$.players[1].id").value(lord))
                .andExpect(jsonPath("$.players[1].npc").value(true))
                .andExpect(jsonPath("$.villages.length()").value(1))
                .andExpect(jsonPath("$.villages[0].id").value(foe.villageId))
                .andExpect(jsonPath("$.villages[0].ownerId").value(foe.playerId))
                .andExpect(jsonPath("$.max").value(GuildEnemyService.ENEMY_MAX));
        // the other guild sees nothing of it
        enemies(foeLeader)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guilds.length()").value(0))
                .andExpect(jsonPath("$.players.length()").value(0))
                .andExpect(jsonPath("$.villages.length()").value(0));

        // marking again changes nothing; unmarking is fine twice
        mark(leader, "PLAYER", foe.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2));
        unmark(leader, "PLAYER", foe.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1));
        unmark(leader, "PLAYER", foe.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1));
    }

    @Test
    void aPlainMemberReadsButCannotChangeTheList() throws Exception {
        mark(leader, "PLAYER", foe.playerId).andExpect(status().isOk());

        mark(member, "PLAYER", foeLeader.playerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GUILD_LEADER"));
        unmark(member, "PLAYER", foe.playerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GUILD_LEADER"));
        enemies(member).andExpect(jsonPath("$.players.length()").value(1));
    }

    @Test
    void ownPeopleAndBarbariansCannotBeMarked() throws Exception {
        long barbarian =
                jdbc.sql("select id from village where player_id is null limit 1")
                        .query(Long.class)
                        .optional()
                        .orElseGet(
                                () ->
                                        jdbc.sql(
                                                        "update village set player_id = null where"
                                                                + " id = ? returning id")
                                                .param(foe.villageId)
                                                .query(Long.class)
                                                .single());
        long ownGuildId =
                jdbc.sql("select guild_id from guild_member where player_id = ?")
                        .param(leader.playerId)
                        .query(Long.class)
                        .single();

        refused("GUILD", ownGuildId);
        refused("PLAYER", leader.playerId);
        refused("PLAYER", member.playerId);
        refused("VILLAGE", member.villageId);
        refused("VILLAGE", barbarian);
        mark(leader, "GUILD", 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUILD_NOT_FOUND"));
        mark(leader, "PLAYER", 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        mark(leader, "VILLAGE", 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        enemies(leader).andExpect(jsonPath("$.players.length()").value(0));
    }

    @Test
    void withoutAGuildOrWithGuildsOffNothingCanBeMarked() throws Exception {
        TestPlayer loner = TestPlayer.register(mockMvc);
        enemies(loner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));
        mark(loner, "PLAYER", foe.playerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));

        setGuildsEnabled(false);
        mark(leader, "PLAYER", foe.playerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILDS_DISABLED"));
    }

    @Test
    void aGuildCanMarkOnlySoManyEnemies() throws Exception {
        long guildId =
                jdbc.sql("select guild_id from guild_member where player_id = ?")
                        .param(leader.playerId)
                        .query(Long.class)
                        .single();
        for (int i = 0; i < GuildEnemyService.ENEMY_MAX; i++) {
            jdbc.sql(
                            "insert into guild_enemy (guild_id, kind, target_id, created_at)"
                                    + " values (?, 'PLAYER', ?, now())")
                    .param(guildId)
                    .param(newLord())
                    .update();
        }

        mark(leader, "PLAYER", foe.playerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENEMY_LIMIT"));
        // a mark that is there already is no new one
        long marked =
                jdbc.sql("select target_id from guild_enemy where guild_id = ? limit 1")
                        .param(guildId)
                        .query(Long.class)
                        .single();
        mark(leader, "PLAYER", marked).andExpect(status().isOk());
        // taking one away makes room
        unmark(leader, "PLAYER", marked).andExpect(status().isOk());
        mark(leader, "PLAYER", foe.playerId).andExpect(status().isOk());
    }

    @Test
    void marksStopCountingWhenTheTargetBecomesAGuildMateOrIsGone() throws Exception {
        mark(leader, "GUILD", foeGuildId).andExpect(status().isOk());
        mark(leader, "PLAYER", foe.playerId).andExpect(status().isOk());
        mark(leader, "VILLAGE", foe.villageId).andExpect(status().isOk());

        // the marked player joins the leader's guild: he and his village are no enemies any more
        join(leader, foe);
        enemies(leader)
                .andExpect(jsonPath("$.players.length()").value(0))
                .andExpect(jsonPath("$.villages.length()").value(0))
                .andExpect(jsonPath("$.guilds.length()").value(1));

        // the marked guild ends: its mark is gone from the list
        foeLeader.browser.delete("/api/guilds/mine").andExpect(status().isOk());
        enemies(leader).andExpect(jsonPath("$.guilds.length()").value(0));

        // the player leaves again: the stored mark counts again, nobody had to re-mark him
        foe.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());
        enemies(leader)
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.villages.length()").value(1));
    }

    @Test
    void theMarksEndWithTheGuild() throws Exception {
        mark(leader, "PLAYER", foe.playerId).andExpect(status().isOk());

        leader.browser.delete("/api/guilds/mine").andExpect(status().isOk());

        assertThat(jdbc.sql("select count(*) from guild_enemy").query(Long.class).single())
                .isZero();
    }
}
