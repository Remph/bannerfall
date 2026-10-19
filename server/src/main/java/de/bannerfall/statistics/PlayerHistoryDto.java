package de.bannerfall.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/players/{id}/history} (02-game-rules §24): the player's points, villages and rank
 * at the end of each day since the statistics started, oldest first; the last entry is today's, as
 * it is now.
 */
public record PlayerHistoryDto(Instant since, List<PlayerDay> days) {

    /** One day (the world's time zone). */
    public record PlayerDay(LocalDate day, int points, int villages, int rank) {}
}
