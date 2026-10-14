package de.bannerfall.achievement;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * One achievement in the player's own list ({@code GET /api/achievements}): earned or locked, with
 * the progress only he sees.
 *
 * @param level levels earned: 0 = locked, 1 for a one-time achievement that is earned, 1 … 3 for a
 *     levelled one
 * @param earnedAt when the highest level was earned; null while locked
 * @param thresholds the values of level 1, 2, 3; empty for a one-time achievement
 * @param progress the current value of a levelled achievement (points, villages, wins, …); null for
 *     a one-time achievement
 */
public record AchievementDto(
        String key,
        String category,
        boolean leveled,
        int level,
        @Schema(nullable = true) Instant earnedAt,
        List<Long> thresholds,
        @Schema(nullable = true) Long progress) {}
