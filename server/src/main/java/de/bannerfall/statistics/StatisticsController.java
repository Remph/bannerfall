package de.bannerfall.statistics;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.player.PlayerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/players/{id}/history} and {@code GET /api/statistics/world} (04-api, Statistics;
 * 02-game-rules §24). Every player sees them, like the ranking.
 */
@RestController
class StatisticsController {

    private final StatisticsService statistics;
    private final PlayerRepository players;

    StatisticsController(StatisticsService statistics, PlayerRepository players) {
        this.statistics = statistics;
        this.players = players;
    }

    /** {@code 404 PLAYER_NOT_FOUND} for an unknown or deleted player, like the profile. */
    @GetMapping("/api/players/{id}/history")
    PlayerHistoryDto playerHistory(@PathVariable long id) {
        if (players.findRank(id).isEmpty()) {
            throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
        }
        return statistics.history(id);
    }

    @GetMapping("/api/statistics/world")
    WorldStatisticsDto worldStatistics() {
        return statistics.world();
    }
}
