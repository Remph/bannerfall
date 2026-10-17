package de.bannerfall.world;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * {@code PUT /api/admin/world-end} (v1.3, 02-game-rules §22): the end date; null = none, the world
 * runs until a side wins.
 */
public record WorldEndRequest(@Schema(nullable = true) Instant endsAt) {}
