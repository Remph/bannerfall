package de.bannerfall.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One village with the asking player's own movements on the way to it (M26, 04-api, Map): his
 * attacks and supports sent from his villages, the attacks and supports other players send to his
 * villages, and his units walking home to one of his villages (M36). The web draws markers on the
 * village's field from the counts.
 *
 * @param attacks attacks on the way to the village (his own and, for his own villages, foreign
 *     ones)
 * @param nobleAttacks how many of {@code attacks} are his own and carry nobles; foreign attacks
 *     never count here, they don't show their units (04-api, Troops)
 * @param supports supports on the way to the village
 * @param returns the player's own units walking home to the village (after a battle, a cancelled
 *     command, a withdrawn or sent-back support); only ever on his own villages
 * @param nextArriveAt the earliest arrival of the attacks and supports; null if there are only
 *     returns
 * @param nextReturnAt the earliest arrival of the returns; null if there are none
 */
public record MapMovementDto(
        long villageId,
        int x,
        int y,
        int attacks,
        int nobleAttacks,
        int supports,
        int returns,
        @Schema(nullable = true) Instant nextArriveAt,
        @Schema(nullable = true) Instant nextReturnAt) {}
