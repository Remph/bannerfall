package de.bannerfall.victory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the sides of the world (02-game-rules §22) with two plain queries: villages, points and
 * keeps per player (and his guild), and the totals of the map. A player belongs to his guild's side
 * only while guilds are on; otherwise everybody is a side of his own.
 */
@Component
class StandingsQuery {

    private static final String PER_PLAYER =
            """
            select v.player_id, p.name as player_name, p.npc,
                   g.id as guild_id, g.name as guild_name, g.tag as guild_tag,
                   count(*) as villages, sum(v.points) as points,
                   count(*) filter (where v.keep) as keeps
            from village v
            join player p on p.id = v.player_id
            left join guild_member m on m.player_id = v.player_id
            left join guild g on g.id = m.guild_id
            group by v.player_id, p.name, p.npc, g.id, g.name, g.tag
            """;

    private static final String TOTALS =
            "select count(*) as villages, count(*) filter (where keep) as keeps from village";

    private final JdbcClient jdbc;

    StandingsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Standings read(boolean guildsCount) {
        Map<String, VictorySide> sides = new LinkedHashMap<>();
        jdbc.sql(PER_PLAYER)
                .query(
                        rs -> {
                            Long guildId = rs.getObject("guild_id", Long.class);
                            VictorySide part =
                                    guildsCount && guildId != null
                                            ? new VictorySide(
                                                    SideKind.GUILD,
                                                    guildId,
                                                    rs.getString("guild_name"),
                                                    rs.getString("guild_tag"),
                                                    false,
                                                    rs.getInt("villages"),
                                                    rs.getLong("points"),
                                                    rs.getInt("keeps"))
                                            : new VictorySide(
                                                    SideKind.PLAYER,
                                                    rs.getLong("player_id"),
                                                    rs.getString("player_name"),
                                                    null,
                                                    rs.getBoolean("npc"),
                                                    rs.getInt("villages"),
                                                    rs.getLong("points"),
                                                    rs.getInt("keeps"));
                            sides.merge(part.key(), part, StandingsQuery::add);
                        });
        int[] totals = new int[2];
        jdbc.sql(TOTALS)
                .query(
                        rs -> {
                            totals[0] = rs.getInt("villages");
                            totals[1] = rs.getInt("keeps");
                        });
        List<VictorySide> ranked = new ArrayList<>(sides.values());
        ranked.sort(Standings.BY_POINTS);
        return new Standings(List.copyOf(ranked), totals[0], totals[1]);
    }

    private static VictorySide add(VictorySide a, VictorySide b) {
        return new VictorySide(
                a.kind(),
                a.id(),
                a.name(),
                a.tag(),
                a.npc(),
                a.villages() + b.villages(),
                a.points() + b.points(),
                a.keeps() + b.keeps());
    }
}
