package de.bannerfall.world;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * {@code PUT /api/admin/world-opening} (M14g): the time the pre-game ends; null = no time set, the
 * world waits.
 */
public record WorldOpeningRequest(@Schema(nullable = true) Instant opensAt) {}
