package de.bannerfall.diplomacy;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.message.LordLetter;
import de.bannerfall.npc.NpcHelpKind;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * An open help request of an allied lord (2.1 S4, 12-solo-and-allies §9), for the request strip and
 * sheet.
 *
 * @param village the lord's village that needs it
 * @param defenders the defenders a request for support asks for; 0 for resources
 * @param goal what a shortage holds up ({@code NOBLE} or {@code WALL}); null for support
 * @param resources what a request for resources still needs (asked less delivered); zero for
 *     support
 * @param createdAt when the lord asked (game time)
 * @param deadline when the attack lands, or when the request for resources ends (game time)
 * @param letter the lord's letter, as in Messages; null if it is gone (deleted player)
 * @param from the village the sheet sends from: for support the nearest of the player's villages
 *     with defenders at home whose march lands before {@code deadline}, else the nearest one with
 *     defenders at home; for resources the nearest one with a free merchant. Null when none can
 *     send
 * @param fill the defenders the sheet fills in from {@code from}: the fastest types first, at most
 *     {@code defenders}, only types that land in time while any does; empty for resources or when
 *     no village can send
 * @param seconds the travel time from {@code from}: of the slowest unit of {@code fill} for
 *     support, of the merchants for resources; null without a way to send
 * @param answered trust for an answer in time
 * @param declined trust for "Not this time" (0 when nothing the player sends could land in time)
 * @param ignored trust for no answer until the deadline (0 then too)
 */
public record HelpRequestDto(
        long id,
        NpcHelpKind kind,
        long lordId,
        String lordName,
        VillageRefDto village,
        int defenders,
        @Schema(nullable = true) String goal,
        ResourceAmounts resources,
        Instant createdAt,
        Instant deadline,
        @Schema(nullable = true) LordLetter letter,
        @Schema(nullable = true) VillageRefDto from,
        Map<UnitType, Integer> fill,
        @Schema(nullable = true) Long seconds,
        int answered,
        int declined,
        int ignored) {}
