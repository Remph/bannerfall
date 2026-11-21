package de.bannerfall.solo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The host's speed control (2.1, 12-solo-and-allies §3).
 *
 * @param soloMode whether this is a solo world; only a solo world sleeps, pauses and speeds up
 * @param asleepSince when the world fell asleep or was paused, null while it runs
 * @param paused the host paused it: it stays asleep until he presses play
 * @param speed the fast forward: 1 (normal), 2 or 4
 */
public record WorldClockDto(
        boolean soloMode,
        @Schema(nullable = true) Instant asleepSince,
        boolean paused,
        int speed) {}
