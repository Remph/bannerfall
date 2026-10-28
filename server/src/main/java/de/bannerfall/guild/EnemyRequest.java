package de.bannerfall.guild;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /api/guilds/mine/enemies}: what to mark. */
public record EnemyRequest(@NotNull EnemyKind kind, @NotNull Long targetId) {}
