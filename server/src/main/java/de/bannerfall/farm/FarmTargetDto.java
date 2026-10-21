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
 * @param template the player's template the entry uses (1.4), null for its own units; {@code units}
 *     are that template's then
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
 * @param note the player's own note on the target village (1.4, 02-game-rules §23), null without
 * @param morale the attack's morale with today's points (1.6, 02-game-rules §8.2), like the command
 *     preview's: null at full strength and at barbarians
 * @param haulCapacity what the units can carry home at most, the list village's Plunder banner
 *     included (1.6): the most one attack can bring
 * @param lastRaid the latest attack from the village at the target that landed (1.6), null before
 *     the first
 */
public record FarmTargetDto(
        long id,
        VillageRefDto target,
        Map<UnitType, Integer> units,
        @Schema(nullable = true) FarmTemplateLetter template,
        double distance,
        long travelSeconds,
        @Schema(nullable = true) Instant protectedUntil,
        @Schema(nullable = true) Instant lastSentAt,
        @Schema(nullable = true) Instant nextBannerRollAt,
        boolean bannerChance,
        @Schema(nullable = true) String note,
        @Schema(nullable = true) Double morale,
        int haulCapacity,
        @Schema(nullable = true) FarmRaidDto lastRaid) {}
