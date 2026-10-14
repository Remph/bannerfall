package de.bannerfall.movement;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * A movement arriving at a village (04-api): attacks, supports, own units returning. The units of
 * an attack stay hidden.
 *
 * @param sourcePlayerName the owner of the source village; null for a barbarian village
 * @param units null for someone else's ATTACK; missing types mean 0
 */
public record IncomingDto(
        long id,
        MovementKind kind,
        VillageRefDto sourceVillage,
        @Schema(nullable = true) String sourcePlayerName,
        Instant arriveAt,
        @Schema(nullable = true) Map<UnitType, Integer> units) {

    static IncomingDto of(Movement movement) {
        VillageRefDto source = VillageRefDto.of(movement.getSourceVillage());
        return new IncomingDto(
                movement.getId(),
                movement.getKind(),
                source,
                source.playerName(),
                movement.getArriveAt(),
                hidden(movement) ? null : movement.getUnits().toMap());
    }

    /**
     * The units of someone else's attack stay hidden; the owner's own attack (a later wave on a
     * village he conquered meanwhile, it arrives like a support) shows them.
     */
    private static boolean hidden(Movement movement) {
        if (movement.getKind() != MovementKind.ATTACK) {
            return false;
        }
        Player owner = movement.getTargetVillage().getPlayer();
        return owner == null || owner.getId() != movement.getOwnerPlayerId();
    }
}
