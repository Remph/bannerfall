package de.bannerfall.guild;

import jakarta.validation.constraints.Positive;

/** {@code POST /api/guilds/mine/leader}: who gets the lead. */
public record LeaderRequest(@Positive long playerId) {}
