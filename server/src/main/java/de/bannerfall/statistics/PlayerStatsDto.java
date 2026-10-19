package de.bannerfall.statistics;

import java.time.Instant;
import java.util.Map;

/**
 * A player's statistics on his profile (02-game-rules §24, {@code GET /api/players/{id}}). Kills
 * and losses in population, loot in resources.
 *
 * @param since when the world started counting (the update to 1.5, or the world's setup)
 */
public record PlayerStatsDto(
        long attacksWon,
        long attacksLost,
        long defencesWon,
        long defencesLost,
        long killedAttacking,
        long killedDefending,
        long unitsLost,
        long lootTaken,
        long lootLost,
        long villagesConquered,
        long villagesLost,
        long unitsRecruited,
        long buildingLevels,
        Instant since) {

    static PlayerStatsDto of(Map<Stat, Long> totals, Instant since) {
        return new PlayerStatsDto(
                totals.getOrDefault(Stat.ATTACKS_WON, 0L),
                totals.getOrDefault(Stat.ATTACKS_LOST, 0L),
                totals.getOrDefault(Stat.DEFENCES_WON, 0L),
                totals.getOrDefault(Stat.DEFENCES_LOST, 0L),
                totals.getOrDefault(Stat.KILLED_ATTACKING, 0L),
                totals.getOrDefault(Stat.KILLED_DEFENDING, 0L),
                totals.getOrDefault(Stat.UNITS_LOST, 0L),
                totals.getOrDefault(Stat.LOOT_TAKEN, 0L),
                totals.getOrDefault(Stat.LOOT_LOST, 0L),
                totals.getOrDefault(Stat.VILLAGES_CONQUERED, 0L),
                totals.getOrDefault(Stat.VILLAGES_LOST, 0L),
                totals.getOrDefault(Stat.UNITS_RECRUITED, 0L),
                totals.getOrDefault(Stat.BUILDING_LEVELS, 0L),
                since);
    }
}
