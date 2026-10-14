package de.bannerfall.recruit;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;

/**
 * An order of a recruit queue (04-api): the answer to {@code POST recruit-orders} and an entry of
 * the queues in {@code GET recruitment}.
 *
 * @param completedCount units already done and at home; the next one is done at {@code startAt +
 *     (completedCount + 1) × secondsPerUnit}
 * @param startAt when the first unit started (in the past for the running order)
 * @param endAt when the last unit is done
 */
public record RecruitOrderDto(
        long id,
        BuildingType building,
        UnitType unit,
        int count,
        int completedCount,
        int secondsPerUnit,
        Instant startAt,
        Instant endAt) {

    public static RecruitOrderDto of(RecruitOrder order) {
        return new RecruitOrderDto(
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
