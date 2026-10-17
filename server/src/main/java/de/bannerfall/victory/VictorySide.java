package de.bannerfall.victory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One side of the world (02-game-rules §22) and what it holds.
 *
 * @param id the guild's or the player's id
 * @param tag the guild's tag, null for a player
 * @param npc an NPC lord (never for a guild)
 * @param villages the villages its members (or it) own
 * @param points the sum of their points
 * @param keeps the banner keeps among {@code villages}
 */
public record VictorySide(
        SideKind kind,
        long id,
        String name,
        @Schema(nullable = true) String tag,
        boolean npc,
        int villages,
        long points,
        int keeps) {

    /** Stored as the victory leader: {@code GUILD:12}, {@code PLAYER:5}. */
    public String key() {
        return kind + ":" + id;
    }
}
