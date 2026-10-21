package de.bannerfall.movement;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * POST /api/villages/{id}/commands/train/preview (04-api): the train as it would be if it were sent
 * now. All its attacks walk at the pace of the slowest unit in the whole train (02-game-rules
 * §6.1), so they share one travel time and one arrival.
 *
 * @param distance in fields
 * @param travelSeconds null if no attack has units
 * @param arriveAt null like {@code travelSeconds}
 * @param attacks one entry per attack of the request, in its order
 * @param morale the attacks' morale with today's points (1.6, 02-game-rules §8.2), like {@link
 *     CommandPreviewDto#morale()}
 */
public record TrainPreviewDto(
        VillageRefDto targetVillage,
        double distance,
        @Schema(nullable = true) Long travelSeconds,
        @Schema(nullable = true) Instant arriveAt,
        List<TrainAttackPreviewDto> attacks,
        @Schema(nullable = true) Double morale) {

    /**
     * @param errors every rule this attack breaks, like {@link CommandPreviewDto#errors()}; its
     *     units are checked against what the attacks before it leave at home. Empty for every
     *     attack = the train can be sent.
     */
    public record TrainAttackPreviewDto(List<ErrorCode> errors) {}
}
