package de.bannerfall.player;

import java.security.Principal;

/**
 * The user of a STOMP connection (01-architecture §4.10): the player, named by his id, so the
 * server can send to {@code /user/{playerId}/queue/events} without looking up account names. Set
 * once at the WebSocket handshake ({@code WebSocketConfig}); {@code @MessageMapping} methods take
 * it as a parameter.
 */
public record PlayerPrincipal(long playerId) implements Principal {

    @Override
    public String getName() {
        return Long.toString(playerId);
    }
}
