package de.bannerfall.world;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * GET /api/world/status: what the login page and the landing page bannerfall.de may know without a
 * login (M14g, M19, M28): the name, whether the world is open, when it opens, two player counts and
 * the version of the server. Only numbers, never names.
 *
 * @param opensAt null if the host has not set a time (and once the world is open)
 * @param registeredPlayers the human players with an account, like {@code WorldDto}
 * @param onlinePlayers the players with an open game connection right now (a player with two tabs
 *     counts once)
 * @param version the release this server runs, e.g. {@code 1.1.0} (or {@code 0.0.1-SNAPSHOT} for a
 *     development build)
 */
public record PublicWorldDto(
        String name,
        WorldPhase phase,
        @Schema(nullable = true) Instant opensAt,
        long registeredPlayers,
        int onlinePlayers,
        String version) {}
