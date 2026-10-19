package de.bannerfall.push;

import jakarta.validation.constraints.NotNull;

/** One switch of the Notifications page. */
public record PushKindDto(@NotNull PushKind kind, boolean enabled) {}
