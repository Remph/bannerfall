package de.bannerfall.npc;

import de.bannerfall.common.Coordinates;
import de.bannerfall.guild.GuildService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Guilds of NPC lords (09-npc-lords §13): who a lord's guild mates are, the points of its guild and
 * of the strongest human side for the brake, and the forming step of a round. Reads with plain SQL
 * like {@link NpcWorld}; every change of a membership goes through {@link GuildService}.
 */
@Component
class NpcGuilds {

    private static final Logger log = LoggerFactory.getLogger(NpcGuilds.class);

    private static final String LORDS =
            """
            select p.id, p.points, m.guild_id, b.guild_by_admin, b.personality,
                   (select count(*) from guild_member g where g.guild_id = m.guild_id) as size
            from player p
            join npc_brain b on b.player_id = p.id
            left join guild_member m on m.player_id = p.id
            where p.npc and p.village_count > 0
            order by p.id
            """;

    private static final String MATES =
            """
            select o.player_id from guild_member me
            join guild_member o on o.guild_id = me.guild_id and o.player_id <> me.player_id
            where me.player_id = :playerId
            """;

    private static final String HUMANS =
            """
            select p.id, p.points, m.guild_id from player p
            left join guild_member m on m.player_id = p.id
            where not p.npc and p.village_count > 0
            """;

    private final JdbcClient jdbc;
    private final GuildService guildService;
    private final WorldService worldService;
    private final NpcTemplates templates;
    private final NpcBalance balance;

    NpcGuilds(
            JdbcClient jdbc,
            GuildService guildService,
            WorldService worldService,
            NpcTemplates templates,
            NpcBalance balance) {
        this.jdbc = jdbc;
        this.guildService = guildService;
        this.worldService = worldService;
        this.templates = templates;
        this.balance = balance;
    }

    /** Lords act as guild mates only while guilds and lord guilds are both on (§13). */
    boolean active() {
        WorldSettings world = worldService.settings();
        return world.guildsEnabled() && world.npcGuildsEnabled();
    }

    /** The other lords of the lord's guild; empty without a guild or while lord guilds are off. */
    Set<Long> mates(long lordId) {
        if (!active()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql(MATES).param("playerId", lordId).query(Long.class).list());
    }

    /**
     * The points per member of the lord's guild (all members), empty without one or while off
     * (§13.5).
     */
    Optional<Double> guildAverage(long lordId) {
        if (!active()) {
            return Optional.empty();
        }
        return jdbc.sql(
                        """
                        select avg(p.points)::float8 from guild_member me
                        join guild_member o on o.guild_id = me.guild_id
                        join player p on p.id = o.player_id
                        where me.player_id = :playerId
                        """)
                .param("playerId", lordId)
                .query(Double.class)
                .optional();
    }

    /**
     * The points per member of the strongest human side (§13.5, 02 §22.1): a guild of humans or a
     * human without one, whichever has the most per member; with guilds off every human is a side.
     * Only humans who own a village count, as members too, like the rubber band (§8); 0 when there
     * are none.
     */
    double strongestHumanSide() {
        boolean guilds = worldService.settings().guildsEnabled();
        Map<String, Long> points = new HashMap<>();
        Map<String, Integer> members = new HashMap<>();
        jdbc.sql(HUMANS)
                .query(
                        (rs, n) -> {
                            long id = rs.getLong("id");
                            Long guild = rs.getObject("guild_id", Long.class);
                            String side = guilds && guild != null ? "G" + guild : "P" + id;
                            points.merge(side, rs.getLong("points"), Long::sum);
                            members.merge(side, 1, Integer::sum);
                            return id;
                        })
                .list();
        return points.entrySet().stream()
                .mapToDouble(e -> (double) e.getValue() / members.get(e.getKey()))
                .max()
                .orElse(0);
    }

    /**
     * The forming step of a round (§13.1): a lord that takes part joins the guild of its nearest
     * neighbour with room, or founds one with its nearest neighbour that takes part as well.
     */
    void form(long lordId) {
        if (!active()) {
            return;
        }
        List<NpcGuildForming.Lord> lords = lords();
        NpcGuildForming.Lord me =
                lords.stream().filter(l -> l.id() == lordId).findFirst().orElse(null);
        if (me == null) {
            return;
        }
        NpcGuildForming.Choice choice =
                NpcGuildForming.choose(
                        me,
                        lords,
                        balance.general().guildRadius()
                                * worldService.settings().mapSpread().factor(),
                        guildService.lordGuildMax());
        switch (choice) {
            case NpcGuildForming.Join join -> {
                guildService.addLord(join.guildId(), lordId);
                log.info("NPC lord {} joined guild {}", lordId, join.guildId());
            }
            case NpcGuildForming.Found found -> {
                NpcGuildForming.Name name = nextName();
                long guildId =
                        guildService.foundForLords(
                                lordId, found.otherId(), name.name(), name.tag());
                log.info(
                        "NPC lords {} and {} founded guild {} ({})",
                        lordId,
                        found.otherId(),
                        guildId,
                        name.name());
            }
            case NpcGuildForming.Stay stay -> {}
        }
    }

    /** A lord out of the game leaves its guild (§13.1); nothing for a lord in none. */
    void leave(long lordId) {
        guildService.removeLord(lordId);
    }

    /** The next free name for a lord guild (§13.1). */
    NpcGuildForming.Name nextName() {
        Set<String> names = new HashSet<>();
        Set<String> tags = new HashSet<>();
        jdbc.sql("select lower(name) as name, lower(tag) as tag from guild")
                .query(
                        (rs, n) -> {
                            names.add(rs.getString("name"));
                            tags.add(rs.getString("tag"));
                            return n;
                        })
                .list();
        return NpcGuildForming.nextName(names, tags);
    }

    /** Every lord that owns a village, as the forming sees it. */
    private List<NpcGuildForming.Lord> lords() {
        Map<Long, List<Coordinates>> villages = new HashMap<>();
        jdbc.sql(
                        "select v.player_id, v.x, v.y from village v join player p on p.id ="
                                + " v.player_id where p.npc")
                .query(
                        (rs, n) -> {
                            villages.computeIfAbsent(
                                            rs.getLong("player_id"), k -> new ArrayList<>())
                                    .add(new Coordinates(rs.getInt("x"), rs.getInt("y")));
                            return n;
                        })
                .list();
        return jdbc.sql(LORDS)
                .query(
                        (rs, n) -> {
                            long id = rs.getLong("id");
                            NpcPersonality personality =
                                    NpcPersonality.valueOf(rs.getString("personality"));
                            return new NpcGuildForming.Lord(
                                    id,
                                    rs.getInt("points"),
                                    rs.getObject("guild_id", Long.class),
                                    rs.getInt("size"),
                                    rs.getBoolean("guild_by_admin"),
                                    midPoints(templates.of(personality)),
                                    villages.getOrDefault(id, List.of()));
                        })
                .list();
    }

    /** Where the template's second stage (MID) starts: the first stage's {@code below-points}. */
    static int midPoints(NpcTemplates.Template template) {
        Integer below = template.stages().getFirst().belowPoints();
        return below == null ? 0 : below;
    }
}
