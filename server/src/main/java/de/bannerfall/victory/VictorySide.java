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
 * @param court set when the side is the court of a solo world (2.1 S2b, 12-solo-and-allies §6)
 */
public record VictorySide(
        SideKind kind,
        long id,
        String name,
        @Schema(nullable = true) String tag,
        boolean npc,
        int villages,
        long points,
        int keeps,
        @Schema(nullable = true) CourtRefDto court) {

    /** A side that is no court. */
    public VictorySide(
            SideKind kind,
            long id,
            String name,
            String tag,
            boolean npc,
            int villages,
            long points,
            int keeps) {
        this(kind, id, name, tag, npc, villages, points, keeps, null);
    }

    /** The same side marked as the court. */
    public VictorySide asCourt(CourtRefDto court) {
        return new VictorySide(kind, id, name, tag, npc, villages, points, keeps, court);
    }

    /** Stored as the victory leader: {@code GUILD:12}, {@code PLAYER:5}. */
    public String key() {
        return kind + ":" + id;
    }
}
