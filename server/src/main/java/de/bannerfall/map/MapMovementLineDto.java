package de.bannerfall.map;

import de.bannerfall.movement.MovementKind;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One of the asking player's movements as a line on the map (v1.3, 04-api, Map): the same movements
 * as the markers ({@link MapMovementDto}), one by one, with both ends.
 *
 * @param own the player's own command or return; false for what other players send to his villages
 * @param nobles his own attack carrying nobles; never true for a foreign attack (its units stay
 *     hidden)
 * @param from where the movement started (the battle's village for a RETURN after a battle)
 * @param to where it arrives (the home village for a RETURN)
 * @param departAt when it left; null for other players' attacks, whose position stays hidden
 */
public record MapMovementLineDto(
        long id,
        MovementKind kind,
        boolean own,
        boolean nobles,
        VillageRefDto from,
        VillageRefDto to,
        @Schema(nullable = true) Instant departAt,
        Instant arriveAt) {}
