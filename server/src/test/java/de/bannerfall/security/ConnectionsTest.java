package de.bannerfall.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * M14e: the server ends a player's open WebSocket connections when his sessions end (ban, logout,
 * password change) and refuses new ones; a frame to a destination clients may not use lands in the
 * suspicion log. Real connections, like {@code WebSocketTest}.
 */
@IntegrationTest
class ConnectionsTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;

    private final java.util.List<StompSession> sessions = new java.util.ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void aBanClosesTheOpenConnectionAndRefusesNewOnes() throws Exception {
        Player player = register();
        StompSession open = connect(player);
        assertThat(open.isConnected()).isTrue();

        new Host(mockMvc)
                .post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\""
                                + clock.instant().plus(Duration.ofDays(1))
                                + "\",\"reason\":\"x\"}")
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());

        await("the connection closes", () -> !open.isConnected());
        assertThatThrownBy(() -> connect(player)).hasStackTraceContaining("403");
        HttpResponse<String> login = player.login();
        assertThat(login.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(login.body(), "$.code")).isEqualTo("ACCOUNT_BANNED");
    }

    @Test
    void aLogoutClosesTheConnectionsOfThatSession() throws Exception {
        Player player = register();
        StompSession open = connect(player);

        assertThat(player.post("/api/auth/logout", "").statusCode()).isEqualTo(204);

        await("the connection closes", () -> !open.isConnected());
    }

    @Test
    void changingThePasswordClosesTheConnectionsOfTheOtherSessions() throws Exception {
        Player player = register();
        Player other = player.loginAgain();
        StompSession mine = connect(player);
        StompSession theirs = connect(other);

        HttpResponse<String> changed =
                player.post(
                        "/api/me/password",
                        "{\"currentPassword\":\""
                                + Browser.PASSWORD
                                + "\",\"newPassword\":\"another password 1\"}");
        assertThat(changed.statusCode()).isEqualTo(204);

        await("the other session's connection closes", () -> !theirs.isConnected());
        // this browser goes on with a new session; its old connection was bound to the old one
        // and the client would reconnect
        assertThat(player.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(other.get("/api/me").statusCode()).isEqualTo(401);
        await("the old connection of this browser closes too", () -> !mine.isConnected());
        assertThat(connect(player).isConnected()).isTrue();
    }

    /**
     * Code review 1.2: the guard used to look at the raw request URI, so {@code /%61pi/me} (which
     * routing decodes to {@code /api/me}) slipped past the ban and the session check.
     */
    @Test
    void anEncodedPathIsNoWayRoundABanOrAPasswordChange() throws Exception {
        Player player = register();
        Player other = player.loginAgain();
        HttpResponse<String> changed =
                player.post(
                        "/api/me/password",
                        "{\"currentPassword\":\""
                                + Browser.PASSWORD
                                + "\",\"newPassword\":\"another password 1\"}");
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(other.get("/%61pi/me").statusCode()).isEqualTo(401);

        new Host(mockMvc)
                .post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\""
                                + clock.instant().plus(Duration.ofDays(1))
                                + "\",\"reason\":\"x\"}")
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        HttpResponse<String> me = player.get("/%61pi/me");
        assertThat(me.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(me.body(), "$.code")).isEqualTo("ACCOUNT_BANNED");
    }

    @Test
    void aFrameToAForbiddenDestinationIsRecorded() throws Exception {
        Player player = register();
        StompSession session = connect(player);

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/chat");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, "{\"text\":\"fake\"}".getBytes(StandardCharsets.UTF_8));

        await(
                "the suspicion log has the frame",
                () ->
                        jdbc.sql(
                                                "select count(*) from abuse_event e join player p"
                                                        + " on p.account_id = e.account_id where"
                                                        + " p.id = ? and e.kind = 'STOMP_REFUSED'")
                                        .param(player.playerId)
                                        .query(Long.class)
                                        .single()
                                == 1);
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void thePublicStatusCountsPlayersWithAnOpenConnectionOnce() throws Exception {
        Player player = register();
        int before = onlineCount(player);

        StompSession first = connect(player);
        await("the player counts as online", () -> onlineCount(player) == before + 1);
        // a second tab of the same player is not a second player
        StompSession second = connect(player.loginAgain());
        assertThat(onlineCount(player)).isEqualTo(before + 1);

        first.disconnect();
        assertThat(onlineCount(player)).isEqualTo(before + 1);
        second.disconnect();
        await("nobody of this player is online", () -> onlineCount(player) == before);
    }

    // ---- helpers ----

    private static int onlineCount(Player visitor) {
        try {
            return JsonPath.<Integer>read(
                    visitor.get("/api/world/status").body(), "$.onlinePlayers");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out: " + what);
    }

    private Player register() throws Exception {
        Player player = new Player(port, Browser.uniqueUsername());
        HttpResponse<String> registered =
                player.post(
                        "/api/auth/register",
                        """
                        {"username":"%s","password":"%s","inviteCode":"%s"}\
                        """
                                .formatted(player.username, Browser.PASSWORD, Browser.INVITE_CODE));
        assertThat(registered.statusCode()).isEqualTo(201);
        player.playerId = JsonPath.<Number>read(registered.body(), "$.playerId").longValue();
        return player;
    }

    private StompSession connect(Player player) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + player.sessionId());
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new SimpleMessageConverter());
        try {
            StompSession session =
                    stomp.connectAsync(
                                    "ws://localhost:" + port + "/ws",
                                    headers,
                                    new StompHeaders(),
                                    new StompSessionHandlerAdapter() {})
                            .get(WAIT_SECONDS, TimeUnit.SECONDS);
            sessions.add(session);
            return session;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Connect failed", e.getCause());
        }
    }

    /** A browser over real HTTP: cookies, CSRF header. */
    private static final class Player {

        final int port;
        final String username;
        long playerId;
        final CookieManager cookies = new CookieManager();
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        Player(int port, String username) {
            this.port = port;
            this.username = username;
        }

        HttpResponse<String> get(String path) throws Exception {
            return http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String json) throws Exception {
            if (cookie("XSRF-TOKEN") == null) {
                get("/api/auth/csrf");
            }
            return http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Content-Type", "application/json")
                            .header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"))
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> login() throws Exception {
            return post(
                    "/api/auth/login",
                    "{\"username\":\"%s\",\"password\":\"%s\"}"
                            .formatted(username, Browser.PASSWORD));
        }

        /** Another browser of the same account. */
        Player loginAgain() throws Exception {
            Player other = new Player(port, username);
            other.playerId = playerId;
            assertThat(other.login().statusCode()).isEqualTo(200);
            return other;
        }

        String sessionId() {
            return cookie("JSESSIONID");
        }

        String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals(name))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }
}
