package de.bannerfall.chronicle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The frames of the replay map (2.1 I1): who owned which village after each day, read from {@code
 * chronicle_frame} and, for the end, from the villages as they are.
 */
@Component
class ChronicleFrames {

    /** {@code [[village_id, player_id or 0], …]} of every village now. */
    static final String OWNERS =
            """
            coalesce((select jsonb_agg(jsonb_build_array(v.id, coalesce(v.player_id, 0)) order by v.id)
                      from village v), '[]'::jsonb)
            """;

    /** Every player who owns a village now, with his guild and points. */
    static final String PLAYERS =
            """
            coalesce((select jsonb_agg(jsonb_build_object(
                          'id', p.id, 'name', p.name, 'npc', p.npc, 'guildId', g.id,
                          'guildName', g.name, 'guildTag', g.tag, 'points', p.points) order by p.id)
                      from player p
                      left join guild_member m on m.player_id = p.id
                      left join guild g on g.id = m.guild_id
                      where p.village_count > 0), '[]'::jsonb)
            """;

    /** A player in a frame. */
    record Owner(
            long id,
            String name,
            boolean npc,
            Long guildId,
            String guildName,
            String guildTag,
            long points) {}

    /**
     * One frame: after {@code day} days (0 = at the opening).
     *
     * @param owners pairs of village id and owner id (0 for a barbarian village)
     */
    record Frame(int day, Instant at, List<long[]> owners, List<Owner> players) {}

    private final JdbcClient jdbc;

    ChronicleFrames(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The stored frames, the first day first. */
    List<Frame> stored() {
        return jdbc.sql("select day, at, owners, players from chronicle_frame order by day")
                .query((rs, row) -> frame(rs, rs.getInt("day")))
                .list();
    }

    /** The world as it is now, as the frame of {@code day} ({@code at} its time). */
    Frame now(int day, Instant at) {
        return jdbc.sql(
                        "select cast(:at as timestamptz) as at, "
                                + OWNERS
                                + " as owners, "
                                + PLAYERS
                                + " as players")
                .param("at", java.sql.Timestamp.from(at))
                .query((rs, row) -> frame(rs, day))
                .single();
    }

    private static Frame frame(ResultSet rs, int day) throws SQLException {
        JsonNode owners = Chronicle.JSON.readTree(rs.getString("owners"));
        List<long[]> pairs = new ArrayList<>(owners.size());
        for (JsonNode pair : owners) {
            pairs.add(new long[] {pair.get(0).asLong(), pair.get(1).asLong()});
        }
        JsonNode players = Chronicle.JSON.readTree(rs.getString("players"));
        List<Owner> list = new ArrayList<>(players.size());
        for (JsonNode p : players) {
            list.add(
                    new Owner(
                            p.get("id").asLong(),
                            p.get("name").asString(),
                            p.get("npc").asBoolean(),
                            p.hasNonNull("guildId") ? p.get("guildId").asLong() : null,
                            p.hasNonNull("guildName") ? p.get("guildName").asString() : null,
                            p.hasNonNull("guildTag") ? p.get("guildTag").asString() : null,
                            p.path("points").asLong()));
        }
        return new Frame(day, rs.getTimestamp("at").toInstant(), pairs, list);
    }
}
