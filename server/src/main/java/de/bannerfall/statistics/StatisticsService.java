package de.bannerfall.statistics;

import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistics (02-game-rules §24): the running totals the game code adds to, the nightly snapshot
 * and the history read from it.
 *
 * <p>Plain SQL like {@code AchievementService}: a total is raised with one {@code insert … on
 * conflict do update}, so parallel events add up exactly, and no entity (least of all the versioned
 * {@code Player}) is touched, so counting never fails the battle it is called from.
 *
 * <p>Days are days in the world's time zone, the setting the night bonus uses ({@code
 * bannerfall.night-bonus.zone}, Europe/Berlin unless the host sets another one).
 */
@Service
public class StatisticsService {

    /** How many leading players the world statistics show over time. */
    public static final int LEADERS = 5;

    /** The ranked players, as the ranking counts them (NPC lords, humans not deleted). */
    private static final String RANKED =
            """
            select rank() over (order by p.points desc) as ranking, p.id, p.name, p.npc,
                   p.points, p.village_count
            from player p left join account a on a.id = p.account_id
            where p.npc or not a.disabled
            """;

    private final JdbcClient jdbc;
    private final WorldService world;
    private final Clock clock;
    private final ZoneId zone;

    StatisticsService(
            JdbcClient jdbc,
            WorldService world,
            Clock clock,
            @Value("${bannerfall.night-bonus.zone:Europe/Berlin}") String zone) {
        this.jdbc = jdbc;
        this.world = world;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    // ---- counting, called by the game code ----

    /** Adds to one total of a player. Nothing for barbarians (null) or a delta of 0 or less. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(Long playerId, Stat stat, long delta) {
        if (playerId == null || delta <= 0) {
            return;
        }
        jdbc.sql(
                        """
                        insert into player_stat (player_id, stat, value) values (:player, :stat, :delta)
                        on conflict (player_id, stat) do update set value = player_stat.value + :delta
                        """)
                .param("player", playerId)
                .param("stat", stat.name())
                .param("delta", delta)
                .update();
    }

    /**
     * Adds what {@link BattleStats#of} found, in one statement: a battle has a dozen or more totals
     * to raise, and one round trip each was about a tenth of the whole battle. Sorted by player and
     * stat, so two transactions lock the rows in the same order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(Map<Long, Map<Stat, Long>> deltas) {
        List<Long> players = new ArrayList<>();
        List<String> stats = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        new TreeMap<>(deltas)
                .forEach(
                        (player, byStat) ->
                                new TreeMap<>(byStat)
                                        .forEach(
                                                (stat, delta) -> {
                                                    if (delta > 0) {
                                                        players.add(player);
                                                        stats.add(stat.name());
                                                        values.add(delta);
                                                    }
                                                }));
        if (players.isEmpty()) {
            return;
        }
        jdbc.sql(
                        """
                        insert into player_stat (player_id, stat, value)
                        select * from unnest(:players::bigint[], :stats::text[], :values::bigint[])
                        on conflict (player_id, stat) do update
                            set value = player_stat.value + excluded.value
                        """)
                .param("players", players.toArray(Long[]::new))
                .param("stats", stats.toArray(String[]::new))
                .param("values", values.toArray(Long[]::new))
                .update();
    }

    // ---- reading ----

    /** Every total of the player; a stat he has no row for is missing. */
    @Transactional(readOnly = true)
    public Map<Stat, Long> totals(long playerId) {
        Map<Stat, Long> totals = new EnumMap<>(Stat.class);
        jdbc.sql("select stat, value from player_stat where player_id = ?")
                .param(playerId)
                .query(
                        rs -> {
                            Stat stat = stat(rs.getString("stat"));
                            if (stat != null) {
                                totals.put(stat, rs.getLong("value"));
                            }
                        });
        return totals;
    }

    @Transactional(readOnly = true)
    public PlayerStatsDto stats(long playerId) {
        return PlayerStatsDto.of(totals(playerId), since());
    }

    /** When the world started counting: the migration that brought the statistics. */
    @Transactional(readOnly = true)
    public Instant since() {
        return jdbc.sql("select since from statistics_since where id = 1")
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    /** The player's days, today's last (the caller checks that he is ranked). */
    @Transactional(readOnly = true)
    public PlayerHistoryDto history(long playerId) {
        List<PlayerHistoryDto.PlayerDay> days =
                new ArrayList<>(
                        jdbc.sql(
                                        """
                                        select day, points, villages, rank from player_day
                                        where player_id = ? order by day
                                        """)
                                .param(playerId)
                                .query(
                                        (rs, row) ->
                                                new PlayerHistoryDto.PlayerDay(
                                                        rs.getDate("day").toLocalDate(),
                                                        rs.getInt("points"),
                                                        rs.getInt("villages"),
                                                        rs.getInt("rank")))
                                .list());
        LocalDate live = liveDay();
        if (days.isEmpty() || days.getLast().day().isBefore(live)) {
            jdbc.sql("select * from (" + RANKED + ") r where r.id = ?")
                    .param(playerId)
                    .query(
                            (rs, row) ->
                                    new PlayerHistoryDto.PlayerDay(
                                            live,
                                            rs.getInt("points"),
                                            rs.getInt("village_count"),
                                            rs.getInt("ranking")))
                    .optional()
                    .ifPresent(days::add);
        }
        return new PlayerHistoryDto(since(), days);
    }

    /** The world's days and its leaders' points over them, today's last. */
    @Transactional(readOnly = true)
    public WorldStatisticsDto world() {
        List<WorldStatisticsDto.WorldDay> days =
                new ArrayList<>(
                        jdbc.sql(
                                        """
                                        select day, players, villages, barbarian_villages, points
                                        from world_day order by day
                                        """)
                                .query(
                                        (rs, row) ->
                                                new WorldStatisticsDto.WorldDay(
                                                        rs.getDate("day").toLocalDate(),
                                                        rs.getInt("players"),
                                                        rs.getInt("villages"),
                                                        rs.getInt("barbarian_villages"),
                                                        rs.getLong("points")))
                                .list());
        LocalDate live = liveDay();
        boolean addLive = days.isEmpty() || days.getLast().day().isBefore(live);
        if (addLive) {
            days.add(worldNow(live));
        }

        record Now(long id, String name, boolean npc, int points) {}
        List<Now> leaders =
                jdbc.sql("select * from (" + RANKED + ") r order by r.ranking, r.id limit :n")
                        .param("n", LEADERS)
                        .query(
                                (rs, row) ->
                                        new Now(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getBoolean("npc"),
                                                rs.getInt("points")))
                        .list();
        Map<Long, List<WorldStatisticsDto.LeaderPoints>> points = new HashMap<>();
        if (!leaders.isEmpty()) {
            jdbc.sql(
                            """
                            select player_id, day, points from player_day
                            where player_id in (:ids) order by day
                            """)
                    .param("ids", leaders.stream().map(Now::id).toList())
                    .query(
                            rs -> {
                                points.computeIfAbsent(
                                                rs.getLong("player_id"), id -> new ArrayList<>())
                                        .add(
                                                new WorldStatisticsDto.LeaderPoints(
                                                        rs.getDate("day").toLocalDate(),
                                                        rs.getInt("points")));
                            });
        }
        List<WorldStatisticsDto.LeaderLine> leaderLines = new ArrayList<>();
        for (Now leader : leaders) {
            List<WorldStatisticsDto.LeaderPoints> line =
                    points.computeIfAbsent(leader.id(), id -> new ArrayList<>());
            if (line.isEmpty() || line.getLast().day().isBefore(live)) {
                line.add(new WorldStatisticsDto.LeaderPoints(live, leader.points()));
            }
            leaderLines.add(
                    new WorldStatisticsDto.LeaderLine(
                            leader.id(), leader.name(), leader.npc(), line));
        }
        return new WorldStatisticsDto(since(), days, leaderLines);
    }

    // ---- the nightly snapshot ----

    /** Today in the world's time zone. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    /**
     * Writes day {@code day} for the world and every ranked player, as they are now, unless it is
     * written already. Nothing before the statistics started, before the world opened or after it
     * ended.
     *
     * @return true if the day was written now
     */
    @Transactional
    public boolean snapshot(LocalDate day) {
        if (!counts(day)) {
            return false;
        }
        Date sqlDay = Date.valueOf(day);
        int written =
                jdbc.sql(
                                "with ranked as ("
                                        + RANKED
                                        + """
                                        )
                                        insert into world_day (day, players, villages,
                                                               barbarian_villages, points)
                                        select :day,
                                               (select count(*) from ranked),
                                               (select count(*) from village
                                                where player_id is not null),
                                               (select count(*) from village where player_id is null),
                                               (select coalesce(sum(points), 0) from ranked)
                                        on conflict (day) do nothing
                                        """)
                        .param("day", sqlDay)
                        .update();
        if (written == 0) {
            return false;
        }
        jdbc.sql(
                        "with ranked as ("
                                + RANKED
                                + """
                                )
                                insert into player_day (player_id, day, points, villages, rank, stats)
                                select r.id, :day, r.points, r.village_count, r.ranking,
                                       coalesce((select jsonb_object_agg(s.stat, s.value)
                                                 from player_stat s where s.player_id = r.id),
                                                '{}'::jsonb)
                                from ranked r
                                on conflict (player_id, day) do nothing
                                """)
                .param("day", sqlDay)
                .update();
        return true;
    }

    /** Whether the world ran on {@code day}: after the statistics started, open, not yet ended. */
    private boolean counts(LocalDate day) {
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        if (world.phase() == WorldPhase.PREGAME || !since().isBefore(end)) {
            return false;
        }
        return world.phase() != WorldPhase.ENDED || !world.endedAt().isBefore(start);
    }

    /** The day the live values belong to: today, or the last day of an ended world. */
    private LocalDate liveDay() {
        LocalDate today = today();
        if (world.phase() == WorldPhase.ENDED && world.endedAt() != null) {
            LocalDate end = LocalDate.ofInstant(world.endedAt(), zone);
            return end.isBefore(today) ? end : today;
        }
        return today;
    }

    private WorldStatisticsDto.WorldDay worldNow(LocalDate day) {
        return jdbc.sql(
                        "with ranked as ("
                                + RANKED
                                + """
                                )
                                select (select count(*) from ranked) as players,
                                       (select count(*) from village where player_id is not null)
                                           as villages,
                                       (select count(*) from village where player_id is null)
                                           as barbarian_villages,
                                       (select coalesce(sum(points), 0) from ranked) as points
                                """)
                .query(
                        (rs, row) ->
                                new WorldStatisticsDto.WorldDay(
                                        day,
                                        rs.getInt("players"),
                                        rs.getInt("villages"),
                                        rs.getInt("barbarian_villages"),
                                        rs.getLong("points")))
                .single();
    }

    /**
     * The stat of that name; null for a name this version doesn't know (written by a newer one).
     */
    private static Stat stat(String name) {
        try {
            return Stat.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
