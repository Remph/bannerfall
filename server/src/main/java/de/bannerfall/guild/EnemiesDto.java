package de.bannerfall.guild;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The enemies the leader of a guild has marked, as every member sees them (1.7, 02-game-rules
 * §16.3). Only marks that still count are listed: targets that are gone, and guild mates (a marked
 * player who joined the guild), are left out. The web app derives the red markers from these ids.
 *
 * @param max how many marks a guild may have
 */
public record EnemiesDto(
        List<EnemyGuildDto> guilds,
        List<EnemyPlayerDto> players,
        List<EnemyVillageDto> villages,
        int max) {

    /** A marked guild. */
    public record EnemyGuildDto(long id, String name, String tag) {}

    /**
     * A marked player.
     *
     * @param guildId his guild, null without one
     */
    public record EnemyPlayerDto(
            long id,
            String name,
            boolean npc,
            @Schema(nullable = true) Long guildId,
            @Schema(nullable = true) String guildTag) {}

    /** A marked village, with its owner as he is now. */
    public record EnemyVillageDto(
            long id, String name, int x, int y, long ownerId, String ownerName, boolean npc) {}

    public int size() {
        return guilds.size() + players.size() + villages.size();
    }
}
