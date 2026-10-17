package de.bannerfall.map;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One village on the map (04-api, Map). Barbarian villages have no owner ({@code playerId} and
 * {@code playerName} are null) and usually no name. The web app picks the colour from {@code
 * playerId} and {@code npc}: own village, other player, NPC lord or barbarian.
 *
 * @param loyalty rounded down (M11). Your own villages: now (it grows back by itself after nobles
 *     hit it). Other villages (M21, 02-game-rules §10): the last value you saw when you attacked
 *     them (see {@code loyaltySeenAt}), null if you never have
 * @param loyaltySeenAt when you saw {@code loyalty}; null for your own villages (it is live) and
 *     when {@code loyalty} is null
 * @param guildId the owner's guild (M15a), null for barbarian villages and players in no guild
 * @param guildTag its tag
 * @param nextBannerRollAt the asking player has used his banner roll against this village's side
 *     (v1.3, 02-game-rules §21.7: its owner, or the barbarian village itself): when it comes back.
 *     Null when the roll is free, for his own villages and while banners or drops are off; the same
 *     as the farm list's tag
 * @param keep a banner keep (v1.3, 02-game-rules §22), whoever owns it
 */
public record MapVillageDto(
        long id,
        int x,
        int y,
        @Schema(nullable = true) String name,
        @Schema(nullable = true) Long playerId,
        @Schema(nullable = true) String playerName,
        boolean npc,
        int points,
        @Schema(nullable = true) Integer loyalty,
        @Schema(nullable = true) Instant loyaltySeenAt,
        @Schema(nullable = true) Long guildId,
        @Schema(nullable = true) String guildTag,
        @Schema(nullable = true) Instant nextBannerRollAt,
        boolean keep) {}
