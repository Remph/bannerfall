package de.bannerfall.notify;

import de.bannerfall.account.SessionsEnded;
import de.bannerfall.player.PlayerService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * The open WebSocket connections (M14e), so the server can close them: all of a player's when his
 * account's sessions end ({@link SessionsEnded}: ban, deletion, password reset or change), and the
 * ones opened from an HTTP session when that session ends (logout, expiry, or ended by {@code
 * AccountGuardFilter}). Before M14e a connection outlived all of these until the page reloaded.
 *
 * <p>{@code WebSocketConfig} wraps the STOMP handler with {@link #decorate} and copies the HTTP
 * session id into the connection's attributes ({@link HttpSessionHandshakeInterceptor}). Spring
 * Boot registers this bean as an {@link HttpSessionListener}.
 */
@Component
public class PlayerConnections implements HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(PlayerConnections.class);

    /** Close code 1008: the server doesn't want this connection any more. */
    static final CloseStatus ENDED = CloseStatus.POLICY_VIOLATION.withReason("Session ended");

    private final PlayerService playerService;
    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();

    PlayerConnections(PlayerService playerService) {
        this.playerService = playerService;
    }

    /** The STOMP handler, remembering every connection while it is open. */
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
                    throws Exception {
                open.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** How many different players have a game connection open (M19, the landing page). */
    public int onlinePlayers() {
        return (int)
                open.values().stream()
                        .map(WebSocketSession::getPrincipal)
                        .filter(principal -> principal != null)
                        .map(Principal::getName)
                        .distinct()
                        .count();
    }

    /** After the commit of a ban, deletion or password change: the player's connections close. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(SessionsEnded event) {
        String player = String.valueOf(playerService.idOf(event.accountId()));
        close(
                open.values().stream()
                        .filter(
                                session ->
                                        session.getPrincipal() != null
                                                && player.equals(session.getPrincipal().getName()))
                        .toList());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        String id = event.getSession().getId();
        close(
                open.values().stream()
                        .filter(
                                session ->
                                        id.equals(
                                                session.getAttributes()
                                                        .get(
                                                                HttpSessionHandshakeInterceptor
                                                                        .HTTP_SESSION_ID_ATTR_NAME)))
                        .toList());
    }

    private void close(Iterable<WebSocketSession> sessions) {
        for (WebSocketSession session : sessions) {
            try {
                session.close(ENDED);
                log.info("Closed the WebSocket of player {}", session.getPrincipal().getName());
            } catch (IOException | RuntimeException e) {
                log.info("Closing a WebSocket failed: {}", e.toString());
            } finally {
                open.remove(session.getId());
            }
        }
    }
}
