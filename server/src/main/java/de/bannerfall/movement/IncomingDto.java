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
 * @param departAt when it set off (the web's progress bars, 1.3): for the village owner's own
 *     movements and for every support, whose line the map shows too; null for someone else's
 *     attack, whose start stays hidden as on the map (M26)
 * @param units null for someone else's ATTACK; missing types mean 0
 */
public record IncomingDto(
        long id,
        MovementKind kind,
        VillageRefDto sourceVillage,
        @Schema(nullable = true) String sourcePlayerName,
        @Schema(nullable = true) Instant departAt,
        Instant arriveAt,
        @Schema(nullable = true) Map<UnitType, Integer> units) {

    static IncomingDto of(Movement movement) {
        VillageRefDto source = VillageRefDto.of(movement.getSourceVillage());
        return new IncomingDto(
                movement.getId(),
                movement.getKind(),
                source,
                source.playerName(),
                startShown(movement) ? movement.getDepartAt() : null,
                movement.getArriveAt(),
                hidden(movement) ? null : movement.getUnits().toMap());
    }

    /** The target village's owner sent it: own troops coming home, own supports and later waves. */
    private static boolean own(Movement movement) {
        Player owner = movement.getTargetVillage().getPlayer();
        return owner != null && owner.getId() == movement.getOwnerPlayerId();
    }

    /**
     * Own movements and every support show when they set off; someone else's attack does not (with
     * the distance its start would give away how fast, so which units, it is).
     */
    private static boolean startShown(Movement movement) {
        return own(movement) || movement.getKind() == MovementKind.SUPPORT;
    }

    /**
     * The units of someone else's attack stay hidden; the owner's own attack (a later wave on a
     * village he conquered meanwhile, it arrives like a support) shows them.
     */
    private static boolean hidden(Movement movement) {
        return movement.getKind() == MovementKind.ATTACK && !own(movement);
    }
}
