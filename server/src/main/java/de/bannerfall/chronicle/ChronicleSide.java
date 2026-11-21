package de.bannerfall.chronicle;

import de.bannerfall.victory.CourtRefDto;
import de.bannerfall.victory.SideKind;
import de.bannerfall.victory.VictorySide;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A side of the world as the chronicle remembers it (02-game-rules §22.1): a guild or a single
 * player, with its name then.
 *
 * @param tag the guild's tag; null for a player
 * @param npc an NPC lord (never a guild)
 * @param court set when the guild is (or became) the court of a solo world
 */
public record ChronicleSide(
        SideKind kind,
        long id,
        String name,
        @Schema(nullable = true) String tag,
        boolean npc,
        @Schema(nullable = true) CourtRefDto court) {

    /** A guild without a court. */
    public static ChronicleSide guild(long id, String name, String tag) {
        return new ChronicleSide(SideKind.GUILD, id, name, tag, false, null);
    }

    /** The side of the victory check, without its numbers. */
    public static ChronicleSide of(VictorySide side) {
        return side == null
                ? null
                : new ChronicleSide(
                        side.kind(), side.id(), side.name(), side.tag(), side.npc(), side.court());
    }

    /** {@code GUILD:12}, {@code PLAYER:5}, like the victory leader. */
    public String key() {
        return kind + ":" + id;
    }
}
