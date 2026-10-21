package de.bannerfall.farm;

import java.time.Instant;

/**
 * The result of the latest attack from a farm list's village at the entry's target that landed
 * (1.6, 02-game-rules §18): the last haul next to what the units can carry.
 *
 * @param at when it landed
 * @param won the attackers won the battle
 * @param loot what they took home, all resources together (0 when they lost)
 * @param full they carried all they could: the village had more (never when they lost or could
 *     carry nothing)
 */
public record FarmRaidDto(Instant at, boolean won, int loot, boolean full) {

    /** The entry's last raid, null before the first. */
    static FarmRaidDto of(FarmTarget entry) {
        if (entry.getLastRaidAt() == null) {
            return null;
        }
        int capacity = entry.getLastRaidCapacity();
        int loot = entry.getLastRaidLoot();
        return new FarmRaidDto(
                entry.getLastRaidAt(),
                entry.getLastRaidWon(),
                loot,
                entry.getLastRaidWon() && capacity > 0 && loot >= capacity);
    }
}
