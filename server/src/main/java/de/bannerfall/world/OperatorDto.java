package de.bannerfall.world;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/world/operator: who runs this world, for the privacy page (public, no login). Both come
 * from the host's settings ({@link WorldOperator}).
 *
 * @param name the host's name, possibly with a postal address; null if not set
 * @param contact how to reach the host, usually an email address; null if not set
 */
public record OperatorDto(
        @Schema(nullable = true) String name, @Schema(nullable = true) String contact) {}
