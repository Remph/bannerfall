package de.bannerfall.village;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The player's tag on his village after {@code PUT /api/villages/{id}/tag} (1.7).
 *
 * @param tag capital letters and digits; null once the tag is removed
 */
public record VillageTagDto(long villageId, @Schema(nullable = true) String tag) {}
