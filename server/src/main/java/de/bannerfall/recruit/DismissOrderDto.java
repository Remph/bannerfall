package de.bannerfall.recruit;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;

/**
 * An order of a dismiss queue (04-api): the answer to {@code POST dismiss-orders} and an entry of
 * {@code dismissQueue} in {@code GET recruitment}.
 *
 * @param completedCount units already dismissed; the next one goes at {@code startAt +
 *     (completedCount + 1) × secondsPerUnit}
 * @param startAt when the first unit started (in the past for the running order)
 * @param endAt when the last unit is gone
 */
public record DismissOrderDto(
        long id,
        BuildingType building,
        UnitType unit,
        int count,
        int completedCount,
        int secondsPerUnit,
        Instant startAt,
        Instant endAt) {

    public static DismissOrderDto of(DismissOrder order) {
        return new DismissOrderDto(
                order.getId(),
                order.getBuilding(),
                order.getUnit(),
                order.getCount(),
                order.getCompletedCount(),
                order.getSecondsPerUnit(),
                order.getStartAt(),
                order.getEndAt());
    }
}
