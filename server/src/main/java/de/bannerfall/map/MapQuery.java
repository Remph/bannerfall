package de.bannerfall.map;

import de.bannerfall.rules.Loyalty;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read side of the map: one plain SQL query per rectangle, no entities (03-data-model, JPA
 * notes: heavy reads use JdbcClient). The web app asks for 20 × 20 chunks.
 */
@Component
class MapQuery {

    /** Most fields one request may span in each direction (04-api). */
    static final int MAX_SIZE = 60;

    private static final String VILLAGES_IN_RECTANGLE =
            """
            select v.id, v.x, v.y, v.name, v.points, v.loyalty, v.loyalty_updated_at, v.player_id,
                   p.name as player_name, coalesce(p.npc, false) as npc,
                   t.id as guild_id, t.tag as guild_tag,
                   s.loyalty as seen_loyalty, s.seen_at as loyalty_seen_at
            from village v
            left join player p on p.id = v.player_id
            left join loyalty_sighting s on s.village_id = v.id and s.player_id = :me
            left join guild_member m on m.player_id = v.player_id
            left join guild t on t.id = m.guild_id
            where v.x >= :fromX and v.x < :toX and v.y >= :fromY and v.y < :toY
            order by v.y, v.x
            """;

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final Clock clock;

    MapQuery(JdbcClient jdbc, WorldService worldService, Clock clock) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * The villages on the fields {@code x … x+w−1} × {@code y … y+h−1}, ordered by row. The part of
     * the rectangle beyond the map edge simply has no villages. The loyalty of the asking player's
     * own villages is regenerated up to now on the fly (nothing is written: the map locks no
     * village); for all others it is the last value he saw, if any (02-game-rules §10, M21).
     */
    List<MapVillageDto> villagesIn(long me, int x, int y, int w, int h) {
        WorldSettings world = worldService.settings();
        int mapSize = world.mapSize();
        double regenPerHour = Loyalty.regenPerHour(world);
        Instant now = clock.instant();
        return jdbc.sql(VILLAGES_IN_RECTANGLE)
                .param("me", me)
                .param("fromX", x)
                .param("toX", end(x, w, mapSize))
                .param("fromY", y)
                .param("toY", end(y, h, mapSize))
                .query(
                        (rs, rowNum) -> {
                            Long ownerId = rs.getObject("player_id", Long.class);
                            boolean own = ownerId != null && ownerId == me;
                            Integer loyalty = null;
                            Instant seenAt = null;
                            if (own) {
                                loyalty =
                                        Loyalty.shown(
                                                Loyalty.regen(
                                                        rs.getDouble("loyalty"),
                                                        regenPerHour,
                                                        rs.getTimestamp("loyalty_updated_at")
                                                                .toInstant(),
                                                        now));
                            } else if (rs.getObject("seen_loyalty") != null) {
                                loyalty = rs.getInt("seen_loyalty");
                                seenAt = rs.getTimestamp("loyalty_seen_at").toInstant();
                            }
                            return new MapVillageDto(
                                    rs.getLong("id"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getString("name"),
                                    ownerId,
                                    rs.getString("player_name"),
                                    rs.getBoolean("npc"),
                                    rs.getInt("points"),
                                    loyalty,
                                    seenAt,
                                    rs.getObject("guild_id", Long.class),
                                    rs.getString("guild_tag"));
                        })
                .list();
    }

    /**
     * Exclusive end of a range, cut at the map edge. Computed in {@code long}: {@code start + size}
     * would overflow for a start near {@code Integer.MAX_VALUE} (such a range is just off the map).
     */
    static int end(int start, int size, int mapSize) {
        return (int) Math.min((long) start + size, mapSize);
    }
}
