package de.bannerfall.solo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A change of the host's speed control; a missing value stays as it is.
 *
 * @param paused true pauses the world, false plays it again
 * @param speed the fast forward: 1 (normal), 2 or 4
 */
public record WorldClockRequest(
        @Schema(nullable = true) Boolean paused, @Schema(nullable = true) Integer speed) {}
