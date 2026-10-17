package de.bannerfall.npc;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One NPC lord as the admin sees it (04-api *Admin*). The personality is only ever sent here, never
 * to players (02-game-rules §11b).
 *
 * @param lastThinkAt when its last round ran; null before the first
 * @param nextThinkAt when its next round is due; null for a lord that is out of the game
 * @param braked the rubber band keeps it from conquering (09 §8)
 * @param boosted the rubber band raises its production
 * @param guildId / guildName / guildTag its guild (09 §13), null for none
 * @param guildByAdmin the admin placed it (§13.7): the forming leaves it alone
 */
public record NpcLordDto(
        long playerId,
        String name,
        NpcPersonality personality,
        String template,
        int points,
        int villageCount,
        @Schema(nullable = true) Instant lastThinkAt,
        @Schema(nullable = true) Instant nextThinkAt,
        boolean braked,
        boolean boosted,
        double productionFactor,
        @Schema(nullable = true) Long guildId,
        @Schema(nullable = true) String guildName,
        @Schema(nullable = true) String guildTag,
        boolean guildByAdmin) {}
