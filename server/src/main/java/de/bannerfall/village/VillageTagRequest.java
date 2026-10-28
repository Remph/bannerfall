package de.bannerfall.village;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/villages/{id}/tag} (1.7). An empty or blank tag removes it; otherwise it is
 * checked by {@link VillageTagService#normalise}.
 */
record VillageTagRequest(@NotNull String tag) {}
