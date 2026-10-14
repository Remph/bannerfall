package de.bannerfall.map;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The movement markers of the map (M26, M36, 02-game-rules §6): one plain SQL query, no entities,
 * like {@link MapQuery}. It reveals nothing the player can't already see at his rally points: his
 * own commands and returns, and what is on the way to his own villages (without the units of
 * foreign attacks).
 */
@Component
class MapMovementsQuery {

    /*
     * The two halves of the union each use an index (home village, target village), which a single
     * "owner = me or target owner = me" could not. A RETURN's target is its home village (02 §6),
     * so the first half finds the player's own returns, grouped on the village they walk home to
     * (M36); the second half leaves RETURNs out, so it can never add somebody else's. Left out as
     * well: the player's own attacks on a village he owns by now (they arrive like a support and
     * don't count as incoming attacks either, 02 §10).
     */
    private static final String MOVEMENTS_OF_PLAYER =
            """
            with relevant as (
                select m.id from movement m join village h on h.id = m.home_village_id
                where h.player_id = :me
                union
                select m.id from movement m join village t on t.id = m.target_village_id
                where t.player_id = :me and m.kind <> 'RETURN'
            )
            select t.id, t.x, t.y,
                   count(*) filter (where m.kind = 'ATTACK') as attacks,
                   count(*) filter (
                       where m.kind = 'ATTACK' and m.owner_player_id = :me and m.u_noble > 0
                   ) as noble_attacks,
                   count(*) filter (where m.kind = 'SUPPORT') as supports,
                   count(*) filter (where m.kind = 'RETURN') as returns,
                   min(m.arrive_at) filter (where m.kind <> 'RETURN') as next_arrive_at,
                   min(m.arrive_at) filter (where m.kind = 'RETURN') as next_return_at
            from relevant r
            join movement m on m.id = r.id
            join village t on t.id = m.target_village_id
            where not (m.kind = 'ATTACK' and m.owner_player_id = :me
                       and t.player_id is not distinct from :me)
            group by t.id, t.x, t.y
            order by t.id
            """;

    private final JdbcClient jdbc;

    MapMovementsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One entry per village that has at least one of the player's movements on the way to it,
     * returns included.
     */
    List<MapMovementDto> movementsOf(long me) {
        return jdbc.sql(MOVEMENTS_OF_PLAYER)
                .param("me", me)
                .query(
                        (rs, rowNum) ->
                                new MapMovementDto(
                                        rs.getLong("id"),
                                        rs.getInt("x"),
                                        rs.getInt("y"),
                                        rs.getInt("attacks"),
                                        rs.getInt("noble_attacks"),
                                        rs.getInt("supports"),
                                        rs.getInt("returns"),
                                        instant(rs.getTimestamp("next_arrive_at")),
                                        instant(rs.getTimestamp("next_return_at"))))
                .list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
