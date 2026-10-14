package de.bannerfall.npc;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/admin/npc-lords}. */
public record SpawnNpcLordRequest(@NotNull NpcPersonality personality) {}
