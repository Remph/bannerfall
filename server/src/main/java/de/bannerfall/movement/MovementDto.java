package de.bannerfall.movement;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * A movement of the player's own units (04-api): sent, cancelled, withdrawn, or in the outgoing
 * list.
 *
 * @param units missing types mean 0
 * @param catapultTarget only for an attack with catapults
 * @param loot only a RETURN carries some
 * @param cancellableUntil the owner may cancel before this (exclusive); null for a RETURN
 * @param notifyReturn the owner gets told when the units are home (1.6, the bell): alone or with
 *     the other attacks of a farm list run or a train
 */
public record MovementDto(
        long id,
        MovementKind kind,
        VillageRefDto sourceVillage,
        VillageRefDto targetVillage,
        VillageRefDto homeVillage,
        Map<UnitType, Integer> units,
        @Schema(nullable = true) BuildingType catapultTarget,
        ResourceAmounts loot,
        Instant departAt,
        Instant arriveAt,
        @Schema(nullable = true) Instant cancellableUntil,
        boolean notifyReturn) {

    static MovementDto of(Movement movement, int cancelWindowSeconds) {
        return new MovementDto(
                movement.getId(),
                movement.getKind(),
                VillageRefDto.of(movement.getSourceVillage()),
                VillageRefDto.of(movement.getTargetVillage()),
                VillageRefDto.of(movement.getHomeVillage()),
                movement.getUnits().toMap(),
                movement.getCatapultTarget(),
                movement.getLoot(),
                movement.getDepartAt(),
                movement.getArriveAt(),
                movement.cancellableUntil(cancelWindowSeconds),
                movement.getReturnWatchId() != null);
    }
}
