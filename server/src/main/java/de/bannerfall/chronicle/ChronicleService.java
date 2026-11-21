package de.bannerfall.chronicle;

import de.bannerfall.chronicle.ChronicleFrames.Frame;
import de.bannerfall.chronicle.ChronicleFrames.Owner;
import de.bannerfall.chronicle.ChronicleRules.Entry;
import de.bannerfall.chronicle.ChronicleRules.Stored;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.victory.SideKind;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the chronicle of an ended world (2.1 I1, 02-game-rules §32): the turning points ({@link
 * ChronicleRules}) and the replay map from the daily frames, with the end as the last frame.
 */
@Service
public class ChronicleService {

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final ChronicleFrames frames;
    private final Chronicle chronicle;

    ChronicleService(
            JdbcClient jdbc,
            WorldService worldService,
            ChronicleFrames frames,
            Chronicle chronicle) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.frames = frames;
        this.chronicle = chronicle;
    }

    /**
     * The chronicle as {@code viewerId} reads it (his moments marked).
     *
     * @throws ApiException {@code 409 WORLD_NOT_ENDED} while the world runs
     */
    @Transactional(readOnly = true)
    public ChronicleDto read(long viewerId) {
        Instant endedAt = worldService.endedAt();
        if (endedAt == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_ENDED,
                    "The chronicle opens when the world has ended");
        }
        WorldSettings world = worldService.settings();
        boolean guilds = world.guildsEnabled();
        int endDay = chronicle.dayOf(endedAt);
        List<Frame> all = new ArrayList<>(frames.stored());
        if (all.isEmpty() || all.getLast().day() < endDay) {
            // a world that ended before the chronicle existed, or an end without its frame: the
            // world stands still at its end, so it is that frame
            all.add(frames.now(endDay, endedAt));
        }
        Long viewerGuild =
                jdbc.sql("select guild_id from guild_member where player_id = :player")
                        .param("player", viewerId)
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        Viewer viewer = new Viewer(viewerId, viewerGuild);
        List<ChronicleDto.Entry> entries =
                ChronicleRules.pick(events(), all, guilds).stream()
                        .map(entry -> dto(entry, viewer))
                        .toList();
        return new ChronicleDto(endedAt, endDay, guilds, entries, map(world, all, guilds, viewer));
    }

    private List<Stored> events() {
        return jdbc.sql("select id, at, day, kind, data from chronicle_event order by at, id")
                .query(
                        (rs, row) ->
                                new Stored(
                                        rs.getLong("id"),
                                        rs.getTimestamp("at").toInstant(),
                                        rs.getInt("day"),
                                        ChronicleKind.valueOf(rs.getString("kind")),
                                        Chronicle.JSON.readValue(
                                                rs.getString("data"), ChronicleFacts.class)))
                .list();
    }

    private static ChronicleDto.Entry dto(Entry entry, Viewer viewer) {
        ChronicleFacts f = entry.facts();
        boolean mine =
                viewer.is(f.from())
                        || viewer.is(f.to())
                        || viewer.is(f.lord())
                        || viewer.is(f.player())
                        || viewer.is(f.side())
                        || viewer.is(f.other());
        return new ChronicleDto.Entry(entry.day(), entry.at(), entry.kind(), f, mine);
    }

    /** The replay map: the villages, the sides and each frame's changes. */
    private ChronicleDto.MapDto map(
            WorldSettings world, List<Frame> all, boolean guilds, Viewer viewer) {
        Map<Long, ChronicleDto.Dot> places = new HashMap<>();
        jdbc.sql("select id, x, y, keep from village")
                .query(
                        (rs, row) ->
                                places.put(
                                        rs.getLong("id"),
                                        new ChronicleDto.Dot(
                                                rs.getInt("x"),
                                                rs.getInt("y"),
                                                rs.getBoolean("keep"))))
                .list();
        Map<Long, Integer> villageIndex = new LinkedHashMap<>();
        List<ChronicleDto.Dot> villages = new ArrayList<>();
        Map<String, Integer> sideIndex = new LinkedHashMap<>();
        List<ChronicleDto.MapSide> sides = new ArrayList<>();
        Map<Long, Integer> state = new HashMap<>(); // village index -> code
        List<ChronicleDto.FrameDto> out = new ArrayList<>();
        for (Frame frame : all) {
            Map<Long, Owner> owners = new HashMap<>();
            frame.players().forEach(owner -> owners.put(owner.id(), owner));
            Map<Long, Integer> next = new HashMap<>();
            for (long[] pair : frame.owners()) {
                ChronicleDto.Dot dot = places.get(pair[0]);
                if (dot == null) {
                    continue; // a village deleted since: no field to draw it on
                }
                int index =
                        villageIndex.computeIfAbsent(
                                pair[0],
                                id -> {
                                    villages.add(dot);
                                    return villages.size() - 1;
                                });
                Owner owner = pair[1] == 0 ? null : owners.get(pair[1]);
                int code = 0;
                if (owner != null) {
                    ChronicleSide side = ChronicleRules.sideOf(owner, guilds);
                    code =
                            sideIndex.computeIfAbsent(
                                            side.key(),
                                            key -> {
                                                sides.add(
                                                        new ChronicleDto.MapSide(
                                                                side.kind(),
                                                                side.id(),
                                                                side.name(),
                                                                side.tag(),
                                                                side.npc(),
                                                                viewer.is(side)));
                                                return sides.size() - 1;
                                            })
                                    + 1;
                }
                next.put((long) index, code);
            }
            List<Integer> changes = new ArrayList<>();
            next.forEach(
                    (index, code) -> {
                        if (!Objects.equals(state.get(index), code)) {
                            changes.add(index.intValue());
                            changes.add(code);
                        }
                    });
            state.keySet().stream()
                    .filter(index -> !next.containsKey(index) && state.get(index) != -1)
                    .forEach(
                            index -> {
                                changes.add(index.intValue());
                                changes.add(-1);
                                next.put(index, -1);
                            });
            state.putAll(next);
            out.add(new ChronicleDto.FrameDto(frame.day(), frame.at(), sorted(changes)));
        }
        return new ChronicleDto.MapDto(world.mapSize(), villages, sides, out);
    }

    /** The pairs by village index, so a frame reads the same every time. */
    private static List<Integer> sorted(List<Integer> pairs) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i += 2) {
            list.add(new int[] {pairs.get(i), pairs.get(i + 1)});
        }
        list.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<Integer> flat = new ArrayList<>(pairs.size());
        list.forEach(
                pair -> {
                    flat.add(pair[0]);
                    flat.add(pair[1]);
                });
        return flat;
    }

    /** The reader and his guild. */
    private record Viewer(long id, Long guildId) {

        boolean is(ChroniclePlayer player) {
            return player != null
                    && (player.id() == id || (guildId != null && guildId.equals(player.guildId())));
        }

        boolean is(ChronicleSide side) {
            if (side == null) {
                return false;
            }
            return side.kind() == SideKind.PLAYER
                    ? side.id() == id
                    : guildId != null && side.id() == guildId;
        }
    }
}
