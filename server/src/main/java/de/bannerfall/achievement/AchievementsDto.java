package de.bannerfall.achievement;

import java.util.List;

/** {@code GET /api/achievements}: all achievements in the order of the list, earned or not. */
public record AchievementsDto(List<AchievementDto> achievements) {}
