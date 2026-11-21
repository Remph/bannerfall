package de.bannerfall.diplomacy;

import de.bannerfall.message.LordLetter;
import de.bannerfall.npc.NpcOrderKind;
import de.bannerfall.npc.NpcOrderState;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The human's current order to one ally (2.1 S3, 12-solo-and-allies §8), on the diplomacy page and
 * the order sheet.
 *
 * @param target the village it is about; null if the village is gone
 * @param answer the kind of the lord's last letter about it; null before it answered
 * @param departAt a planned "land together" march leaves then (game time); null otherwise
 * @param arriveAt its troops or merchants arrive then (game time); null otherwise
 */
public record OrderDto(
        NpcOrderKind kind,
        @Schema(nullable = true) VillageRefDto target,
        NpcOrderState state,
        boolean landTogether,
        @Schema(nullable = true) LordLetter.Kind answer,
        Instant givenAt,
        @Schema(nullable = true) Instant departAt,
        @Schema(nullable = true) Instant arriveAt) {}
