package de.bannerfall.movement;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * POST /api/villages/{id}/commands/preview (04-api): the command as it would be if it were sent
 * now, and every rule it breaks.
 *
 * @param distance in fields
 * @param travelSeconds null if the command has no units (an empty army has no speed)
 * @param arriveAt null like {@code travelSeconds}
 * @param errors in the order {@code send} checks them (it answers with the first); empty = the
 *     command can be sent
 * @param morale the attacker's morale if the attack arrived with today's points (1.6, 02-game-rules
 *     §8.2), e.g. {@code 0.48}; null at full strength, for a support and against a barbarian
 *     village. The battle reads the points at the arrival.
 */
public record CommandPreviewDto(
        VillageRefDto targetVillage,
        double distance,
        @Schema(nullable = true) Long travelSeconds,
        @Schema(nullable = true) Instant arriveAt,
        List<ErrorCode> errors,
        @Schema(nullable = true) Double morale) {}
