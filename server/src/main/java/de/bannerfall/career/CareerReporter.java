package de.bannerfall.career;

import de.bannerfall.account.AccountServerSettings;
import de.bannerfall.achievement.Achievement;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.statistics.Stat;
import de.bannerfall.world.WorldService;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tells the account server how this world's linked players are doing (1.5, 01-architecture §4.9b):
 * name, points, villages, rank, achievements, a few statistics totals and, once the world has
 * ended, its result for them. The account server shows it on their career page.
 *
 * <p>Every few minutes ({@code bannerfall.account-server.career-interval}) the reporter builds the
 * report of every account that logs in through the account server and sends those that changed
 * since the last time ({@code career_sync} keeps a hash of what was sent), at most {@link #BATCH}
 * per request, authenticated with this world's client id and secret. Nothing waits for it: it runs
 * on its own thread, and a failed round is simply the next round's work.
 *
 * <p>A batch the account server rejects as invalid (400) is sent again one player at a time, so a
 * single report it won't take doesn't hold back the others. That report is not sent again until it
 * changes (or the server restarts); the log names its subject.
 */
@Component
public class CareerReporter {

    private static final Logger log = LoggerFactory.getLogger(CareerReporter.class);

    /** Where the account server takes the reports, below its issuer address. */
    public static final String PATH = "/realm-api/career";

    /** Players per request. */
    static final int BATCH = 100;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** What the account server gets: this world and a batch of its players. */
    public record Report(World world, List<PlayerReport> players) {}

    /**
     * @param startedAt when this world's database was set up ({@code world_settings.started_at},
     *     never changes): tells a later world on the same realm apart from this one
     * @param phase {@code PREGAME}, {@code OPEN} or {@code ENDED}
     */
    public record World(String name, Instant startedAt, String phase, Instant endedAt) {}

    /**
     * One player of this world.
     *
     * @param subject the account's id at the account server
     * @param rank null while the player is deleted here
     * @param deleted the host deleted the player on this world
     * @param result null until the world has ended
     * @param stats his battle totals (1.5, 02-game-rules §24)
     */
    public record PlayerReport(
            String subject,
            String name,
            Instant joinedAt,
            int points,
            int villages,
            Long rank,
            boolean deleted,
            List<EarnedAchievement> achievements,
            Result result,
            Stats stats) {}

    /**
     * A few statistics totals (02-game-rules §24). {@code unitsKilled} is the population killed
     * attacking and defending together.
     */
    public record Stats(
            long attacksWon, long defencesWon, long unitsKilled, long villagesConquered) {}

    /**
     * An achievement level (levelled ones once per level reached, 1 to 3: bronze, silver, gold).
     */
    public record EarnedAchievement(String key, boolean leveled, int level, Instant earnedAt) {}

    /**
     * How the world ended for the player: his side (his guild, or himself without one), its place
     * in the final standings, and whether it won.
     */
    public record Result(String reason, Instant endedAt, boolean won, Integer place, String side) {}

    private record Linked(long accountId, PlayerReport report) {}

    /** What the account server did with a request. */
    private enum Answer {
        TAKEN,
        /** 400: something in the request is invalid; sending it again won't help. */
        REJECTED,
        /** Not reached, or any other answer: the next round tries again. */
        FAILED
    }

    private final AccountServerSettings settings;
    private final JdbcClient jdbc;
    private final PlayerRepository players;
    private final WorldService world;
    private final Clock clock;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().name("career-report").daemon().factory());
    private final AtomicBoolean running = new AtomicBoolean();

    /** The hash of the report the account server rejected, per account: not sent again as is. */
    private final Map<Long, String> rejected = new ConcurrentHashMap<>();

    CareerReporter(
            AccountServerSettings settings,
            JdbcClient jdbc,
            PlayerRepository players,
            WorldService world,
            Clock clock) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.players = players;
        this.world = world;
        this.clock = clock;
    }

    /**
     * The scheduler's tick: starts a round on the reporter's own thread (the scheduler's single
     * thread also runs the game's events), unless one is still running.
     */
    @Scheduled(
            fixedDelayString = "${bannerfall.account-server.career-interval:PT5M}",
            initialDelayString = "PT1M")
    void tick() {
        if (settings.careerReports() && running.compareAndSet(false, true)) {
            worker.execute(
                    () -> {
                        try {
                            report();
                        } catch (RuntimeException e) {
                            log.warn("Career reports failed: {}", e.toString());
                        } finally {
                            running.set(false);
                        }
                    });
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    /**
     * One round: sends the reports that changed since they were last sent.
     *
     * @return how many players were reported; stops at the first failed request, goes on after a
     *     rejected one
     */
    public int report() {
        if (!settings.careerReports()) {
            return 0;
        }
        World current = currentWorld();
        Map<Long, String> sent = new HashMap<>();
        jdbc.sql("select account_id, sent_hash from career_sync")
                .query(
                        rs -> {
                            sent.put(rs.getLong("account_id"), rs.getString("sent_hash"));
                        });
        List<Linked> changed = new ArrayList<>();
        Map<Long, String> hashes = new HashMap<>();
        for (Linked linked : linkedPlayers(current)) {
            String hash = hash(current, linked.report());
            if (!hash.equals(sent.get(linked.accountId()))
                    && !hash.equals(rejected.get(linked.accountId()))) {
                changed.add(linked);
                hashes.put(linked.accountId(), hash);
            }
        }
        int reported = 0;
        for (int from = 0; from < changed.size(); from += BATCH) {
            List<Linked> batch = changed.subList(from, Math.min(from + BATCH, changed.size()));
            Answer answer = send(current, batch);
            if (answer == Answer.TAKEN) {
                reported += markSent(batch, hashes);
                continue;
            }
            if (answer == Answer.FAILED) {
                break;
            }
            // one player at a time, to find the reports the account server won't take
            for (Linked linked : batch) {
                Answer alone = batch.size() == 1 ? answer : send(current, List.of(linked));
                if (alone == Answer.FAILED) {
                    return logged(reported);
                }
                if (alone == Answer.TAKEN) {
                    reported += markSent(List.of(linked), hashes);
                } else {
                    rejected.put(linked.accountId(), hashes.get(linked.accountId()));
                    log.warn(
                            "The account server rejected the career report of subject {}; it"
                                    + " is sent again when it changes",
                            linked.report().subject());
                }
            }
        }
        return logged(reported);
    }

    /** Remembers what was sent, so it goes out again only after a change. */
    private int markSent(List<Linked> batch, Map<Long, String> hashes) {
        Timestamp now = Timestamp.from(clock.instant());
        for (Linked linked : batch) {
            jdbc.sql(
                            """
                            insert into career_sync (account_id, sent_hash, sent_at)
                            values (:account, :hash, :at)
                            on conflict (account_id)
                            do update set sent_hash = excluded.sent_hash, sent_at = excluded.sent_at
                            """)
                    .param("account", linked.accountId())
                    .param("hash", hashes.get(linked.accountId()))
                    .param("at", now)
                    .update();
            rejected.remove(linked.accountId());
        }
        return batch.size();
    }

    private static int logged(int reported) {
        if (reported > 0) {
            log.info("Reported the careers of {} players to the account server", reported);
        }
        return reported;
    }

    private World currentWorld() {
        String phase = world.phase().name();
        return new World(world.settings().name(), world.startedAt(), phase, world.endedAt());
    }

    /** Every player whose account logs in through the configured account server. */
    private List<Linked> linkedPlayers(World current) {
        record Row(
                long accountId,
                String subject,
                boolean deleted,
                long playerId,
                String name,
                Instant joinedAt,
                int points,
                int villages) {}
        List<Row> rows =
                jdbc.sql(
                                """
                                select a.id as account_id, a.external_subject, a.disabled,
                                       p.id as player_id, p.name, p.created_at, p.points,
                                       p.village_count
                                from account a join player p on p.account_id = a.id
                                where a.external_issuer = :issuer and not p.npc
                                order by a.id
                                """)
                        .param("issuer", settings.issuer())
                        .query(
                                (rs, rowNum) ->
                                        new Row(
                                                rs.getLong("account_id"),
                                                rs.getString("external_subject"),
                                                rs.getBoolean("disabled"),
                                                rs.getLong("player_id"),
                                                rs.getString("name"),
                                                rs.getTimestamp("created_at").toInstant(),
                                                rs.getInt("points"),
                                                rs.getInt("village_count")))
                        .list();
        Map<Long, List<EarnedAchievement>> achievements = new HashMap<>();
        jdbc.sql(
                        """
                        select pa.player_id, pa.achievement, pa.level, pa.earned_at
                        from player_achievement pa
                        join player p on p.id = pa.player_id
                        join account a on a.id = p.account_id
                        where a.external_issuer = :issuer
                        order by pa.earned_at, pa.achievement, pa.level
                        """)
                .param("issuer", settings.issuer())
                .query(
                        rs -> {
                            achievements
                                    .computeIfAbsent(
                                            rs.getLong("player_id"), id -> new ArrayList<>())
                                    .add(
                                            new EarnedAchievement(
                                                    rs.getString("achievement"),
                                                    leveled(rs.getString("achievement")),
                                                    rs.getInt("level"),
                                                    rs.getTimestamp("earned_at").toInstant()));
                        });
        Map<Long, Map<String, Long>> stats = new HashMap<>();
        jdbc.sql(
                        """
                        select s.player_id, s.stat, s.value
                        from player_stat s
                        join player p on p.id = s.player_id
                        join account a on a.id = p.account_id
                        where a.external_issuer = :issuer
                        """)
                .param("issuer", settings.issuer())
                .query(
                        rs -> {
                            stats.computeIfAbsent(rs.getLong("player_id"), id -> new HashMap<>())
                                    .put(rs.getString("stat"), rs.getLong("value"));
                        });
        Results results = "ENDED".equals(current.phase()) ? loadResults() : null;
        List<Linked> linked = new ArrayList<>();
        for (Row row : rows) {
            Long rank = row.deleted() ? null : players.findRank(row.playerId()).orElse(null);
            linked.add(
                    new Linked(
                            row.accountId(),
                            new PlayerReport(
                                    row.subject(),
                                    row.name(),
                                    row.joinedAt(),
                                    row.points(),
                                    row.villages(),
                                    rank,
                                    row.deleted(),
                                    achievements.getOrDefault(row.playerId(), List.of()),
                                    results == null ? null : results.of(row.playerId()),
                                    stats(stats.getOrDefault(row.playerId(), Map.of())))));
        }
        return linked;
    }

    private static Stats stats(Map<String, Long> totals) {
        return new Stats(
                totals.getOrDefault(Stat.ATTACKS_WON.name(), 0L),
                totals.getOrDefault(Stat.DEFENCES_WON.name(), 0L),
                totals.getOrDefault(Stat.KILLED_ATTACKING.name(), 0L)
                        + totals.getOrDefault(Stat.KILLED_DEFENDING.name(), 0L),
                totals.getOrDefault(Stat.VILLAGES_CONQUERED.name(), 0L));
    }

    /** The world's result (02-game-rules §22), to look up each player's side in. */
    private record Results(
            String reason,
            Instant endedAt,
            String winnerKind,
            Long winnerRef,
            Map<String, Side> sides,
            Map<Long, Long> guildOf) {

        private record Side(int rank, String name) {}

        Result of(long playerId) {
            String key = "PLAYER:" + playerId;
            if (!sides.containsKey(key) && guildOf.containsKey(playerId)) {
                key = "GUILD:" + guildOf.get(playerId);
            }
            Side side = sides.get(key);
            boolean won = winnerRef != null && key.equals(winnerKind + ":" + winnerRef);
            return new Result(
                    reason,
                    endedAt,
                    won,
                    side == null ? null : side.rank(),
                    side == null ? null : side.name());
        }
    }

    private Results loadResults() {
        record Header(String reason, Instant endedAt, String winnerKind, Long winnerRef) {}
        Header header =
                jdbc.sql(
                                "select reason, ended_at, winner_kind, winner_ref_id from"
                                        + " world_result where id = 1")
                        .query(
                                (rs, rowNum) ->
                                        new Header(
                                                rs.getString("reason"),
                                                rs.getTimestamp("ended_at").toInstant(),
                                                rs.getString("winner_kind"),
                                                rs.getObject("winner_ref_id", Long.class)))
                        .optional()
                        .orElse(null);
        if (header == null) {
            return null;
        }
        Map<String, Results.Side> sides = new HashMap<>();
        jdbc.sql("select rank, kind, ref_id, name from world_result_side")
                .query(
                        rs -> {
                            sides.put(
                                    rs.getString("kind") + ":" + rs.getLong("ref_id"),
                                    new Results.Side(rs.getInt("rank"), rs.getString("name")));
                        });
        Map<Long, Long> guildOf = new HashMap<>();
        jdbc.sql("select player_id, guild_id from guild_member")
                .query(
                        rs -> {
                            guildOf.put(rs.getLong("player_id"), rs.getLong("guild_id"));
                        });
        return new Results(
                header.reason(),
                header.endedAt(),
                header.winnerKind(),
                header.winnerRef(),
                sides,
                guildOf);
    }

    private static boolean leveled(String key) {
        try {
            return Achievement.valueOf(key).leveled();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Answer send(World world, List<Linked> batch) {
        Report report = new Report(world, batch.stream().map(Linked::report).toList());
        String credentials =
                URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8)
                        + ":"
                        + URLEncoder.encode(settings.clientSecret(), StandardCharsets.UTF_8);
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(settings.issuer() + PATH))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString(
                                                        credentials.getBytes(
                                                                StandardCharsets.UTF_8)))
                        .POST(HttpRequest.BodyPublishers.ofString(toJson(report)))
                        .build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status / 100 == 2) {
                return Answer.TAKEN;
            }
            log.warn("The account server answered the career report with {}", status);
            if (status == 400) {
                return Answer.REJECTED;
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("The account server missed the career report: {}", e.toString());
        }
        return Answer.FAILED;
    }

    /** A report as the account server gets it; {@code CareerReportSampleTest} keeps a sample. */
    static String toJson(Report report) {
        return JSON.writeValueAsString(report);
    }

    private static String hash(World world, PlayerReport report) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(JSON.writeValueAsBytes(new Report(world, List.of(report))));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
