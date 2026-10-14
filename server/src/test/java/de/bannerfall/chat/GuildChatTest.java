package de.bannerfall.chat;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guild chat (02-game-rules §13, §16): only the members read and write it, it never shows in
 * the global chat, the leader (and the host) hides messages, and a guild that ends takes its
 * messages with it. Sending over a real STOMP connection is in WebSocketTest.
 */
@IntegrationTest
class GuildChatTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired ChatService chatService;
    @Autowired WorldService worldService;
    @Autowired SentMessages sent;

    private TestPlayer leader;
    private TestPlayer member;
    private TestPlayer outsider;
    private long guildId;

    @BeforeEach
    void setUp() throws Exception {
        setGuildsEnabled(true);
        leader = TestPlayer.register(mockMvc);
        member = TestPlayer.register(mockMvc);
        outsider = TestPlayer.register(mockMvc);
        String suffix = Long.toString(System.nanoTime(), 36);
        guildId =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .param("Chatters " + suffix)
                        .param(suffix.substring(suffix.length() - 4))
                        .query(Long.class)
                        .single();
        addMember(leader, "LEADER");
        addMember(member, "MEMBER");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from guild where id = ?").param(guildId).update();
        setGuildsEnabled(false);
    }

    private void setGuildsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set guilds_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void addMember(TestPlayer player, String role) {
        jdbc.sql(
                        "insert into guild_member (player_id, guild_id, role, joined_at) values (?,"
                                + " ?, ?, now())")
                .param(player.playerId)
                .param(guildId)
                .param(role)
                .update();
    }

    /** Messages need a second between them from the same player. */
    private ChatMessageDto say(TestPlayer who, String text) {
        clock.advance(Duration.ofSeconds(1));
        return chatService.sendToGuild(who.playerId, text);
    }

    // ---- sending ----

    @Test
    void aMessageIsStoredForTheGuildAndSentToEachMemberOnly() {
        int globalBefore = sent.chat().size();

        ChatMessageDto message = say(member, "  Attack at dawn \n");

        assertThat(message.text()).isEqualTo("Attack at dawn");
        assertThat(
                        jdbc.sql("select guild_id from chat_message where id = ?")
                                .param(message.id())
                                .query(Long.class)
                                .single())
                .isEqualTo(guildId);
        for (TestPlayer inside : List.of(leader, member)) {
            List<String> heard = sent.guildChat(inside.playerId);
            assertThat(heard).hasSize(1);
            assertThat(JsonPath.<String>read(heard.get(0), "$.text")).isEqualTo("Attack at dawn");
            assertThat(JsonPath.<String>read(heard.get(0), "$.playerName"))
                    .isEqualTo(message.playerName());
        }
        assertThat(sent.guildChat(outsider.playerId)).isEmpty();
        assertThat(sent.chat()).hasSize(globalBefore);
    }

    @Test
    void onlyAMemberCanWriteAndOnlyWhileGuildsAreOn() {
        assertRefused(outsider, "hello?", ErrorCode.NOT_IN_GUILD);

        setGuildsEnabled(false);
        assertRefused(member, "hello?", ErrorCode.GUILDS_DISABLED);
    }

    @Test
    void theTextRulesAndTheRateLimitAreTheGlobalChats() {
        assertRefused(member, "  ", ErrorCode.VALIDATION);
        assertRefused(member, "a".repeat(501), ErrorCode.VALIDATION);

        chatService.sendToGuild(member.playerId, "one");
        // the slot is shared with the global chat
        assertThatThrownBy(() -> chatService.send(member.playerId, "two"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CHAT_RATE_LIMIT));
    }

    // ---- history ----

    @Test
    void theHistoryIsForMembersAndNotMixedWithTheGlobalChat() throws Exception {
        ChatMessageDto inGuild = say(member, "guild only");
        clock.advance(Duration.ofSeconds(1));
        ChatMessageDto global = chatService.send(member.playerId, "for everybody");

        String mine = body(member.browser.get("/api/guilds/mine/chat").andExpect(status().isOk()));
        assertThat(JsonPath.<List<Integer>>read(mine, "$[*].id"))
                .containsExactly((int) inGuild.id());

        String everybody =
                body(outsider.browser.get("/api/chat/messages").andExpect(status().isOk()));
        List<Integer> ids = JsonPath.read(everybody, "$[*].id");
        assertThat(ids).contains((int) global.id()).doesNotContain((int) inGuild.id());
    }

    @Test
    void anOutsiderHasNoHistoryAndTheHistoryNeedsALogin() throws Exception {
        outsider.browser
                .get("/api/guilds/mine/chat")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));
        mockMvc.perform(get("/api/guilds/mine/chat")).andExpect(status().isUnauthorized());
        member.browser.get("/api/guilds/mine/chat?before=0").andExpect(status().isBadRequest());
    }

    @Test
    void aMemberWhoLeavesNoLongerHearsOrReadsTheChat() throws Exception {
        say(leader, "before");
        jdbc.sql("delete from guild_member where player_id = ?").param(member.playerId).update();
        int heardBefore = sent.guildChat(member.playerId).size();

        say(leader, "after");

        assertThat(sent.guildChat(member.playerId)).hasSize(heardBefore);
        member.browser.get("/api/guilds/mine/chat").andExpect(status().isConflict());
    }

    // ---- hiding ----

    @Test
    void theLeaderHidesAMessageAndTheMembersHearOfIt() throws Exception {
        ChatMessageDto message = say(member, "rude");
        int heardBefore = sent.guildChat(member.playerId).size();

        leader.browser
                .delete("/api/guilds/mine/chat/" + message.id())
                .andExpect(status().isNoContent());

        List<String> heard = sent.guildChat(member.playerId);
        assertThat(heard).hasSize(heardBefore + 1);
        assertThat(JsonPath.<Integer>read(heard.get(heardBefore), "$.hiddenId"))
                .isEqualTo((int) message.id());
        assertThat(sent.guildChat(outsider.playerId)).isEmpty();
        String history =
                body(member.browser.get("/api/guilds/mine/chat").andExpect(status().isOk()));
        assertThat(JsonPath.<List<Object>>read(history, "$")).isEmpty();
        // again: nothing more is sent
        leader.browser
                .delete("/api/guilds/mine/chat/" + message.id())
                .andExpect(status().isNoContent());
        assertThat(sent.guildChat(member.playerId)).hasSize(heardBefore + 1);
    }

    @Test
    void onlyTheLeaderHidesAndOnlyHisOwnGuildsMessages() throws Exception {
        ChatMessageDto message = say(leader, "keep");
        member.browser
                .delete("/api/guilds/mine/chat/" + message.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GUILD_LEADER"));
        outsider.browser
                .delete("/api/guilds/mine/chat/" + message.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));

        // another guild's leader, a global message and an unknown id are all "not found"
        TestPlayer other = TestPlayer.register(mockMvc);
        String suffix = Long.toString(System.nanoTime(), 36);
        long otherGuild =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .param("Others " + suffix)
                        .param(suffix.substring(suffix.length() - 4))
                        .query(Long.class)
                        .single();
        try {
            jdbc.sql(
                            "insert into guild_member (player_id, guild_id, role, joined_at) values"
                                    + " (?, ?, 'LEADER', now())")
                    .param(other.playerId)
                    .param(otherGuild)
                    .update();
            other.browser
                    .delete("/api/guilds/mine/chat/" + message.id())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
            clock.advance(Duration.ofSeconds(1));
            ChatMessageDto global = chatService.send(leader.playerId, "global");
            leader.browser
                    .delete("/api/guilds/mine/chat/" + global.id())
                    .andExpect(status().isNotFound());
            leader.browser
                    .delete("/api/guilds/mine/chat/999999999")
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.sql("delete from guild where id = ?").param(otherGuild).update();
        }
        assertThat(
                        jdbc.sql("select deleted from chat_message where id = ?")
                                .param(message.id())
                                .query(Boolean.class)
                                .single())
                .isFalse();
    }

    @Test
    void whenTheHostHidesAGuildMessageOnlyTheGuildHearsOfIt() {
        ChatMessageDto message = say(member, "to be hidden by the host");
        int heardBefore = sent.guildChat(leader.playerId).size();
        int globalBefore = sent.chat().size();

        chatService.hide(message.id());

        assertThat(sent.guildChat(leader.playerId)).hasSize(heardBefore + 1);
        assertThat(sent.guildChat(outsider.playerId)).isEmpty();
        assertThat(sent.chat()).hasSize(globalBefore);
    }

    // ---- the end of a guild ----

    @Test
    void aGuildThatEndsTakesItsMessagesWithIt() {
        ChatMessageDto message = say(member, "gone with the guild");

        jdbc.sql("delete from guild where id = ?").param(guildId).update();

        assertThat(
                        jdbc.sql("select count(*) from chat_message where id = ?")
                                .param(message.id())
                                .query(Long.class)
                                .single())
                .isZero();
    }

    private void assertRefused(TestPlayer who, String text, ErrorCode code) {
        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> chatService.sendToGuild(who.playerId, text))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
