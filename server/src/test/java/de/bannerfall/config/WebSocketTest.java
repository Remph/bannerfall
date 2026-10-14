package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.world.WorldService;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Real STOMP connections to the running server (01-architecture §4.10, 04-api <i>WebSocket</i>):
 * the handshake needs a logged-in session from the page's own origin, chat goes both ways, a
 * player's notifications reach only his connection, and what clients send or subscribe where they
 * have no business is dropped.
 */
@IntegrationTest
class WebSocketTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired MutableClock clock;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<Client> clients = new ArrayList<>();

    @AfterEach
    void disconnect() {
        clients.forEach(Client::close);
    }

    // ---- handshake ----

    @Test
    void theHandshakeNeedsALogin() {
        assertThatThrownBy(() -> connect(new WebSocketHttpHeaders()))
                .hasStackTraceContaining("401");
    }

    @Test
    void theHandshakeNeedsThePagesOwnOrigin() throws Exception {
        WebSocketHttpHeaders headers = loggedIn().headers();
        headers.setOrigin("https://evil.example");
        assertThatThrownBy(() -> connect(headers)).hasStackTraceContaining("403");
    }

    // ---- chat ----

    @Test
    void theServerAgreesOnHeartbeats() throws Exception {
        // the web client asks for 10 s both ways (stompjs default); the test client needs a
        // scheduler
        // to take part in heartbeats at all
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setDaemon(true);
        scheduler.initialize();
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setTaskScheduler(scheduler);
        stomp.setDefaultHeartbeat(new long[] {10_000, 10_000});
        Account account = loggedIn();
        Client client = new Client(account.username(), account.playerId());
        BlockingQueue<String> connected = new LinkedBlockingQueue<>();
        StompSessionHandlerAdapter handler =
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        connected.add(String.valueOf(headers.getFirst("heart-beat")));
                    }
                };
        client.session =
                stomp.connectAsync(
                                "ws://localhost:" + port + "/ws",
                                account.headers(),
                                new StompHeaders(),
                                handler)
                        .get(WAIT_SECONDS, TimeUnit.SECONDS);
        clients.add(client);

        assertThat(connected.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("10000,10000");
    }

    @Test
    void chatMessagesReachEveryoneTheSenderToo() throws Exception {
        Client ben = connect(loggedIn());
        Client tom = connect(loggedIn());
        BlockingQueue<String> benChat = ben.subscribe("/topic/chat");
        BlockingQueue<String> tomChat = tom.subscribe("/topic/chat");
        ben.ready();
        tom.ready();

        ben.send("/app/chat.send", "{\"text\":\"  Hallo Tom \"}");

        String atTom = next(tomChat, "Hallo Tom");
        assertThat(JsonPath.<String>read(atTom, "$.playerName")).isEqualTo(ben.username);
        assertThat(next(benChat, "Hallo Tom")).isEqualTo(atTom);
    }

    @Test
    void aRefusedMessageGoesBackToItsSenderOnly() throws Exception {
        Client ben = connect(loggedIn());
        Client tom = connect(loggedIn());
        tom.ready();
        ben.ready();

        ben.send("/app/chat.send", "{\"text\":\"first\"}");
        ben.send("/app/chat.send", "{\"text\":\"too fast\"}");

        String error = ben.errors.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(JsonPath.<String>read(error, "$.code")).isEqualTo("CHAT_RATE_LIMIT");
        // ready() checks that Tom's own refusal is the first thing on his error queue: Ben's
        // would have come first
        tom.ready();
    }

    @Test
    void guildChatReachesTheMembersOnly() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        String tag = Long.toString(System.nanoTime(), 36);
        long guildId =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .param("Sockets " + tag)
                        .param(tag.substring(tag.length() - 4))
                        .query(Long.class)
                        .single();
        try {
            Client ben = connect(loggedIn());
            Client tom = connect(loggedIn());
            Client mallory = connect(loggedIn());
            for (Client member : List.of(ben, tom)) {
                jdbc.sql(
                                "insert into guild_member (player_id, guild_id, role, joined_at)"
                                        + " values (?, ?, 'MEMBER', now())")
                        .param(member.playerId)
                        .param(guildId)
                        .update();
            }
            BlockingQueue<String> benGuild = ben.subscribe("/user/queue/guild-chat");
            BlockingQueue<String> tomGuild = tom.subscribe("/user/queue/guild-chat");
            BlockingQueue<String> malloryGuild = mallory.subscribe("/user/queue/guild-chat");
            BlockingQueue<String> malloryChat = mallory.subscribe("/topic/chat");
            ben.ready();
            tom.ready();
            mallory.ready();

            ben.send("/app/guild-chat.send", "{\"text\":\"  Meet at 20|20 \"}");

            String atTom = next(tomGuild, "Meet at 20|20");
            assertThat(JsonPath.<String>read(atTom, "$.playerName")).isEqualTo(ben.username);
            assertThat(next(benGuild, "Meet at 20|20")).isEqualTo(atTom);
            // an outsider is refused, and what the guild said never reached her
            mallory.send("/app/guild-chat.send", "{\"text\":\"let me in\"}");
            String refused = mallory.errors.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(refused).contains("NOT_IN_GUILD");
            assertThat(malloryGuild).isEmpty();
            assertThat(malloryChat).isEmpty();
        } finally {
            jdbc.sql("delete from guild where id = ?").param(guildId).update();
            jdbc.sql("update world_settings set guilds_enabled = false").update();
            worldService.reload();
        }
    }

    // ---- notifications ----

    @Test
    void aNotificationReachesOnlyItsPlayer() throws Exception {
        Client ben = connect(loggedIn());
        Client tom = connect(loggedIn());
        BlockingQueue<String> benEvents = ben.subscribe("/user/queue/events");
        BlockingQueue<String> tomEvents = tom.subscribe("/user/queue/events");
        ben.ready();
        tom.ready();

        notify(ben.playerId, new GameNotification.VillageUpdated(42));

        assertThat(benEvents.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo("{\"type\":\"VILLAGE_UPDATED\",\"villageId\":42}");
        // Tom's own notification is the first he gets: Ben's would have come first
        notify(tom.playerId, new GameNotification.VillageUpdated(43));
        assertThat(tomEvents.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo("{\"type\":\"VILLAGE_UPDATED\",\"villageId\":43}");
    }

    @Test
    void aNotificationReachesEveryConnectionOfItsPlayer() throws Exception {
        // two tabs, or a phone whose old connection hasn't timed out yet
        Account account = loggedIn();
        List<Client> tabs = List.of(connect(account), connect(account), connect(account));
        List<BlockingQueue<String>> events = new ArrayList<>();
        for (Client tab : tabs) {
            events.add(tab.subscribe("/user/queue/events"));
            tab.ready();
        }

        notify(account.playerId(), new GameNotification.VillageUpdated(42));
        notify(account.playerId(), new GameNotification.VillageUpdated(43));

        for (BlockingQueue<String> queue : events) {
            assertThat(queue.poll(WAIT_SECONDS, TimeUnit.SECONDS)).contains("42");
            assertThat(queue.poll(WAIT_SECONDS, TimeUnit.SECONDS)).contains("43");
        }
    }

    // ---- what clients may do ----

    @Test
    void sendingStraightToTheChatTopicIsDropped() throws Exception {
        Client tom = connect(loggedIn());
        BlockingQueue<String> tomChat = tom.subscribe("/topic/chat");
        tom.ready();
        Client mallory = connect(loggedIn());

        mallory.send("/topic/chat", "{\"id\":1,\"playerName\":\"Ben\",\"text\":\"fake\"}");
        // the connection goes on; everything before this is handled
        mallory.ready();

        // a real message sent afterwards is the first one Tom gets: the fake would have come first
        mallory.send("/app/chat.send", "{\"text\":\"real\"}");
        assertThat(tomChat.poll(WAIT_SECONDS, TimeUnit.SECONDS)).contains("\"text\":\"real\"");
    }

    @Test
    void subscribingToSomeoneElsesQueueIsDropped() throws Exception {
        Client ben = connect(loggedIn());
        Client mallory = connect(loggedIn());
        BlockingQueue<String> stolen = mallory.subscribe("/user/" + ben.playerId + "/queue/events");
        BlockingQueue<String> malloryEvents = mallory.subscribe("/user/queue/events");
        BlockingQueue<String> benEvents = ben.subscribe("/user/queue/events");
        ben.ready();
        mallory.ready();

        notify(ben.playerId, new GameNotification.VillageUpdated(42));
        notify(mallory.playerId, new GameNotification.VillageUpdated(43));

        assertThat(benEvents.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        // Mallory's connection gets its frames in order: had the stolen subscription worked,
        // Ben's notification would be there before her own
        assertThat(malloryEvents.poll(WAIT_SECONDS, TimeUnit.SECONDS)).contains("43");
        assertThat(stolen).isEmpty();
    }

    // ---- helpers ----

    private void notify(long playerId, GameNotification notification) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                events.publishEvent(
                                        new PlayerNotification(playerId, notification)));
    }

    /** A registered, logged-in account (real HTTP: the handshake needs a real session). */
    private Account loggedIn() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        String base = "http://localhost:" + port;
        http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).build(),
                HttpResponse.BodyHandlers.discarding());
        String username = Browser.uniqueUsername();
        HttpResponse<String> registered =
                http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/auth/register"))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", cookie(cookies, "XSRF-TOKEN"))
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                """
                                                {"username":"%s","password":"%s","inviteCode":"%s"}\
                                                """
                                                        .formatted(
                                                                username,
                                                                Browser.PASSWORD,
                                                                Browser.INVITE_CODE)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(registered.statusCode()).isEqualTo(201);
        long playerId = JsonPath.<Number>read(registered.body(), "$.playerId").longValue();
        return new Account(username, playerId, cookie(cookies, "JSESSIONID"));
    }

    private static String cookie(CookieManager cookies, String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No cookie " + name));
    }

    private record Account(String username, long playerId, String sessionId) {

        WebSocketHttpHeaders headers() {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("Cookie", "JSESSIONID=" + sessionId);
            return headers;
        }
    }

    private StompSession connect(WebSocketHttpHeaders headers) throws Exception {
        return connect(headers, new Client(null, 0)).session;
    }

    private Client connect(Account account) throws Exception {
        return connect(account.headers(), new Client(account.username(), account.playerId()));
    }

    private Client connect(WebSocketHttpHeaders headers, Client client) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new SimpleMessageConverter());
        try {
            client.session =
                    stomp.connectAsync(
                                    "ws://localhost:" + port + "/ws",
                                    headers,
                                    new StompHeaders(),
                                    client)
                            .get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Connect failed", e.getCause());
        }
        clients.add(client);
        return client;
    }

    /** The next message on the queue that contains the text. */
    private static String next(BlockingQueue<String> queue, String text) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < end) {
            String message = queue.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && message.contains(text)) {
                return message;
            }
        }
        throw new AssertionError("No message with '" + text + "'");
    }

    /** One STOMP connection of one player. */
    private class Client extends StompSessionHandlerAdapter {

        final String username;
        final long playerId;
        StompSession session;
        BlockingQueue<String> errors;

        Client(String username, long playerId) {
            this.username = username;
            this.playerId = playerId;
        }

        BlockingQueue<String> subscribe(String destination) {
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            session.subscribe(
                    destination,
                    new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return byte[].class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                        }
                    });
            return queue;
        }

        void send(String destination, String json) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination(destination);
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(headers, json.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Waits until the server has handled everything sent so far: the frames of a connection are
         * handled in order, so the answer to an empty chat message means the subscriptions before
         * it are in place. Subscribes to the chat errors if needed.
         */
        void ready() throws InterruptedException {
            if (errors == null) {
                errors = subscribe("/user/queue/errors");
            }
            send("/app/chat.send", "{\"text\":\"\"}");
            String error = errors.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).as("answer to the empty message").contains("VALIDATION");
            // the empty message doesn't count for the rate limit, but a later one in the same
            // second by the same player would: move on
            clock.advance(Duration.ofSeconds(1));
        }

        void close() {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
