package de.bannerfall.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Private messages and the block list (02-game-rules §17): sending, the inbox, unread counts,
 * limits, and what a block hides.
 */
@IntegrationTest
class MessageFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired SentMessages sent;

    private TestPlayer anna;
    private TestPlayer ben;

    @BeforeEach
    void registerPlayers() throws Exception {
        anna = TestPlayer.register(mockMvc);
        ben = TestPlayer.register(mockMvc);
    }

    private static ResultActions send(TestPlayer from, TestPlayer to, String text)
            throws Exception {
        return from.browser.post(
                "/api/messages/with/" + to.playerId, "{\"text\":" + quote(text) + "}");
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static void sendOk(TestPlayer from, TestPlayer to, String text) throws Exception {
        send(from, to, text).andExpect(status().isCreated());
    }

    private long unread(TestPlayer player) throws Exception {
        String json = TestPlayer.body(player.browser.get("/api/messages/unread"));
        return JsonPath.<Number>read(json, "$.unread").longValue();
    }

    private static ResultActions conversation(TestPlayer me, TestPlayer other) throws Exception {
        return me.browser.get("/api/messages/with/" + other.playerId);
    }

    private static ResultActions inbox(TestPlayer me) throws Exception {
        return me.browser.get("/api/messages/conversations");
    }

    /** One second later, so the next message passes the rate limit. */
    private void tick() {
        clock.advance(Duration.ofSeconds(2));
    }

    // ---- sending and reading ----

    @Test
    void aMessageArrivesUnreadAndIsNotifiedLive() throws Exception {
        send(anna, ben, "  Hello Ben  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.text").value("Hello Ben"));

        assertThat(unread(ben)).isEqualTo(1);
        assertThat(unread(anna)).isZero();
        assertThat(sent.toPlayer(ben.playerId))
                .anyMatch(
                        json ->
                                json.contains("\"MESSAGE_RECEIVED\"")
                                        && json.contains("\"fromPlayerId\":" + anna.playerId));
        assertThat(sent.toPlayer(anna.playerId)).noneMatch(j -> j.contains("MESSAGE_RECEIVED"));

        inbox(ben)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].playerId").value(anna.playerId))
                .andExpect(jsonPath("$[0].lastText").value("Hello Ben"))
                .andExpect(jsonPath("$[0].lastMine").value(false))
                .andExpect(jsonPath("$[0].unread").value(1));
        inbox(anna)
                .andExpect(jsonPath("$[0].playerId").value(ben.playerId))
                .andExpect(jsonPath("$[0].lastMine").value(true))
                .andExpect(jsonPath("$[0].unread").value(0));
    }

    @Test
    void readingMarksTheConversationReadAndOnlyThatOne() throws Exception {
        TestPlayer cleo = TestPlayer.register(mockMvc);
        sendOk(anna, ben, "one");
        tick();
        sendOk(cleo, ben, "two");
        tick();
        sendOk(anna, ben, "three");
        assertThat(unread(ben)).isEqualTo(3);

        // loading the messages doesn't mark them: the client says so when the person sees them
        conversation(ben, anna)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("one"))
                .andExpect(jsonPath("$[1].text").value("three"))
                .andExpect(jsonPath("$[0].mine").value(false));
        assertThat(unread(ben)).isEqualTo(3);

        ben.browser
                .post("/api/messages/with/" + anna.playerId + "/read", "")
                .andExpect(status().isNoContent());
        assertThat(unread(ben)).isEqualTo(1);
        // idempotent
        ben.browser
                .post("/api/messages/with/" + anna.playerId + "/read", "")
                .andExpect(status().isNoContent());
        assertThat(unread(ben)).isEqualTo(1);
    }

    @Test
    void theConversationShowsBothSidesOldestFirstAndPagesBack() throws Exception {
        long firstId = 0;
        for (int i = 1; i <= 3; i++) {
            ResultActions sentOne = send(i % 2 == 1 ? anna : ben, i % 2 == 1 ? ben : anna, "m" + i);
            String json = TestPlayer.body(sentOne.andExpect(status().isCreated()));
            if (i == 3) {
                firstId = JsonPath.<Number>read(json, "$.id").longValue();
            }
            tick();
        }
        conversation(anna, ben)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].text").value("m1"))
                .andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[1].text").value("m2"))
                .andExpect(jsonPath("$[1].mine").value(false))
                .andExpect(jsonPath("$[2].text").value("m3"));
        // before = id of m3: m1 and m2
        anna.browser
                .get("/api/messages/with/" + ben.playerId + "?before=" + firstId)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].text").value("m2"));
    }

    @Test
    void theInboxSortsTheNewestConversationFirst() throws Exception {
        TestPlayer cleo = TestPlayer.register(mockMvc);
        sendOk(anna, ben, "to ben");
        tick();
        sendOk(anna, cleo, "to cleo");
        tick();
        sendOk(ben, anna, "back");
        inbox(anna)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].playerId").value(ben.playerId))
                .andExpect(jsonPath("$[0].lastText").value("back"))
                .andExpect(jsonPath("$[1].playerId").value(cleo.playerId));
    }

    // ---- rules ----

    @Test
    void theTextFollowsTheChatRules() throws Exception {
        send(anna, ben, "   ").andExpect(status().isBadRequest());
        send(anna, ben, "x".repeat(501)).andExpect(status().isBadRequest());
        send(anna, ben, "bell\u0007").andExpect(status().isBadRequest());
        anna.browser
                .post("/api/messages/with/" + ben.playerId, "{}")
                .andExpect(status().isBadRequest());
        // 500 characters and a line break are fine
        send(anna, ben, "line one\nline two").andExpect(status().isCreated());
        tick();
        send(anna, ben, "y".repeat(500)).andExpect(status().isCreated());
        assertThat(unread(ben)).isEqualTo(2);
    }

    @Test
    void youCannotWriteToYourselfOrToSomeoneWhoIsNotThere() throws Exception {
        send(anna, anna, "me")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_PLAYER"));
        anna.browser
                .post("/api/messages/with/999999999", "{\"text\":\"hi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        anna.browser.get("/api/messages/with/999999999").andExpect(status().isNotFound());
    }

    @Test
    void nobodyCanWriteToADeletedPlayer() throws Exception {
        new Host(mockMvc)
                .post("/api/admin/players/" + ben.playerId + "/delete", "")
                .andExpect(status().isOk());
        send(anna, ben, "hello?")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    /** Code review 1.2, D6: a deleted player takes his messages with him (§17). */
    @Test
    void aDeletedPlayersConversationsDisappear() throws Exception {
        sendOk(ben, anna, "hello anna");
        tick();
        sendOk(anna, ben, "hi ben");
        assertThat(unread(anna)).isEqualTo(1);
        inbox(anna).andExpect(jsonPath("$.length()").value(1));

        new Host(mockMvc)
                .post("/api/admin/players/" + ben.playerId + "/delete", "")
                .andExpect(status().isOk());

        assertThat(unread(anna)).isZero();
        inbox(anna).andExpect(jsonPath("$.length()").value(0));
        conversation(anna, ben)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oneMessagePerSecond() throws Exception {
        sendOk(anna, ben, "first");
        send(anna, ben, "too fast")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MESSAGE_RATE_LIMIT"));
        // the refused message was not stored
        conversation(ben, anna).andExpect(jsonPath("$.length()").value(1));
        // the limit is per sender
        sendOk(ben, anna, "answer");
        clock.advance(Duration.ofSeconds(1));
        sendOk(anna, ben, "now fine");
    }

    @Test
    void aSenderCanLeaveAtMostThirtyUnreadMessagesWithOnePerson() throws Exception {
        for (int i = 0; i < 30; i++) {
            sendOk(anna, ben, "spam " + i);
            tick();
        }
        send(anna, ben, "31st")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOX_FULL"));
        // somebody else can still write, and so can Ben to Anna
        sendOk(ben, anna, "stop it");
        // reading makes room
        tick();
        ben.browser
                .post("/api/messages/with/" + anna.playerId + "/read", "")
                .andExpect(status().isNoContent());
        sendOk(anna, ben, "sorry");
    }

    @Test
    void theEndpointsNeedLogin() throws Exception {
        Browser stranger = Browser.open(mockMvc);
        stranger.get("/api/messages/unread").andExpect(status().isUnauthorized());
        stranger.get("/api/messages/conversations").andExpect(status().isUnauthorized());
        stranger.get("/api/blocks").andExpect(status().isUnauthorized());
    }

    // ---- blocking ----

    @Test
    void aBlockHidesTheBlockedPlayersMessagesAndTellsHimNothing() throws Exception {
        sendOk(anna, ben, "before the block");
        tick();
        assertThat(unread(ben)).isEqualTo(1);

        ben.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        // already received messages vanish too
        assertThat(unread(ben)).isZero();
        inbox(ben).andExpect(jsonPath("$.length()").value(0));
        conversation(ben, anna).andExpect(jsonPath("$.length()").value(0));

        int notifiedBefore = (int) countNotifications(ben);
        // Anna notices nothing: the message is accepted
        send(anna, ben, "ignored").andExpect(status().isCreated());
        assertThat(unread(ben)).isZero();
        assertThat(countNotifications(ben)).isEqualTo(notifiedBefore);
        inbox(ben).andExpect(jsonPath("$.length()").value(0));
        // and she sees her own side
        conversation(anna, ben).andExpect(jsonPath("$.length()").value(2));
        // Anna can't tell the block from a Ben who never reads: the same limit of 30 applies,
        // counted from the block ("ignored" was the first)
        for (int i = 0; i < 29; i++) {
            tick();
            send(anna, ben, "more " + i).andExpect(status().isCreated());
        }
        tick();
        send(anna, ben, "one too many")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOX_FULL"));

        // Ben can't write to someone he blocked
        tick();
        send(ben, anna, "hi")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_BLOCKED"));

        // unblocking brings the earlier conversation back; the messages sent meanwhile are read
        ben.browser.delete("/api/blocks/" + anna.playerId).andExpect(status().isNoContent());
        inbox(ben)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unread").value(1));
        assertThat(unread(ben)).isEqualTo(1);
        tick();
        sendOk(ben, anna, "hello again");
        // after the unblock only the unread messages count again (the ones written meanwhile are
        // read)
        tick();
        send(anna, ben, "after the unblock").andExpect(status().isCreated());
    }

    private long countNotifications(TestPlayer player) {
        return sent.toPlayer(player.playerId).stream()
                .filter(json -> json.contains("MESSAGE_RECEIVED"))
                .count();
    }

    @Test
    void theBlockListIsPrivateAndIdempotent() throws Exception {
        TestPlayer cleo = TestPlayer.register(mockMvc);
        ben.browser.get("/api/blocks").andExpect(jsonPath("$.length()").value(0));
        ben.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        ben.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        ben.browser.put("/api/blocks/" + cleo.playerId, "").andExpect(status().isNoContent());
        String json = TestPlayer.body(ben.browser.get("/api/blocks").andExpect(status().isOk()));
        List<Number> ids = JsonPath.read(json, "$[*].playerId");
        assertThat(ids.stream().map(Number::longValue))
                .containsExactlyInAnyOrder(anna.playerId, cleo.playerId);
        // the others don't see it
        anna.browser.get("/api/blocks").andExpect(jsonPath("$.length()").value(0));
        // unblocking someone who isn't blocked is fine
        ben.browser.delete("/api/blocks/" + anna.playerId).andExpect(status().isNoContent());
        ben.browser.delete("/api/blocks/" + anna.playerId).andExpect(status().isNoContent());
        ben.browser.get("/api/blocks").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void youCannotBlockYourselfOrNobody() throws Exception {
        ben.browser
                .put("/api/blocks/" + ben.playerId, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_PLAYER"));
        ben.browser.put("/api/blocks/999999999", "").andExpect(status().isNotFound());
    }
}
