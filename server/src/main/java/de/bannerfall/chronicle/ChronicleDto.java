package de.bannerfall.chronicle;

import de.bannerfall.victory.SideKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The chronicle of an ended world (2.1 I1, 02-game-rules §32, 04-api *Chronicle*).
 *
 * @param endedAt when the world ended
 * @param days the world's last day (the day it ended on)
 * @param guilds whether the sides are guilds (guilds on) or single players
 * @param entries the turning points, the first first
 * @param map the replay map
 */
public record ChronicleDto(
        Instant endedAt, int days, boolean guilds, List<Entry> entries, MapDto map) {

    /**
     * One turning point.
     *
     * @param day the game day it happened on (0 before the opening)
     * @param at when it happened (real time)
     * @param mine it is about the reader or his guild
     */
    @Schema(name = "ChronicleEntry")
    public record Entry(
            int day, Instant at, ChronicleKind kind, ChronicleFacts facts, boolean mine) {}

    /**
     * The replay map.
     *
     * @param size the map's width and height in fields
     * @param villages every village that appears in a frame
     * @param sides every side that owned a village in a frame
     * @param frames the first day first; the first frame names every village, the others only what
     *     changed
     */
    @Schema(name = "ChronicleMap")
    public record MapDto(
            int size, List<Dot> villages, List<MapSide> sides, List<FrameDto> frames) {}

    /** A village on the replay map. */
    @Schema(name = "ChronicleDot")
    public record Dot(int x, int y, boolean keep) {}

    /**
     * An owner on the replay map.
     *
     * @param tag the guild's tag; null for a player
     * @param npc an NPC lord (never a guild)
     * @param mine the reader or his guild
     */
    @Schema(name = "ChronicleMapSide")
    public record MapSide(
            SideKind kind,
            long id,
            String name,
            @Schema(nullable = true) String tag,
            boolean npc,
            boolean mine) {}

    /**
     * One day of the replay.
     *
     * @param day after how many days (0 = the opening)
     * @param changes pairs of a village's index in {@code villages} and its owner then: the side's
     *     index in {@code sides} + 1, 0 for barbarians, -1 for a village that is gone
     */
    @Schema(name = "ChronicleFrame")
    public record FrameDto(int day, Instant at, List<Integer> changes) {}
}
