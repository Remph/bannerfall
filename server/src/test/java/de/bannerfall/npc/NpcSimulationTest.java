package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.common.RandomSource;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.Production;
import de.bannerfall.rules.Storage;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The NPC simulation of 09-npc-lords §11 (M12d): a fresh world with only NPC lords, then the same
 * with two sleeping humans, at speed 3 for 50 days (the clock moves from event to event, the real
 * event engine runs everything). It prints the summary tables of §11, checks the targets and fails
 * on the ones that are hard rules (a build order in the quiet hours, a FAILED event, an attack that
 * breaks the human's quiet hours or cap, a human who loses a spared last village).
 *
 * <p>Tagged {@code simulation}: {@code ./gradlew test} skips it, {@code ./gradlew simulationTest}
 * runs it (and only it). The dice are seeded, so a run can be repeated. Knobs (Gradle properties
 * {@code -PsimDays=…}, {@code -PsimThinkMinutes=…}, {@code -PsimSeed=…}); the tables are also
 * written to {@code build/simulation/}. Balance variants (v1.3): {@code -PsimWalls=true} (barbarian
 * walls with the default settings), {@code -PsimFarmShare=…} and {@code -PsimFarmRest=…} (a CUSTOM
 * difficulty: NORMAL with these farm values), {@code -PsimFarmCooldown=…} (minutes, overrides
 * {@code farm-cooldown-minutes}), {@code -PsimLabel=…} (added to the title and the file name). Lord
 * guilds: {@code -PsimLordGuilds=true}; lord banners (1.4, 09 §14): {@code -PsimLordBanners=true};
 * archers (1.6, 02-game-rules §5.6): {@code -PsimArchers=true}; morale (1.6, 02 §8.2, on by
 * default): {@code -PsimMorale=false}; learning lords (1.6, 09 §15): {@code
 * -PsimLordLearning=true}; lord shipments (1.6, 09 §5.5) are on unless {@code
 * -PsimShipments=false}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final double SPEED = 3.0;
    private static final int LORDS = 6;
    private static final Duration SAMPLE = Duration.ofHours(6);
    private static final Duration MAX_STEP = Duration.ofMinutes(10);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired TestRandomSource dice;
    @Autowired GameData gameData;
    @Autowired NpcBalance balance;
    @Autowired NpcBrainRepository brains;

    private final int days = Integer.getInteger("simulation.days", 50);
    private final int thinkMinutes = Integer.getInteger("simulation.thinkMinutes", 60);
    private final long seed = Long.getLong("simulation.seed", 20261007L);
    private final boolean walls = Boolean.getBoolean("simulation.walls");
    private final String farmShare = System.getProperty("simulation.farmShare");
    private final String farmRest = System.getProperty("simulation.farmRest");
    private final String label = System.getProperty("simulation.label", "");
    private final boolean lordGuilds = Boolean.getBoolean("simulation.lordGuilds");
    private final boolean lordBanners = Boolean.getBoolean("simulation.lordBanners");
    private final boolean archers = Boolean.getBoolean("simulation.archers");
    private final boolean morale =
            Boolean.parseBoolean(System.getProperty("simulation.morale", "true"));
    private final boolean lordLearning = Boolean.getBoolean("simulation.lordLearning");
    private final boolean shipments = !"false".equals(System.getProperty("simulation.shipments"));

    /** The days (at speed 3) at which the lords' points are noted. */
    private static final int[] CHECKPOINTS = {5, 10, 20, 30, 40};

    private ListAppender<ILoggingEvent> warnings;

    @BeforeEach
    void setUp() {
        warnings = new ListAppender<>();
        warnings.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(warnings);
        if (System.getProperty("simulation.sql") != null) {
            ((Logger) LoggerFactory.getLogger("org.hibernate.SQL")).setLevel(Level.DEBUG);
        }
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(warnings);
        dice.reset();
        jdbc.sql(
                        "update world_settings set speed = 1.0, npc_lord_count = 0,"
                                + " npc_think_minutes = 20, npc_difficulty = 'NORMAL', npc_custom ="
                                + " null, initialized_at = null, barbarians_per_new_player = 0,"
                                + " barbarian_walls_enabled = false, guilds_enabled = false,"
                                + " npc_guilds_enabled = false, npc_banners_enabled = false,"
                                + " npc_learning_enabled = false, npc_shipments_enabled = true")
                .update();
        worldService.reload();
    }

    /**
     * Learning lords (09 §15), with {@code -PsimLordLearning=true}: per lord the attacks it saw
     * (faded to the end) and its wall goals, and the human wins of the last days.
     */
    private String learningLines() {
        if (!lordLearning) {
            return "";
        }
        StringBuilder out = new StringBuilder("\nLearning lords (faded at the end):\n");
        Instant now = clock.instant();
        double halfLife = balance.general().learning().halfLifeHours();
        for (NpcBrain brain : brains.findAll()) {
            NpcMemory.Lessons lessons = brain.getMemory().lessons();
            NpcMemory.Tally seen = NpcLearning.fadedTo(lessons.seen(), now, halfLife);
            out.append(
                    String.format(
                            Locale.ROOT,
                            "  lord %d %-9s attacks %.1f scoutings %.1f cavalry share %s,"
                                    + " wall goals %d%n",
                            brain.getPlayerId(),
                            brain.getPersonality(),
                            seen.attacks(),
                            seen.scoutings(),
                            seen.infantry() + seen.cavalry() == 0
                                    ? "-"
                                    : String.format(
                                            Locale.ROOT,
                                            "%.2f",
                                            seen.cavalry() / (seen.infantry() + seen.cavalry())),
                            lessons.walls().size()));
        }
        return out.toString();
    }

    @Test
    void npcLordsOnly() throws Exception {
        run("NPC lords only", 0);
    }

    @Test
    void withTwoSleepingHumans() throws Exception {
        run("two sleeping humans", 2);
    }

    // ---- the run ----

    private void run(String title, int sleepers) throws Exception {
        long wallStart = System.nanoTime();
        if (!label.isEmpty()) {
            title = title + " " + label;
        }
        resetWorld();
        applyVariant();
        dice.use(new SeededRandomSource(seed));
        Instant start = clock.instant();
        if (start.isBefore(Instant.parse("2026-03-02T11:00:00Z"))) {
            clock.set(Instant.parse("2026-03-02T11:00:00Z")); // a Monday, noon in Berlin
            start = clock.instant();
        }
        initializer.initialize();
        List<Long> humans = new ArrayList<>();
        for (int i = 0; i < sleepers; i++) {
            humans.add(TestPlayer.register(mockMvc).playerId);
        }
        Set<Long> lordIds = new HashSet<>(ids("select id from player where npc"));

        Instant end = start.plus(Duration.ofDays(days));
        Instant nextSample = start;
        long[] orders = {0, 0}; // last build order id seen, orders queued in the quiet hours
        Map<Long, Double> academyDay = new HashMap<>();
        double[] waste = new double[2]; // wasted, produced, over all lords
        Map<Long, double[]> wasteByLord = new HashMap<>();
        long[] lastMovement = {0};
        List<Send> sends = new ArrayList<>();
        Map<Long, int[]> pointsByDay = new HashMap<>();
        int[] checkpoint = {0};
        while (clock.instant().isBefore(end)) {
            Instant now = clock.instant();
            Instant next =
                    jdbc.sql("select min(due_at) from game_event where status = 'PENDING'")
                            .query(Timestamp.class)
                            .optional()
                            .map(Timestamp::toInstant)
                            .orElse(end);
            Instant target = min(min(next, now.plus(MAX_STEP)), end);
            if (target.isAfter(now)) {
                clock.set(target);
            }
            processor.processDue(clock.instant());

            checkBuildOrders(orders);
            collectSends(humans, lastMovement, sends);
            if (!clock.instant().isBefore(nextSample)) {
                nextSample = clock.instant().plus(SAMPLE);
                sampleAcademies(start, academyDay);
                sampleWaste(wasteByLord, waste);
                samplePoints(start, checkpoint, pointsByDay);
            }
        }
        sampleAcademies(start, academyDay);
        Duration wall = Duration.ofNanos(System.nanoTime() - wallStart);

        Result result =
                collect(
                        title,
                        start,
                        wall,
                        lordIds,
                        humans,
                        academyDay,
                        wasteByLord,
                        (int) orders[1],
                        sends,
                        pointsByDay);
        String text = result.render() + shipmentLine() + lordGuildLines() + learningLines();
        System.out.println(text);
        Path dir = Path.of("build", "simulation");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(title.replace(' ', '-') + ".txt"), text);
        result.assertRules();
    }

    /**
     * The build orders lords queued since {@code state[0]}; counts those queued in the build quiet
     * window into {@code state[1]}.
     */
    private void checkBuildOrders(long[] state) {
        NpcBalanceSettings.Difficulty difficulty = balance.difficulty();
        jdbc.sql(
                        "select bo.id, bo.created_at from build_order bo join village v on v.id ="
                                + " bo.village_id join player p on p.id = v.player_id where p.npc"
                                + " and bo.id > ? order by bo.id")
                .param(state[0])
                .query(
                        (rs, row) -> {
                            state[0] = Math.max(state[0], rs.getLong("id"));
                            Instant at = rs.getTimestamp("created_at").toInstant();
                            if (NpcPlanning.inQuietWindow(
                                    at,
                                    difficulty.buildQuietFromTime(),
                                    difficulty.buildQuietToTime(),
                                    BERLIN)) {
                                state[1]++;
                            }
                            return null;
                        })
                .list();
    }

    /** A command of a lord towards a human village; a conquest train is one (09 §7). */
    private record Send(long lord, long human, Instant at) {}

    /**
     * The attack commands (scouts and trains included) lords sent to the humans' villages since
     * {@code last[0]}, as the caps count them: the clearing and the noble attack of a train leave
     * the same village for the same target in the same turn (one {@code processDue} call) and count
     * once.
     */
    private void collectSends(List<Long> humans, long[] last, List<Send> sends) {
        if (humans.isEmpty()) {
            return;
        }
        Set<String> trains = new HashSet<>();
        jdbc.sql(
                        "select m.id, m.owner_player_id, m.source_village_id, m.target_village_id,"
                                + " m.depart_at, t.player_id as human from movement m join player o"
                                + " on o.id = m.owner_player_id join village t on t.id ="
                                + " m.target_village_id join player h on h.id = t.player_id where"
                                + " m.kind = 'ATTACK' and o.npc and not h.npc and m.id > ? order by"
                                + " m.id")
                .param(last[0])
                .query(
                        (rs, row) -> {
                            last[0] = Math.max(last[0], rs.getLong("id"));
                            Instant at = rs.getTimestamp("depart_at").toInstant();
                            String train =
                                    rs.getLong("source_village_id")
                                            + "/"
                                            + rs.getLong("target_village_id")
                                            + "/"
                                            + at;
                            if (trains.add(train)) {
                                sends.add(
                                        new Send(
                                                rs.getLong("owner_player_id"),
                                                rs.getLong("human"),
                                                at));
                            }
                            return null;
                        })
                .list();
    }

    /** The most commands within any 24 hours (the caps' rolling window, 09 §7). */
    private static int maxPer24h(List<Instant> times) {
        List<Instant> sorted = times.stream().sorted().toList();
        int max = 0;
        int from = 0;
        for (int i = 0; i < sorted.size(); i++) {
            while (!sorted.get(from).isAfter(sorted.get(i).minus(Duration.ofHours(24)))) {
                from++;
            }
            max = Math.max(max, i - from + 1);
        }
        return max;
    }

    private void sampleAcademies(Instant start, Map<Long, Double> academyDay) {
        for (long lord :
                ids(
                        "select distinct v.player_id from village v join player p on p.id ="
                                + " v.player_id where p.npc and v.b_academy >= 1")) {
            academyDay.putIfAbsent(
                    lord, Duration.between(start, clock.instant()).toMinutes() / 1440.0);
        }
    }

    /**
     * Warehouse waste (§11 point 1): for every lord village and resource at (or above) the
     * capacity, the production of the sample interval counts as wasted.
     */
    private void sampleWaste(Map<Long, double[]> byLord, double[] total) {
        Instant now = clock.instant();
        double hours = SAMPLE.toMinutes() / 60.0;
        jdbc.sql(
                        "select v.player_id, v.wood, v.clay, v.iron, v.resources_updated_at,"
                                + " v.b_woodcutter, v.b_clay_pit, v.b_iron_mine, v.b_warehouse,"
                                + " p.production_factor from village v join player p on p.id ="
                                + " v.player_id where p.npc")
                .query(
                        (rs, row) -> {
                            long lord = rs.getLong("player_id");
                            double factor = rs.getDouble("production_factor");
                            Instant from = rs.getTimestamp("resources_updated_at").toInstant();
                            double capacity = Storage.warehouseCapacity(rs.getInt("b_warehouse"));
                            String[] stock = {"wood", "clay", "iron"};
                            String[] mine = {"b_woodcutter", "b_clay_pit", "b_iron_mine"};
                            double[] own = byLord.computeIfAbsent(lord, k -> new double[2]);
                            for (int i = 0; i < 3; i++) {
                                double perHour =
                                        Production.perHour(rs.getInt(mine[i]), SPEED, factor);
                                double now0 =
                                        Production.advance(
                                                rs.getDouble(stock[i]),
                                                perHour,
                                                capacity,
                                                from,
                                                now);
                                own[1] += perHour * hours;
                                total[1] += perHour * hours;
                                if (now0 >= capacity) {
                                    own[0] += perHour * hours;
                                    total[0] += perHour * hours;
                                }
                            }
                            return null;
                        })
                .list();
    }

    // ---- collecting ----

    private record LordRow(
            long id,
            String name,
            NpcPersonality personality,
            int points,
            int villages,
            int armyPop,
            int attacks,
            int won,
            int lost,
            int scouts,
            long loot,
            int conquests,
            int villagesLost,
            Double academyDay,
            Double firstConquestDay,
            int rounds,
            long warns,
            double wastePercent,
            double produced,
            int[] pointsByDay) {}

    private Result collect(
            String title,
            Instant start,
            Duration wall,
            Set<Long> lordIds,
            List<Long> humans,
            Map<Long, Double> academyDay,
            Map<Long, double[]> wasteByLord,
            int quietBuildOrders,
            List<Send> sends,
            Map<Long, int[]> pointsByDay) {
        String popExpr = popExpression("");
        Map<Long, Integer> army = new HashMap<>();
        jdbc.sql(
                        "select v.player_id as pid, sum("
                                + popExpr
                                + ") as pop from unit_stack s join village v on v.id ="
                                + " s.home_village_id where v.player_id is not null group by"
                                + " v.player_id")
                .query(
                        (rs, row) -> {
                            army.merge(rs.getLong("pid"), rs.getInt("pop"), Integer::sum);
                            return null;
                        })
                .list();
        jdbc.sql(
                        "select owner_player_id as pid, sum("
                                + popExpr
                                + ") as pop from movement s group by owner_player_id")
                .query(
                        (rs, row) -> {
                            army.merge(rs.getLong("pid"), rs.getInt("pop"), Integer::sum);
                            return null;
                        })
                .list();
        Map<Long, Integer> rounds = new HashMap<>();
        jdbc.sql(
                        "select ref_id, count(*) as n from game_event where kind = 'NPC_THINK' and"
                                + " status = 'DONE' group by ref_id")
                .query(
                        (rs, row) -> {
                            rounds.put(rs.getLong("ref_id"), rs.getInt("n"));
                            return null;
                        })
                .list();
        Map<Long, long[]> battles = new HashMap<>(); // attacks, won, lost, scouts, loot, conq, lost
        Map<Long, Double> firstConquest = new HashMap<>();
        jdbc.sql(
                        """
                        select player_id,
                          count(*) filter (where kind = 'ATTACK') as attacks,
                          count(*) filter (where kind = 'ATTACK'
                                           and payload->>'outcome' = 'ATTACKER_WON') as won,
                          count(*) filter (where kind = 'ATTACK'
                                           and payload->>'outcome' = 'DEFENDER_WON') as lost,
                          count(*) filter (where kind = 'SCOUT') as scouts,
                          coalesce(sum(case when kind = 'ATTACK' then
                              (payload->'loot'->>'wood')::bigint + (payload->'loot'->>'clay')::bigint
                              + (payload->'loot'->>'iron')::bigint else 0 end), 0) as loot,
                          count(*) filter (where kind = 'CONQUEST') as conquests,
                          count(*) filter (where kind = 'VILLAGE_LOST') as lostv,
                          min(happened_at) filter (where kind = 'CONQUEST') as first
                        from report group by player_id
                        """)
                .query(
                        (rs, row) -> {
                            battles.put(
                                    rs.getLong("player_id"),
                                    new long[] {
                                        rs.getLong("attacks"),
                                        rs.getLong("won"),
                                        rs.getLong("lost"),
                                        rs.getLong("scouts"),
                                        rs.getLong("loot"),
                                        rs.getLong("conquests"),
                                        rs.getLong("lostv")
                                    });
                            Timestamp first = rs.getTimestamp("first");
                            if (first != null) {
                                firstConquest.put(
                                        rs.getLong("player_id"),
                                        Duration.between(start, first.toInstant()).toMinutes()
                                                / 1440.0);
                            }
                            return null;
                        })
                .list();
        Map<Long, Long> warnsByLord = new HashMap<>();
        long warnsTotal = 0;
        for (ILoggingEvent event : List.copyOf(warnings.list)) {
            if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                warnsTotal++;
                String message = event.getFormattedMessage();
                for (long id : lordIds) {
                    if (message.contains("NPC lord " + id + ":")
                            || message.contains("NPC lord " + id + " ")) {
                        warnsByLord.merge(id, 1L, Long::sum);
                    }
                }
            }
        }

        List<LordRow> rows = new ArrayList<>();
        jdbc.sql(
                        "select p.id, p.name, p.points, p.village_count, b.personality from player"
                                + " p join npc_brain b on b.player_id = p.id order by p.id")
                .query(
                        (rs, row) -> {
                            long id = rs.getLong("id");
                            long[] b = battles.getOrDefault(id, new long[7]);
                            double[] w = wasteByLord.getOrDefault(id, new double[2]);
                            rows.add(
                                    new LordRow(
                                            id,
                                            rs.getString("name"),
                                            NpcPersonality.valueOf(rs.getString("personality")),
                                            rs.getInt("points"),
                                            rs.getInt("village_count"),
                                            army.getOrDefault(id, 0),
                                            (int) b[0],
                                            (int) b[1],
                                            (int) b[2],
                                            (int) b[3],
                                            b[4],
                                            (int) b[5],
                                            (int) b[6],
                                            academyDay.get(id),
                                            firstConquest.get(id),
                                            rounds.getOrDefault(id, 0),
                                            warnsByLord.getOrDefault(id, 0L),
                                            w[1] == 0 ? 0 : 100.0 * w[0] / w[1],
                                            w[1],
                                            pointsByDay.getOrDefault(
                                                    id, new int[CHECKPOINTS.length])));
                            return null;
                        })
                .list();

        // what the humans went through
        List<Instant> arrivals = new ArrayList<>();
        int quietArrivals = 0;
        NpcBalanceSettings.Balance general = balance.general();
        for (long human : humans) {
            for (Instant at :
                    jdbc
                            .sql(
                                    "select happened_at from report where player_id = ? and kind"
                                            + " = 'DEFENSE' order by happened_at")
                            .param(human)
                            .query(Timestamp.class)
                            .list()
                            .stream()
                            .map(Timestamp::toInstant)
                            .toList()) {
                arrivals.add(at);
                if (NpcPlanning.inQuietWindow(
                        at, general.quietFromTime(), general.quietToTime(), general.zone())) {
                    quietArrivals++;
                }
            }
        }
        int maxPer24h = 0;
        for (long human : humans) {
            List<Instant> own =
                    jdbc
                            .sql(
                                    "select happened_at from report where player_id = ? and kind"
                                            + " = 'DEFENSE' order by happened_at")
                            .param(human)
                            .query(Timestamp.class)
                            .list()
                            .stream()
                            .map(Timestamp::toInstant)
                            .toList();
            int from = 0;
            for (int i = 0; i < own.size(); i++) {
                while (own.get(i).isAfter(own.get(from).plus(Duration.ofHours(24)))) {
                    from++;
                }
                maxPer24h = Math.max(maxPer24h, i - from + 1);
            }
        }
        int maxSendsPerHuman = 0;
        int maxSendsPerLord = 0;
        for (long human : humans) {
            maxSendsPerHuman =
                    Math.max(
                            maxSendsPerHuman,
                            maxPer24h(
                                    sends.stream()
                                            .filter(x -> x.human() == human)
                                            .map(Send::at)
                                            .toList()));
            for (long lord : lordIds) {
                maxSendsPerLord =
                        Math.max(
                                maxSendsPerLord,
                                maxPer24h(
                                        sends.stream()
                                                .filter(x -> x.human() == human && x.lord() == lord)
                                                .map(Send::at)
                                                .toList()));
            }
        }
        int humansLostVillages = 0;
        int humansWithoutVillage = 0;
        for (long human : humans) {
            humansLostVillages += battles.getOrDefault(human, new long[7])[6];
            if (jdbc.sql("select village_count from player where id = ?")
                            .param(human)
                            .query(Integer.class)
                            .single()
                    == 0) {
                humansWithoutVillage++;
            }
        }
        int barbarians =
                jdbc.sql("select count(*) from village where player_id is null")
                        .query(Integer.class)
                        .single();
        int npcVillages =
                jdbc.sql(
                                "select count(*) from village v join player p on p.id = v.player_id"
                                        + " where p.npc")
                        .query(Integer.class)
                        .single();
        int failed =
                jdbc.sql("select count(*) from game_event where status = 'FAILED'")
                        .query(Integer.class)
                        .single();
        List<String> playerConquests = new ArrayList<>();
        jdbc.sql(
                        "select r.happened_at, p.name as winner,"
                                + " r.payload->'village'->>'playerName' as loser, (select"
                                + " max(e.happened_at) from report e where e.kind = 'CONQUEST' and"
                                + " e.payload->'village'->>'id' = r.payload->'village'->>'id' and"
                                + " e.happened_at < r.happened_at) as held_since from report r join"
                                + " player p on p.id = r.player_id where r.kind = 'CONQUEST' and"
                                + " r.payload->'village'->>'playerId' is not null order by"
                                + " r.happened_at, r.id")
                .query(
                        (rs, row) -> {
                            Timestamp since = rs.getTimestamp("held_since");
                            playerConquests.add(
                                    String.format(
                                                    Locale.ROOT,
                                                    "day %.1f: %s took a village of %s",
                                                    Duration.between(
                                                                            start,
                                                                            rs.getTimestamp(
                                                                                            "happened_at")
                                                                                    .toInstant())
                                                                    .toMinutes()
                                                            / 1440.0,
                                                    rs.getString("winner"),
                                                    rs.getString("loser"))
                                            + (since == null
                                                    ? ""
                                                    : String.format(
                                                            Locale.ROOT,
                                                            " (conquered by them on day %.1f)",
                                                            Duration.between(
                                                                                    start,
                                                                                    since
                                                                                            .toInstant())
                                                                            .toMinutes()
                                                                    / 1440.0)));
                            return null;
                        })
                .list();
        Map<String, Integer> events = new java.util.TreeMap<>();
        jdbc.sql(
                        "select kind, count(*) as n from game_event where status = 'DONE' group by"
                                + " kind")
                .query(
                        (rs, row) -> {
                            events.put(rs.getString("kind"), rs.getInt("n"));
                            return null;
                        })
                .list();
        int braked = 0;
        for (long id : lordIds) {
            if (jdbc.sql("select memory->'stance'->>'braked' from npc_brain where player_id = ?")
                    .param(id)
                    .query(String.class)
                    .optional()
                    .map("true"::equals)
                    .orElse(false)) {
                braked++;
            }
        }
        return new Result(
                title,
                wall,
                rows,
                humans.size(),
                new HumanStats(
                        arrivals.size(),
                        quietArrivals,
                        maxPer24h,
                        sends.size(),
                        maxSendsPerHuman,
                        maxSendsPerLord,
                        humansLostVillages,
                        humansWithoutVillage),
                barbarians,
                npcVillages,
                failed,
                warnsTotal,
                quietBuildOrders,
                braked,
                balance.difficulty().attacksPerHumanPerDay(),
                balance.difficulty().attacksPerPlayerPerDay(),
                events,
                playerConquests,
                bannerLines());
    }

    /**
     * Lord banners (09 §14.9): what each lord holds by family and tier (equipped ones counted too),
     * its paladin level, and the finds and thefts of the run.
     */
    private List<String> bannerLines() {
        List<String> lines = new ArrayList<>();
        jdbc.sql(
                        """
                        select p.name,
                          count(*) filter (where b.type not like '%\\_STANDARD' and b.tier = 1) as b1,
                          count(*) filter (where b.type not like '%\\_STANDARD' and b.tier = 2) as b2,
                          count(*) filter (where b.type not like '%\\_STANDARD' and b.tier = 3) as b3,
                          count(*) filter (where b.type like '%\\_STANDARD') as std,
                          count(*) filter (where b.equipped_village_id is not null) as up,
                          coalesce((select max(r.level) from research_order r
                                    where r.player_id = p.id and r.unit = 'PALADIN'), 1) as paladin
                        from player p left join banner b on b.owner_id = p.id
                        where p.npc group by p.id, p.name order by p.id
                        """)
                .query(
                        (rs, row) ->
                                lines.add(
                                        String.format(
                                                Locale.ROOT,
                                                "  %-20s banners I/II/III %d/%d/%d, standards %d,"
                                                        + " equipped %d, paladin level %d",
                                                rs.getString("name"),
                                                rs.getInt("b1"),
                                                rs.getInt("b2"),
                                                rs.getInt("b3"),
                                                rs.getInt("std"),
                                                rs.getInt("up"),
                                                rs.getInt("paladin"))))
                .list();
        Map<String, Integer> kinds = new java.util.TreeMap<>();
        jdbc.sql(
                        """
                        select e.kind || case
                                 when f.npc and t.npc then ' lord->lord'
                                 when f.npc and t.id is null then ' lord->gone'
                                 when f.npc then ' lord->human'
                                 when t.npc then ' ->lord'
                                 else '' end as k, count(*) as n
                        from banner_event e
                        left join player f on f.id = e.from_player_id
                        left join player t on t.id = e.to_player_id
                        where e.kind in ('FOUND', 'STOLEN', 'FUSED', 'CONQUERED')
                          and (f.npc or t.npc)
                        group by k
                        """)
                .query(
                        (rs, row) -> {
                            kinds.put(rs.getString("k"), rs.getInt("n"));
                            return null;
                        })
                .list();
        lines.add("  banner log of lords: " + kinds);
        return lines;
    }

    private String popExpression(String prefix) {
        List<String> terms = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            terms.add(
                    prefix
                            + "u_"
                            + type.name().toLowerCase(Locale.ROOT)
                            + " * "
                            + gameData.unit(type).pop());
        }
        return String.join(" + ", terms);
    }

    private record HumanStats(
            int arrivals,
            int quietArrivals,
            int maxArrivalsPer24h,
            int sends,
            int maxSendsPer24h,
            int maxSendsPerLordPer24h,
            int villagesLost,
            int wiped) {}

    private record Result(
            String title,
            Duration wall,
            List<LordRow> lords,
            int humans,
            HumanStats human,
            int barbarians,
            int npcVillages,
            int failedEvents,
            long warns,
            int quietBuildOrders,
            int braked,
            int humanCap,
            int lordCap,
            Map<String, Integer> events,
            List<String> playerConquests,
            List<String> bannerLines) {

        String render() {
            StringBuilder out = new StringBuilder();
            out.append("\n==== NPC simulation: ").append(title).append(" ====\n");
            out.append(
                    "lord                 pers  pts  vill army  atk won lost scout  loot conq lostV"
                            + " academy 1stConq rounds warn waste%\n");
            for (LordRow l : lords) {
                out.append(
                        String.format(
                                Locale.ROOT,
                                "%-20s %-4s %5d %4d %5d %4d %3d %4d %5d %5d %4d %5s %7s %7s %6d %4d"
                                        + " %5.1f%n",
                                l.name(),
                                l.personality().name().substring(0, 3),
                                l.points(),
                                l.villages(),
                                l.armyPop(),
                                l.attacks(),
                                l.won(),
                                l.lost(),
                                l.scouts(),
                                l.loot() / 1000,
                                l.conquests(),
                                l.villagesLost(),
                                day(l.academyDay()),
                                day(l.firstConquestDay()),
                                l.rounds(),
                                l.warns(),
                                l.wastePercent()));
            }
            out.append("(loot in thousands; days at speed 3)\n");
            out.append("points by day (speed 3) and loot as a share of the own production:\n");
            StringBuilder head = new StringBuilder("lord                 pers");
            for (int d : CHECKPOINTS) {
                head.append(String.format(Locale.ROOT, " %6s", "d" + d));
            }
            out.append(head).append("    end  loot/prod\n");
            for (LordRow l : lords) {
                StringBuilder line =
                        new StringBuilder(
                                String.format(
                                        Locale.ROOT,
                                        "%-20s %-4s",
                                        l.name(),
                                        l.personality().name().substring(0, 3)));
                for (int v : l.pointsByDay()) {
                    line.append(String.format(Locale.ROOT, " %6d", v));
                }
                line.append(
                        String.format(
                                Locale.ROOT,
                                " %6d %8.0f %%%n",
                                l.points(),
                                l.produced() == 0 ? 0 : 100.0 * l.loot() / l.produced()));
                out.append(line);
            }
            double loot = lords.stream().mapToLong(LordRow::loot).sum();
            double produced = lords.stream().mapToDouble(LordRow::produced).sum();
            out.append(
                    String.format(
                            Locale.ROOT,
                            "all lords: loot %.0f k, own production %.0f k, loot/prod %.0f %%,"
                                    + " average points %.0f%n",
                            loot / 1000,
                            produced / 1000,
                            produced == 0 ? 0 : 100 * loot / produced,
                            avg(lords, LordRow::points)));
            out.append("per personality (averages):\n");
            for (NpcPersonality p : NpcPersonality.values()) {
                List<LordRow> of = lords.stream().filter(l -> l.personality() == p).toList();
                if (of.isEmpty()) {
                    continue;
                }
                out.append(
                        String.format(
                                Locale.ROOT,
                                "  %-10s points %7.0f  villages %4.1f  army %6.0f  attacks %5.1f"
                                        + "  won %5.1f  conquests %4.1f%n",
                                p,
                                avg(of, LordRow::points),
                                avg(of, LordRow::villages),
                                avg(of, LordRow::armyPop),
                                avg(of, LordRow::attacks),
                                avg(of, LordRow::won),
                                avg(of, LordRow::conquests)));
            }
            out.append(
                    String.format(
                            Locale.ROOT,
                            "world: barbarian villages left %d, NPC villages %d, FAILED events %d,"
                                    + " WARNs %d, run time %d s%n",
                            barbarians,
                            npcVillages,
                            failedEvents,
                            warns,
                            wall.toSeconds()));
            out.append("villages of players taken by lords:")
                    .append(playerConquests.isEmpty() ? " none\n" : "\n");
            playerConquests.forEach(c -> out.append("  ").append(c).append('\n'));
            out.append("events handled: ").append(events).append('\n');
            out.append("lord banners:\n");
            bannerLines.forEach(line -> out.append(line).append('\n'));
            out.append(
                    String.format(
                            Locale.ROOT,
                            "build orders queued in the build quiet hours: %d; lords braked at the"
                                    + " end: %d of %d%n",
                            quietBuildOrders,
                            braked,
                            lords.size()));
            if (humans > 0) {
                out.append(
                        String.format(
                                Locale.ROOT,
                                "sleeping humans (%d): %d arrivals (max %d per rolling 24 h), %d"
                                        + " commands sent (max %d per human and 24 h, cap %d; max"
                                        + " %d per lord, human and 24 h, cap %d), %d in the quiet"
                                        + " hours, villages lost %d, wiped out %d%n",
                                humans,
                                human.arrivals(),
                                human.maxArrivalsPer24h(),
                                human.sends(),
                                human.maxSendsPer24h(),
                                humanCap,
                                human.maxSendsPerLordPer24h(),
                                lordCap,
                                human.quietArrivals(),
                                human.villagesLost(),
                                human.wiped()));
            }
            out.append("targets (09 §11):\n");
            for (Target t : targets()) {
                out.append("  ").append(t.ok() ? "ok   " : "MISS ").append(t.text()).append('\n');
            }
            return out.toString();
        }

        private record Target(boolean ok, String text) {}

        private List<Target> targets() {
            List<Target> t = new ArrayList<>();
            double waste = lords.stream().mapToDouble(LordRow::wastePercent).max().orElse(0);
            t.add(
                    new Target(
                            quietBuildOrders == 0 && failedEvents == 0 && warns == 0 && waste < 15,
                            "1 healthy: quiet-hour orders "
                                    + quietBuildOrders
                                    + ", FAILED "
                                    + failedEvents
                                    + ", WARNs "
                                    + warns
                                    + ", worst warehouse waste "
                                    + Math.round(waste)
                                    + " %"));
            List<Integer> points = lords.stream().map(LordRow::points).sorted().toList();
            double median =
                    points.isEmpty()
                            ? 0
                            : (points.get((points.size() - 1) / 2) + points.get(points.size() / 2))
                                    / 2.0;
            double minAvg = Double.MAX_VALUE;
            double maxAvg = 0;
            for (NpcPersonality p : NpcPersonality.values()) {
                double a =
                        lords.stream()
                                .filter(l -> l.personality() == p)
                                .mapToInt(LordRow::points)
                                .average()
                                .orElse(0);
                if (a > 0) {
                    minAvg = Math.min(minAvg, a);
                    maxAvg = Math.max(maxAvg, a);
                }
            }
            boolean perPersonality = maxAvg <= 1.5 * minAvg;
            boolean aroundMedian =
                    lords.stream()
                            .allMatch(l -> l.points() >= 0.5 * median && l.points() <= 2 * median);
            int most = lords.stream().mapToInt(LordRow::villages).max().orElse(0);
            boolean spread = npcVillages == 0 || most <= 0.4 * npcVillages;
            t.add(
                    new Target(
                            perPersonality && aroundMedian && spread,
                            String.format(
                                    Locale.ROOT,
                                    "2 even: personality averages within 1.5x (%.2fx), every lord"
                                            + " within 0.5-2x the median (%s), most villages of one"
                                            + " lord %d of %d",
                                    minAvg == 0 ? 0 : maxAvg / minAvg,
                                    aroundMedian ? "yes" : "NO",
                                    most,
                                    npcVillages)));
            long conquered = lords.stream().filter(l -> l.conquests() > 0).count();
            int offWon =
                    lords.stream()
                            .filter(l -> l.personality() == NpcPersonality.OFFENSIVE)
                            .mapToInt(l -> l.won() - l.lost())
                            .sum();
            t.add(
                    new Target(
                            humans > 0 || (conquered >= 4 && offWon > 0),
                            "3 alive: "
                                    + conquered
                                    + " of "
                                    + lords.size()
                                    + " lords conquered a village (need 4; not asked in the run"
                                    + " with humans, the brake holds them back), OFFENSIVE won"
                                    + " minus lost "
                                    + offWon));
            t.add(
                    new Target(
                            human.quietArrivals() == 0 && human.wiped() == 0 && capsKept(),
                            "4 fair: arrivals in the quiet hours "
                                    + human.quietArrivals()
                                    + ", humans wiped out "
                                    + human.wiped()
                                    + ", max commands per human and 24 h "
                                    + human.maxSendsPer24h()
                                    + " (cap "
                                    + humanCap
                                    + "), per lord and human "
                                    + human.maxSendsPerLordPer24h()
                                    + " (cap "
                                    + lordCap
                                    + ")"));
            t.add(
                    new Target(
                            wall.toMinutes() < 10,
                            "5 fast enough: " + wall.toSeconds() + " s (limit 600)"));
            return t;
        }

        /** Fails on the hard rules; the balance targets are only printed. */
        void assertRules() {
            assertThat(quietBuildOrders).as("build orders in the build quiet hours").isZero();
            assertThat(failedEvents).as("FAILED events").isZero();
            assertThat(human.quietArrivals()).as("arrivals at humans in the quiet hours").isZero();
            assertThat(human.wiped()).as("humans who lost every village").isZero();
            assertThat(human.maxSendsPer24h())
                    .as("commands of all lords to one human in 24 h")
                    .isLessThanOrEqualTo(humanCap);
            assertThat(human.maxSendsPerLordPer24h())
                    .as("commands of one lord to one human in 24 h")
                    .isLessThanOrEqualTo(lordCap);
        }

        private boolean capsKept() {
            return human.maxSendsPer24h() <= humanCap && human.maxSendsPerLordPer24h() <= lordCap;
        }

        private static String day(Double day) {
            return day == null ? "-" : String.format(Locale.ROOT, "%.1f", day);
        }

        private static double avg(List<LordRow> rows, java.util.function.ToIntFunction<LordRow> f) {
            return rows.stream().mapToInt(f).average().orElse(0);
        }
    }

    // ---- helpers ----

    /** The lords' points at each checkpoint day the clock has passed (§11 summary, v1.3). */
    private void samplePoints(Instant start, int[] next, Map<Long, int[]> byLord) {
        double day = Duration.between(start, clock.instant()).toMinutes() / 1440.0;
        while (next[0] < CHECKPOINTS.length && day >= CHECKPOINTS[next[0]]) {
            int index = next[0]++;
            jdbc.sql("select id, points from player where npc")
                    .query(
                            (rs, row) -> {
                                byLord.computeIfAbsent(
                                                        rs.getLong("id"),
                                                        k -> new int[CHECKPOINTS.length])[index] =
                                        rs.getInt("points");
                                return null;
                            })
                    .list();
        }
    }

    /** The balance variant of this run (Gradle properties, see the class comment). */
    private void applyVariant() {
        jdbc.sql("update world_settings set barbarian_walls_enabled = ?").param(walls).update();
        // lord guilds (09 §13): -PsimLordGuilds=true
        jdbc.sql("update world_settings set guilds_enabled = ?, npc_guilds_enabled = ?")
                .param(lordGuilds)
                .param(lordGuilds)
                .update();
        // lord banners (09 §14): -PsimLordBanners=true
        jdbc.sql("update world_settings set npc_banners_enabled = ?").param(lordBanners).update();
        // archers (1.6, 02 §5.6): -PsimArchers=true
        jdbc.sql("update world_settings set archers_enabled = ?").param(archers).update();
        // morale (1.6, 02 §8.2): on like in a new world unless -PsimMorale=false
        jdbc.sql("update world_settings set morale_enabled = ?").param(morale).update();
        // learning lords (09 §15): -PsimLordLearning=true
        jdbc.sql("update world_settings set npc_learning_enabled = ?").param(lordLearning).update();
        // lord shipments (09 §5.5): on unless -PsimShipments=false
        jdbc.sql("update world_settings set npc_shipments_enabled = ?").param(shipments).update();
        if (farmShare != null || farmRest != null) {
            NpcBalanceSettings.Difficulty normal =
                    balance.general().difficulties().get(NpcDifficulty.NORMAL);
            NpcBalanceSettings.Difficulty custom =
                    new NpcBalanceSettings.Difficulty(
                            normal.productionFactor(),
                            normal.boostBelow(),
                            normal.boostFactor(),
                            normal.leadMargin(),
                            normal.attacksPerPlayerPerDay(),
                            normal.attacksPerHumanPerDay(),
                            normal.quietHours(),
                            normal.spareLastVillage(),
                            normal.buildQuietFrom(),
                            normal.buildQuietTo(),
                            farmShare == null
                                    ? normal.farmLootShare()
                                    : Double.parseDouble(farmShare),
                            farmRest == null ? normal.farmRest() : Boolean.parseBoolean(farmRest));
            jdbc.sql(
                            "update world_settings set npc_difficulty = 'CUSTOM', npc_custom ="
                                    + " cast(? as jsonb)")
                    .param(new NpcDifficultyConverter().convertToDatabaseColumn(custom))
                    .update();
        }
        worldService.reload();
        System.out.printf(
                Locale.ROOT,
                "variant: walls %s, lord guilds %s, morale %s, difficulty %s (farm share %s, farm"
                        + " rest %s), farm cooldown %s min%n",
                walls,
                lordGuilds,
                morale,
                worldService.settings().npcDifficulty(),
                balance.difficulty().farmLootShare(),
                balance.difficulty().farmRest(),
                balance.general().farmCooldownMinutes());
    }

    private void resetWorld() {
        jdbc.sql(
                        "truncate table report, movement, unit_stack, build_order, recruit_order,"
                                + " game_event, npc_brain, chat_message, village, player, account,"
                                + " guild restart identity cascade")
                .update();
        jdbc.sql(
                        "update world_settings set speed = ?, npc_lord_count = ?, npc_think_minutes"
                                + " = ?, npc_difficulty = 'NORMAL', npc_custom = null,"
                                + " initialized_at = null, barbarians_per_new_player = 3,"
                                + " barbarian_initial_count = 30")
                .param(SPEED)
                .param(LORDS)
                .param(thinkMinutes)
                .update();
        worldService.reload();
    }

    /** The lords' shipments of the run (09 §5.5): trips and resources moved. */
    private String shipmentLine() {
        Map<String, Object> row =
                jdbc.sql(
                                "select count(*) as trips, coalesce(sum(wood + clay + iron), 0) as"
                                        + " moved from trade_transport")
                        .query()
                        .singleRow();
        return String.format(
                Locale.ROOT,
                "lord shipments (%s): %d trips, %d k resources moved%n",
                shipments ? "on" : "off",
                ((Number) row.get("trips")).longValue(),
                ((Number) row.get("moved")).longValue() / 1000);
    }

    /**
     * Lords against lords and the lord guilds at the end (09 §13.9): attacks and scoutings of a
     * lord on another lord's village, supports one lord sent another, and each guild with its
     * members and points.
     */
    private String lordGuildLines() {
        String lords = "(select id from player where npc)";
        int fights =
                jdbc.sql(
                                "select count(*) from report r where r.kind in ('ATTACK', 'SCOUT')"
                                        + " and r.player_id in "
                                        + lords
                                        + " and (r.payload -> 'defender' ->> 'playerId')::bigint in"
                                        + " "
                                        + lords)
                        .query(Integer.class)
                        .single();
        int supports =
                jdbc.sql(
                                "select count(*) from report r where r.kind = 'SUPPORT_ARRIVED' and"
                                    + " r.player_id = (r.payload -> 'from' ->> 'playerId')::bigint"
                                    + " and r.player_id in "
                                        + lords
                                        + " and (r.payload -> 'to' ->> 'playerId')::bigint in "
                                        + lords
                                        + " and (r.payload -> 'to' ->> 'playerId')::bigint <>"
                                        + " r.player_id")
                        .query(Integer.class)
                        .single();
        StringBuilder out =
                new StringBuilder(
                        String.format(
                                Locale.ROOT,
                                "lords against lords: %d attacks and scoutings, %d supports to"
                                        + " another lord%n",
                                fights,
                                supports));
        jdbc.sql(
                        "select g.name, g.tag, string_agg(p.name || ' (day ' ||"
                            + " to_char(extract(epoch from m.joined_at - w.started_at) / 86400,"
                            + " 'FM990.0') || ')', ', ' order by m.joined_at) as members,"
                            + " sum(p.points) as points from guild g join guild_member m on"
                            + " m.guild_id = g.id join player p on p.id = m.player_id cross join"
                            + " (select min(created_at) as started_at from player) w group by g.id"
                            + " order by g.id")
                .query(
                        (rs, n) ->
                                out.append(
                                        String.format(
                                                Locale.ROOT,
                                                "guild %s [%s] %d points: %s (joined)%n",
                                                rs.getString("name"),
                                                rs.getString("tag"),
                                                rs.getLong("points"),
                                                rs.getString("members"))))
                .list();
        return out.toString();
    }

    private List<Long> ids(String sql) {
        return jdbc.sql(sql).query(Long.class).list();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Seeded dice for a repeatable run. */
    private static final class SeededRandomSource implements RandomSource {
        private final Random random;

        SeededRandomSource(long seed) {
            this.random = new Random(seed);
        }

        @Override
        public double nextDouble() {
            return random.nextDouble();
        }

        @Override
        public int nextInt(int minIncl, int maxIncl) {
            return minIncl + random.nextInt(maxIncl - minIncl + 1);
        }
    }
}
