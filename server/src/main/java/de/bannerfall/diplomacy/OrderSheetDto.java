package de.bannerfall.diplomacy;

import de.bannerfall.npc.NpcOrderKind;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/diplomacy/orders/{villageId}} (2.1 S3, 12-solo-and-allies §8): the order sheet
 * for one target village.
 *
 * @param targetGuildTag the tag of the owner's guild; null without
 * @param kinds the orders the target takes, the sheet's first tab first
 * @param ownAttackArrivesAt the human's own attack on the target that lands first (scouts-only
 *     attacks don't count): "land together" times the allies' march to it; null without one
 * @param allies the human's allied lords, without the target's owner, by name
 */
public record OrderSheetDto(
        VillageRefDto target,
        @Schema(nullable = true) String targetGuildTag,
        List<NpcOrderKind> kinds,
        @Schema(nullable = true) Instant ownAttackArrivesAt,
        List<OrderAllyDto> allies) {}
