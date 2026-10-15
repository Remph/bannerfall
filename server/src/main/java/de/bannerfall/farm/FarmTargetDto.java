package de.bannerfall.farm;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * One line of the farm list (04-api <i>Farm helper</i>).
 *
 * @param units the template: only the types that are sent
 * @param distance in fields, from the list's village
 * @param travelSeconds with the template's slowest unit
 * @param protectedUntil the target player's beginner protection (the client compares it with the
 *     time of arrival); null for barbarians and players without
 * @param lastSentAt when the helper last sent the template, null if never
 * @param nextBannerRollAt the player has used the banner roll against this target (02-game-rules
 *     §21.7: per player, or per barbarian village) and may roll again from then on; null when a won
 *     battle would roll now, or banners or drops are switched off
 */
public record FarmTargetDto(
        long id,
        VillageRefDto target,
        Map<UnitType, Integer> units,
        double distance,
        long travelSeconds,
        @Schema(nullable = true) Instant protectedUntil,
        @Schema(nullable = true) Instant lastSentAt,
        @Schema(nullable = true) Instant nextBannerRollAt) {}
