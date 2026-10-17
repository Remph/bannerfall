package de.bannerfall.victory;

import de.bannerfall.world.EndReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The result of an ended world (02-game-rules §22, tables {@code world_result} and {@code
 * world_result_side}): written once when the world ends, deleted if the host takes the end back.
 * Plain SQL: two small tables nobody changes in between.
 */
@Component
class WorldResultStore {

    /** The final standings keep at most this many sides. */
    static final int MAX_SIDES = 100;

    private final JdbcClient jdbc;

    WorldResultStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void save(WorldResultDto result) {
        VictorySide winner = result.winner();
        jdbc.sql(
                        """
                        insert into world_result (id, reason, ended_at, winner_kind, winner_ref_id,
                                                  winner_name, winner_tag, winner_npc)
                        values (1, :reason, :endedAt, :kind, :refId, :name, :tag, :npc)
                        """)
                .param("reason", result.reason().name())
                .param("endedAt", Timestamp.from(result.endedAt()))
                .param("kind", winner == null ? null : winner.kind().name())
                .param("refId", winner == null ? null : winner.id())
                .param("name", winner == null ? null : winner.name())
                .param("tag", winner == null ? null : winner.tag())
                .param("npc", winner != null && winner.npc())
                .update();
        int rank = 1;
        for (VictorySide side : result.sides()) {
            jdbc.sql(
                            """
                            insert into world_result_side (rank, kind, ref_id, name, tag, npc,
                                                           villages, points, keeps)
                            values (:rank, :kind, :refId, :name, :tag, :npc, :villages, :points,
                                    :keeps)
                            """)
                    .param("rank", rank++)
                    .param("kind", side.kind().name())
                    .param("refId", side.id())
                    .param("name", side.name())
                    .param("tag", side.tag())
                    .param("npc", side.npc())
                    .param("villages", side.villages())
                    .param("points", side.points())
                    .param("keeps", side.keeps())
                    .update();
        }
    }

    Optional<WorldResultDto> load() {
        List<VictorySide> sides =
                jdbc.sql(
                                """
                                select kind, ref_id, name, tag, npc, villages, points, keeps
                                from world_result_side order by rank
                                """)
                        .query(
                                (rs, rowNum) ->
                                        new VictorySide(
                                                SideKind.valueOf(rs.getString("kind")),
                                                rs.getLong("ref_id"),
                                                rs.getString("name"),
                                                rs.getString("tag"),
                                                rs.getBoolean("npc"),
                                                rs.getInt("villages"),
                                                rs.getLong("points"),
                                                rs.getInt("keeps")))
                        .list();
        return jdbc.sql("select * from world_result where id = 1")
                .query((rs, rowNum) -> result(rs, sides))
                .optional();
    }

    void delete() {
        jdbc.sql("delete from world_result_side").update();
        jdbc.sql("delete from world_result").update();
    }

    private static WorldResultDto result(ResultSet rs, List<VictorySide> sides)
            throws SQLException {
        String kind = rs.getString("winner_kind");
        VictorySide winner = null;
        if (kind != null) {
            long refId = rs.getLong("winner_ref_id");
            SideKind sideKind = SideKind.valueOf(kind);
            // the winner's numbers from the standings; the row keeps the name if it is not there
            winner =
                    sides.stream()
                            .filter(s -> s.kind() == sideKind && s.id() == refId)
                            .findFirst()
                            .orElse(
                                    new VictorySide(
                                            sideKind,
                                            refId,
                                            rs.getString("winner_name"),
                                            rs.getString("winner_tag"),
                                            rs.getBoolean("winner_npc"),
                                            0,
                                            0,
                                            0));
        }
        Instant endedAt = rs.getTimestamp("ended_at").toInstant();
        return new WorldResultDto(
                EndReason.valueOf(rs.getString("reason")), endedAt, winner, sides);
    }
}
