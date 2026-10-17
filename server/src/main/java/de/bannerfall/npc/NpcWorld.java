package de.bannerfall.npc;

import de.bannerfall.common.Coordinates;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * What an NPC lord may see of the world (09-npc-lords §4), read with plain SQL like the map: every
 * village with its owner, points and loyalty (the map), the owners' points and village counts (the
 * ranking), the incoming attacks a human would see and the lord's own attacks on the way. Nothing
 * of a foreign village's units, resources or buildings.
 */
@Component
class NpcWorld {

    /**
     * One village as the map shows it, with what the ranking says about its owner.
     *
     * @param playerId null for a barbarian village
     * @param loyalty now, as the map shows it
     * @param ownerPoints / ownerVillages / ownerProtectedUntil 0 / 0 / null for barbarians
     */
    record MapVillage(
            long id,
            int x,
            int y,
            Long playerId,
            boolean npc,
            int points,
            int loyalty,
            int ownerPoints,
            int ownerVillages,
            Instant ownerProtectedUntil) {

        Coordinates coordinates() {
            return new Coordinates(x, y);
        }

        boolean barbarian() {
            return playerId == null;
        }

        boolean human() {
            return playerId != null && !npc;
        }
    }

    /**
     * A foreign attack on one of the lord's villages, as a human sees it (no units).
     *
     * @param distance in fields, from its source to the target
     */
    record Incoming(
            long id,
            long targetVillageId,
            long sourceVillageId,
            double distance,
            Instant departAt,
            Instant arriveAt) {}

    private static final String VILLAGES =
            """
            select v.id, v.x, v.y, v.player_id, coalesce(p.npc, false) as npc, v.points,
                   v.loyalty, v.loyalty_updated_at, coalesce(p.points, 0) as owner_points,
                   coalesce(p.village_count, 0) as owner_villages, p.protected_until
            from village v
            left join player p on p.id = v.player_id
            """;

    private static final String INCOMING =
            """
            select m.id, m.target_village_id, m.source_village_id, m.depart_at, m.arrive_at,
                   s.x as sx, s.y as sy, t.x as tx, t.y as ty
            from movement m
            join village t on t.id = m.target_village_id
            join village s on s.id = m.source_village_id
            where t.player_id in (:playerIds) and m.kind = 'ATTACK'
              and m.owner_player_id not in (:playerIds)
            order by m.arrive_at, m.id
            """;

    private final JdbcClient jdbc;
    private final WorldService worldService;

    NpcWorld(JdbcClient jdbc, WorldService worldService) {
        this.jdbc = jdbc;
        this.worldService = worldService;
    }

    /** Every village on the map, by id, with the loyalty regenerated up to {@code now}. */
    Map<Long, MapVillage> villages(Instant now) {
        WorldSettings world = worldService.settings();
        double regenPerHour = Loyalty.regenPerHour(world);
        return jdbc
                .sql(VILLAGES)
                .query(
                        (rs, n) -> {
                            var protectedUntil = rs.getTimestamp("protected_until");
                            return new MapVillage(
                                    rs.getLong("id"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getObject("player_id", Long.class),
                                    rs.getBoolean("npc"),
                                    rs.getInt("points"),
                                    Loyalty.shown(
                                            Loyalty.regen(
                                                    rs.getDouble("loyalty"),
                                                    regenPerHour,
                                                    rs.getTimestamp("loyalty_updated_at")
                                                            .toInstant(),
                                                    now)),
                                    rs.getInt("owner_points"),
                                    rs.getInt("owner_villages"),
                                    protectedUntil == null ? null : protectedUntil.toInstant());
                        })
                .list()
                .stream()
                .collect(Collectors.toMap(MapVillage::id, Function.identity()));
    }

    /**
     * The foreign attacks on the way to the villages of these players, next first: the lord and its
     * guild mates (09 §13.3, the guild tells each other what is coming); attacks among them left
     * out.
     */
    List<Incoming> incomingAttacks(Collection<Long> playerIds) {
        return jdbc.sql(INCOMING)
                .param("playerIds", playerIds)
                .query(
                        (rs, n) ->
                                new Incoming(
                                        rs.getLong("id"),
                                        rs.getLong("target_village_id"),
                                        rs.getLong("source_village_id"),
                                        new Coordinates(rs.getInt("sx"), rs.getInt("sy"))
                                                .distanceTo(
                                                        new Coordinates(
                                                                rs.getInt("tx"), rs.getInt("ty"))),
                                        rs.getTimestamp("depart_at").toInstant(),
                                        rs.getTimestamp("arrive_at").toInstant()))
                .list();
    }

    /** The targets of the lord's attacks on the way (scouts included). */
    Set<Long> ownAttackTargets(long playerId) {
        return new HashSet<>(
                jdbc.sql(
                                "select target_village_id from movement where owner_player_id ="
                                        + " :playerId and kind = 'ATTACK'")
                        .param("playerId", playerId)
                        .query(Long.class)
                        .list());
    }

    /** The villages these players' nobles are on the way to (09 §13.2). */
    Set<Long> trainTargets(Collection<Long> playerIds) {
        return new HashSet<>(
                jdbc.sql(
                                "select target_village_id from movement where owner_player_id in"
                                        + " (:playerIds) and kind = 'ATTACK' and u_noble > 0")
                        .param("playerIds", playerIds)
                        .query(Long.class)
                        .list());
    }

    /** Which of these movement ids still exist. */
    Set<Long> existingMovements(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.sql("select id from movement where id in (:ids)")
                        .param("ids", ids)
                        .query(Long.class)
                        .list());
    }
}
