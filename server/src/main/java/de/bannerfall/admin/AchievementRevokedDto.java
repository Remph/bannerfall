package de.bannerfall.admin;

/** The answer of a revoke: {@code changed} is false if the achievement was revoked before. */
public record AchievementRevokedDto(long playerId, String achievement, boolean changed) {}
