package de.bannerfall.chat;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The global chat (02-game-rules §13): validation, rate limit and broadcast through {@link
 * ChatService}, the history over REST. Sending over a real STOMP connection is in WebSocketTest.
 */
@IntegrationTest
class ChatTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired ChatService chatService;
    @Autowired SentMessages sent;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    // ---- sending ----

    @Test
    void aMessageIsTrimmedSavedAndSentToEveryoneAfterCommit() {
        int before = sent.chat().size();

        ChatMessageDto message = chatService.send(player.playerId, "  Hallo zusammen!\n ");

        assertThat(message.text()).isEqualTo("Hallo zusammen!");
        assertThat(message.playerId()).isEqualTo(player.playerId);
        assertThat(message.createdAt()).isEqualTo(clock.instant());
        String stored =
                jdbc.sql("select text from chat_message where id = ?")
                        .param(message.id())
                        .query(String.class)
                        .single();
        assertThat(stored).isEqualTo("Hallo zusammen!");
        List<String> broadcast = sent.chat().subList(before, sent.chat().size());
        assertThat(broadcast).hasSize(1);
        assertThat(JsonPath.<Integer>read(broadcast.get(0), "$.id").longValue())
                .isEqualTo(message.id());
        assertThat(JsonPath.<String>read(broadcast.get(0), "$.playerName"))
                .isEqualTo(message.playerName())
                .startsWith("P");
        assertThat(JsonPath.<String>read(broadcast.get(0), "$.text")).isEqualTo("Hallo zusammen!");
        assertThat(Instant.parse(JsonPath.read(broadcast.get(0), "$.createdAt")))
                .isEqualTo(clock.instant());
    }

    @Test
    void anEmptyMessageIsRefused() {
        assertRefused(null, ErrorCode.VALIDATION);
        assertRefused("", ErrorCode.VALIDATION);
        assertRefused(" \t\n ", ErrorCode.VALIDATION);
    }

    @Test
    void fiveHundredCharactersAreTheLimitCountedAsTheyAppear() {
        assertRefused("a".repeat(501), ErrorCode.VALIDATION);

        // trimmed first
        chatService.send(player.playerId, " " + "a".repeat(500) + " ");
        clock.advance(Duration.ofSeconds(1));
        // an emoji is two Java chars but one character (and one in varchar(500))
        String emojis = "🏰".repeat(500);
        assertThat(chatService.send(player.playerId, emojis).text()).isEqualTo(emojis);
    }

    @Test
    void onlyOneMessagePerSecondAndPlayer() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        chatService.send(player.playerId, "first");
        int before = sent.chat().size();

        clock.advance(Duration.ofMillis(999));
        assertRefused("too fast", ErrorCode.CHAT_RATE_LIMIT);
        assertThat(sent.chat()).hasSize(before);
        // someone else may talk meanwhile
        chatService.send(other.playerId, "me too");

        clock.advance(Duration.ofMillis(1));
        chatService.send(player.playerId, "second");
    }

    @Test
    void aRefusedMessageDoesNotCountForTheRateLimit() {
        assertRefused("", ErrorCode.VALIDATION);
        chatService.send(player.playerId, "now");
    }

    @Test
    void aRolledBackMessageDoesNotCountForTheRateLimit() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx -> {
                            chatService.send(player.playerId, "never sent");
                            tx.setRollbackOnly();
                        });
        chatService.send(player.playerId, "now");
    }

    // ---- history ----

    @Test
    void theHistoryHasTheLast100MessagesOldestFirstAndPagesBack() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            ids.add(insert("message " + i, false));
        }
        long hidden = insert("deleted by the admin", false);
        jdbc.sql("update chat_message set deleted = true where id = ?").param(hidden).update();
        long newest = insert("newest", false);

        String page = body(player.browser.get("/api/chat/messages").andExpect(status().isOk()));
        List<Number> pageIds = JsonPath.read(page, "$[*].id");
        assertThat(pageIds).hasSize(100);
        assertThat(pageIds.getLast().longValue()).isEqualTo(newest);
        assertThat(pageIds.stream().map(Number::longValue)).doesNotContain(hidden);
        // oldest first: ids 6..104 of this test, then the newest
        assertThat(pageIds.get(0).longValue()).isEqualTo(ids.get(6));
        assertThat(JsonPath.<String>read(page, "$[98].text")).isEqualTo("message 104");
        assertThat(JsonPath.<String>read(page, "$[99].playerName")).startsWith("P");
        assertThat(JsonPath.<Number>read(page, "$[99].playerId").longValue())
                .isEqualTo(player.playerId);

        String older =
                body(
                        player.browser
                                .get("/api/chat/messages?before=" + ids.get(6))
                                .andExpect(status().isOk()));
        List<Number> olderIds = JsonPath.read(older, "$[*].id");
        // older messages of other tests may come first
        assertThat(olderIds.subList(olderIds.size() - 6, olderIds.size()))
                .map(Number::longValue)
                .containsExactlyElementsOf(ids.subList(0, 6));
    }

    @Test
    void theHistoryNeedsALoginAndAValidBefore() throws Exception {
        Browser.open(mockMvc)
                .get("/api/chat/messages")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        player.browser
                .get("/api/chat/messages?before=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("before"));
    }

    private void assertRefused(String text, ErrorCode code) {
        assertThatThrownBy(() -> chatService.send(player.playerId, text))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private long insert(String text, boolean deleted) {
        return jdbc.sql(
                        "insert into chat_message (player_id, text, deleted, created_at)"
                                + " values (?, ?, ?, ?) returning id")
                .params(player.playerId, text, deleted, Timestamp.from(clock.instant()))
                .query(Long.class)
                .single();
    }
}
