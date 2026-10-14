package de.bannerfall.achievement;

import java.time.Instant;

/**
 * An achievement a player has earned, as the profile shows it to everybody (02-game-rules §19): the
 * highest {@code level} reached (1 for a one-time achievement, which is not {@code leveled}) and
 * when it was earned.
 */
public record EarnedAchievementDto(
        String key, String category, boolean leveled, int level, Instant earnedAt) {}
