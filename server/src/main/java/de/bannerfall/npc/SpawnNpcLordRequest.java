package de.bannerfall.npc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/admin/npc-lords}.
 *
 * @param variant one of the personality's variants (09-npc-lords §16.8), {@code PLAIN} for its
 *     classic template, or null to draw one like at world start
 */
public record SpawnNpcLordRequest(
        @NotNull NpcPersonality personality, @Schema(nullable = true) String variant) {}
