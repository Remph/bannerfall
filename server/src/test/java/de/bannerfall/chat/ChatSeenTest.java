package de.bannerfall.chat;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
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
 * The chat read state (1.7): a marker per player and channel that only moves forward, is capped at
 * the newest message, belongs to the player's current guild, and is pushed to his devices when it
 * moves. Receiving the push over a real STOMP connection is in WebSocketTest.
 */
@IntegrationTest
class ChatSeenTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired ChatService chatService;
    @Autowired WorldService worldService;
    @Autowired SentMessages sent;

    private TestPlayer reader;
    private TestPlayer writer;
    private Long guildId;

    @BeforeEach
    void setUp() throws Exception {
        reader = TestPlayer.register(mockMvc);
        writer = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        if (guildId != null) {
            jdbc.sql("delete from guild where id = ?").param(guildId).update();
            jdbc.sql("update world_settings set guilds_enabled = false").update();
            worldService.reload();
            guildId = null;
        }
    }

    private long say(String text) {
        clock.advance(Duration.ofSeconds(1));
        return chatService.send(writer.playerId, text).id();
    }

    private void joinNewGuild(TestPlayer... members) {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        String suffix = Long.toString(System.nanoTime(), 36);
        guildId =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .param("Seen " + suffix)
                        .param(suffix.substring(suffix.length() - 4))
                        .query(Long.class)
                        .single();
        for (TestPlayer member : members) {
            jdbc.sql(
                            "insert into guild_member (player_id, guild_id, role, joined_at)"
                                    + " values (?, ?, 'MEMBER', now())")
                    .param(member.playerId)
                    .param(guildId)
                    .update();
        }
    }

    private String mark(TestPlayer who, String guild, long id) throws Exception {
        return body(
                who.browser
                        .put("/api/chat/seen", "{\"guildId\":%s,\"id\":%d}".formatted(guild, id))
                        .andExpect(status().isOk()));
    }

    private List<Integer> markers(TestPlayer who) throws Exception {
        return JsonPath.read(
                body(who.browser.get("/api/chat/seen").andExpect(status().isOk())), "$[*].id");
    }

    @Test
    void aNewPlayerHasNoMarkers() throws Exception {
        assertThat(markers(reader)).isEmpty();
    }

    @Test
    void theMarkerOnlyMovesForwardAndAnOlderOneAnswersTheCurrent() throws Exception {
        long first = say("one");
        long second = say("two");

        assertThat(JsonPath.<Integer>read(mark(reader, "null", first), "$.id"))
                .isEqualTo((int) first);
        assertThat(JsonPath.<Integer>read(mark(reader, "null", second), "$.id"))
                .isEqualTo((int) second);
        // a device that is behind: nothing moves back, and it learns where the marker is
        String stale = mark(reader, "null", first);
        assertThat(JsonPath.<Integer>read(stale, "$.id")).isEqualTo((int) second);
        assertThat(JsonPath.<Object>read(stale, "$.guildId")).isNull();
        assertThat(markers(reader)).containsExactly((int) second);
    }

    @Test
    void theMarkerIsCappedAtTheNewestMessage() throws Exception {
        long newest = say("only");

        String answer = mark(reader, "null", newest + 1000);

        assertThat(JsonPath.<Integer>read(answer, "$.id")).isEqualTo((int) newest);
    }

    @Test
    void aChannelWithoutMessagesKeepsNoMarker() throws Exception {
        joinNewGuild(reader);

        String answer = mark(reader, guildId.toString(), 5);

        assertThat(JsonPath.<Integer>read(answer, "$.id")).isZero();
        assertThat(markers(reader)).isEmpty();
    }

    @Test
    void everyPlayerHasHisOwnMarker() throws Exception {
        long id = say("hello");

        mark(reader, "null", id);

        assertThat(markers(reader)).containsExactly((int) id);
        assertThat(markers(writer)).isEmpty();
    }

    @Test
    void theOtherDevicesHearOnlyOfAMarkerThatMoved() throws Exception {
        long first = say("one");
        long second = say("two");
        int heard = sent.chatSeen(reader.playerId).size();

        mark(reader, "null", first);
        mark(reader, "null", first);
        mark(reader, "null", second);
        mark(reader, "null", first);

        List<String> pushed = sent.chatSeen(reader.playerId);
        assertThat(pushed).hasSize(heard + 2);
        assertThat(JsonPath.<Integer>read(pushed.get(heard), "$.id")).isEqualTo((int) first);
        assertThat(JsonPath.<Integer>read(pushed.get(heard + 1), "$.id")).isEqualTo((int) second);
        assertThat(sent.chatSeen(writer.playerId)).isEmpty();
    }

    @Test
    void theGuildChatHasItsOwnMarkerForTheCurrentGuildOnly() throws Exception {
        joinNewGuild(reader, writer);
        clock.advance(Duration.ofSeconds(1));
        long inGuild = chatService.sendToGuild(writer.playerId, "guild talk").id();
        long inWorld = say("world talk");

        mark(reader, guildId.toString(), inGuild);
        mark(reader, "null", inWorld);

        String all = body(reader.browser.get("/api/chat/seen").andExpect(status().isOk()));
        assertThat(JsonPath.<List<Integer>>read(all, "$[?(@.guildId == " + guildId + ")].id"))
                .containsExactly((int) inGuild);
        assertThat(JsonPath.<List<Integer>>read(all, "$[?(@.guildId == null)].id"))
                .containsExactly((int) inWorld);
        // a guild marker is capped by that guild's chat, not by the world's newer message
        String capped = mark(reader, guildId.toString(), inWorld + 1000);
        assertThat(JsonPath.<Integer>read(capped, "$.id")).isEqualTo((int) inGuild);
    }

    @Test
    void aGuildMarkerNeedsMembership() throws Exception {
        joinNewGuild(writer);

        reader.browser
                .put("/api/chat/seen", "{\"guildId\":%d,\"id\":1}".formatted(guildId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));
    }

    @Test
    void aGuildThatEndsTakesItsMarkersWithIt() throws Exception {
        joinNewGuild(reader, writer);
        clock.advance(Duration.ofSeconds(1));
        long inGuild = chatService.sendToGuild(writer.playerId, "bye").id();
        mark(reader, guildId.toString(), inGuild);

        jdbc.sql("delete from guild where id = ?").param(guildId).update();

        assertThat(markers(reader)).isEmpty();
        guildId = null;
        jdbc.sql("update world_settings set guilds_enabled = false").update();
        worldService.reload();
    }

    @Test
    void theRequestIsChecked() throws Exception {
        reader.browser
                .put("/api/chat/seen", "{\"guildId\":null,\"id\":0}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/chat/seen")).andExpect(status().isUnauthorized());
    }
}
