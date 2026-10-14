package de.bannerfall.building;

import de.bannerfall.gamedata.BuildingType;
import java.time.Instant;

/**
 * An order of the build queue (04-api): the answer to {@code POST build-orders} and an entry of
 * {@code VillageDto.buildQueue}.
 *
 * @param targetLevel the level the building has when the order is done
 * @param startAt when it starts (in the past for the running order, in the future for the next
 *     ones)
 * @param endAt when it is done
 */
public record BuildOrderDto(
        long id, BuildingType building, int targetLevel, Instant startAt, Instant endAt) {

    public static BuildOrderDto of(BuildOrder order) {
        return new BuildOrderDto(
                order.getId(),
                order.getBuilding(),
                order.getTargetLevel(),
                order.getStartAt(),
                order.getEndAt());
    }
}
