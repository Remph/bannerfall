package de.bannerfall.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/statistics/world} (02-game-rules §24): the world at the end of each day, oldest
 * first, and the points of today's leading players over the same days. The last day of each list is
 * today, as it is now.
 *
 * @param leaders the {@link StatisticsService#LEADERS} players with the most points now, best first
 */
public record WorldStatisticsDto(Instant since, List<WorldDay> days, List<LeaderLine> leaders) {

    /**
     * @param players ranked players (humans whose account isn't deleted, and NPC lords)
     * @param points the ranked players' points together
     */
    public record WorldDay(
            LocalDate day, int players, int villages, int barbarianVillages, long points) {}

    /** A leading player; a day before he joined is missing from {@code days}. */
    public record LeaderLine(long playerId, String name, boolean npc, List<LeaderPoints> days) {}

    public record LeaderPoints(LocalDate day, int points) {}
}
