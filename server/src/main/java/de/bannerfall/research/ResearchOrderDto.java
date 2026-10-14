package de.bannerfall.research;

import de.bannerfall.gamedata.UnitType;
import java.time.Instant;

/**
 * A running research (04-api): the answer to {@code POST research-orders} and the entries of {@code
 * GET smithy}.
 *
 * @param villageId the village where it runs (its smithy is busy until {@code endAt})
 * @param level the level the unit reaches
 */
public record ResearchOrderDto(
        long id, long villageId, UnitType unit, int level, Instant startAt, Instant endAt) {

    static ResearchOrderDto of(ResearchOrder order) {
        return new ResearchOrderDto(
                order.getId(),
                order.getVillageId(),
                order.getUnit(),
                order.getLevel(),
                order.getStartAt(),
                order.getEndAt());
    }
}
