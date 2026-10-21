package de.bannerfall.farm;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/villages/{id}/farm-targets/copy (1.6.1): the player's village that gets the entries.
 */
public record FarmCopyRequest(@NotNull Long toVillageId) {}
