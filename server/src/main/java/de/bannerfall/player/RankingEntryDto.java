package de.bannerfall.player;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line of the ranking (04-api, {@code GET /api/ranking}).
 *
 * @param guildId the player's guild (M15a), null if he is in none
 * @param guildTag its tag, shown in brackets before the name
 */
public record RankingEntryDto(
        long rank,
        long playerId,
        String name,
        boolean npc,
        int points,
        int villageCount,
        @Schema(nullable = true) Long guildId,
        @Schema(nullable = true) String guildTag) {}
