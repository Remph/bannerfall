package de.bannerfall.config;

import de.bannerfall.abuse.AbuseKind;
import de.bannerfall.abuse.AbuseService;
import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.notify.PlayerConnections;
import de.bannerfall.player.PlayerPrincipal;
import de.bannerfall.player.PlayerService;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP over a plain WebSocket at {@code /ws} (01-architecture §4.10, 04-api <i>WebSocket</i>).
 *
 * <ul>
 *   <li>The handshake is an HTTP request: {@link SecurityConfig} lets only logged-in sessions
 *       through, and only from the page's own origin (Spring's default; the Vite proxy keeps the
 *       {@code Host} header, so it counts as the same origin in development).
 *   <li>The connection's user is the {@link PlayerPrincipal}: {@code /user/queue/events} of a
 *       player is {@code /user/{playerId}/queue/events} on the server side.
 *   <li>Clients may only send to {@code /app/**} and only subscribe to the chat and their own
 *       queues ({@link DestinationGuard}). Without that guard a client could SEND straight to
 *       {@code /topic/chat} and skip {@code ChatService} (no checks, any name). A dropped frame
 *       goes to the suspicion log (M14e).
 *   <li>The server closes a player's connections when his sessions end (ban, deletion, password,
 *       logout; {@link PlayerConnections}, M14e).
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * How often each side sends a heartbeat at most (the browser's STOMP client asks for the same).
     */
    static final long HEARTBEAT_MS = 10_000;

    private final PlayerService playerService;
    private final PlayerConnections connections;
    private final AbuseService abuse;

    WebSocketConfig(
            PlayerService playerService, PlayerConnections connections, AbuseService abuse) {
        this.playerService = playerService;
        this.connections = connections;
        this.abuse = abuse;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The interceptor copies the HTTP session id into the connection (PlayerConnections).
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new PlayerHandshakeHandler(playerService))
                .addInterceptors(new HttpSessionHandshakeInterceptor());
        // A client's frames are handled in the order it sent them (subscribe, then send).
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Heartbeats both ways (M14f): a phone that went to sleep or changed network leaves a dead
        // connection that neither side notices otherwise; the web client reconnects when the
        // server falls silent. The broker needs a scheduler for them (daemon thread, one is
        // enough).
        ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setPoolSize(1);
        heartbeats.setDaemon(true);
        heartbeats.setThreadNamePrefix("ws-heartbeat-");
        heartbeats.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // Each client gets the messages in the order they were sent (chat).
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(connections::decorate);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new DestinationGuard(abuse));
    }

    /** Names the connection after the logged-in account's player. */
    static class PlayerHandshakeHandler extends DefaultHandshakeHandler {

        private final PlayerService playerService;

        PlayerHandshakeHandler(PlayerService playerService) {
            this.playerService = playerService;
        }

        @Override
        protected Principal determineUser(
                ServerHttpRequest request,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            if (request.getPrincipal() instanceof Authentication auth
                    && auth.getPrincipal() instanceof AccountPrincipal account) {
                return new PlayerPrincipal(playerService.idOf(account.accountId()));
            }
            // Can't happen behind SecurityConfig; without a user the connection can do nothing.
            return null;
        }
    }

    /**
     * Drops SENDs and SUBSCRIBEs a client has no business with; everything else (CONNECT,
     * DISCONNECT, UNSUBSCRIBE ...) passes. The web client never sends such frames, so a dropped one
     * is someone talking to the server by hand: logged at WARN and recorded in the suspicion log
     * (M14e). Dropped, not refused with an ERROR frame: with the receive order preserved Spring
     * would only log an exception and send nothing either.
     */
    static class DestinationGuard implements ChannelInterceptor {

        private static final Logger log = LoggerFactory.getLogger(DestinationGuard.class);

        private final AbuseService abuse;

        DestinationGuard(AbuseService abuse) {
            this.abuse = abuse;
        }

        static final Set<String> SUBSCRIPTIONS =
                Set.of(
                        "/topic/chat",
                        "/user/queue/events",
                        "/user/queue/errors",
                        "/user/queue/guild-chat");

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor stomp =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (stomp == null || stomp.getCommand() == null) {
                return message;
            }
            StompCommand command = stomp.getCommand();
            String destination = stomp.getDestination();
            boolean allowed =
                    switch (command) {
                        case SEND ->
                                destination != null
                                        && destination.startsWith("/app/")
                                        && stomp.getUser() != null;
                        case SUBSCRIBE ->
                                SUBSCRIPTIONS.contains(destination) && stomp.getUser() != null;
                        default -> true;
                    };
            if (!allowed) {
                Principal user = stomp.getUser();
                log.warn(
                        "Dropped STOMP {} to {} from player {}",
                        command,
                        destination,
                        user == null ? "?" : user.getName());
                if (user instanceof PlayerPrincipal player) {
                    abuse.suspectPlayer(
                            player.playerId(),
                            AbuseKind.STOMP_REFUSED,
                            "STOMP " + command,
                            String.valueOf(destination));
                }
                return null;
            }
            return message;
        }
    }
}
