package de.bannerfall.npc;

import jakarta.validation.constraints.NotNull;

/**
 * {@code GET/PUT /api/admin/npc-settings}: the difficulty the world's NPC lords play at. Picking
 * CUSTOM for the first time stores a copy of NORMAL as the CUSTOM values (09 §8).
 */
public record NpcSettingsDto(@NotNull NpcDifficulty difficulty) {}
