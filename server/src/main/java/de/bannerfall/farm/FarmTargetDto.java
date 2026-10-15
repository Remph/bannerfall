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
 * @param bannerChance a won attack with the template can roll a banner now (1.2.3, §21.7): drops
 *     are on, the roll is free, none of the player's attacks that could use it is on the way, no
 *     earlier entry of the list has the same pair (a player's villages share one), and the template
 *     can win one (not scouts alone, against barbarians at least {@code drops.minLoserPop}
 *     population). The entries "Send banner chances" sends.
 */
public record FarmTargetDto(
        long id,
        VillageRefDto target,
        Map<UnitType, Integer> units,
        double distance,
        long travelSeconds,
        @Schema(nullable = true) Instant protectedUntil,
        @Schema(nullable = true) Instant lastSentAt,
        @Schema(nullable = true) Instant nextBannerRollAt,
        boolean bannerChance) {}
