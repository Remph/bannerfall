package de.bannerfall.abuse;

import de.bannerfall.farm.FarmService;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.rules.Storage;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks for states the rules never allow (M14e, 01-architecture §4.12): what an exploit that got
 * through anyway would leave behind. Read only; it repairs nothing (that is the host's call, with
 * the cheats or SQL). Every night and on demand ({@code POST /api/admin/invariants}, CLI {@code
 * check}); each problem is a WARN line, the last result shows in the status.
 *
 * <p>Resources "far above the capacity" are more than {@value #CAPACITY_FACTOR} times the warehouse
 * capacity: production and every receipt stop at the capacity, only the host cheats ignore it.
 */
@Service
public class InvariantCheck {

    private static final Logger log = LoggerFactory.getLogger("bannerfall.abuse");

    static final int CAPACITY_FACTOR = 2;

    /** Examples per problem (ids to look at); the count is complete. */
    static final int EXAMPLES = 10;

    private final JdbcClient jdbc;
    private final GameData gameData;
    private final Clock clock;
    private volatile InvariantReportDto last;

    InvariantCheck(JdbcClient jdbc, GameData gameData, Clock clock) {
        this.jdbc = jdbc;
        this.gameData = gameData;
        this.clock = clock;
    }

    /** Every night at 03:50 UTC. */
    @Scheduled(cron = "0 50 3 * * *", zone = "UTC")
    public void nightly() {
        run();
    }

    /** Runs every check now; logs each problem at WARN. */
    @Transactional(readOnly = true)
    public InvariantReportDto run() {
        List<InvariantProblemDto> problems = new ArrayList<>();
        add(problems, "NEGATIVE_RESOURCES", villageIds("wood < 0 or clay < 0 or iron < 0"));
        add(problems, "LOYALTY_OUT_OF_RANGE", villageIds("loyalty < 0 or loyalty > 100"));
        add(problems, "LEVEL_OUT_OF_RANGE", villageIds(levelsOutOfRange()));
        add(problems, "RESOURCES_ABOVE_CAPACITY", resourcesAboveCapacity());
        add(
                problems,
                "NEGATIVE_UNITS",
                ids(
                        "select 'stack ' || id from unit_stack where "
                                + anyUnitNegative()
                                + " union all select 'movement ' || id from movement where "
                                + anyUnitNegative()
                                + " union all select 'recruit order ' || id from recruit_order"
                                + " where count <= 0 or completed_count < 0"
                                + " or completed_count > count"
                                + " union all select 'dismiss order ' || id from dismiss_order"
                                + " where count <= 0 or completed_count < 0"
                                + " or completed_count > count"));
        add(problems, "BAD_FARM_TARGET", ids(badFarmTargets()));
        add(
                problems,
                "MOVEMENT_WITHOUT_EVENT",
                ids(withoutEvent("movement", "MOVEMENT_ARRIVE", "true")));
        add(
                problems,
                "BUILD_ORDER_WITHOUT_EVENT",
                ids(withoutEvent("build_order", "BUILD_COMPLETE", "true")));
        add(
                problems,
                "TRANSPORT_WITHOUT_EVENT",
                ids(withoutEvent("trade_transport", "TRADE_ARRIVE", "not t.arrived")));
        add(
                problems,
                "STACK_WITHOUT_VILLAGE",
                ids(
                        """
                        select 'stack ' || s.id from unit_stack s
                        left join village h on h.id = s.home_village_id
                        left join village l on l.id = s.location_village_id
                        where h.id is null or l.id is null
                        """));
        InvariantReportDto report = new InvariantReportDto(clock.instant(), problems);
        for (InvariantProblemDto problem : problems) {
            log.warn(
                    "Invariant check: {} × {}, e.g. {}",
                    problem.count(),
                    problem.kind(),
                    problem.examples());
        }
        if (problems.isEmpty()) {
            log.info("Invariant check: nothing wrong");
        }
        last = report;
        return report;
    }

    /** The result of the last run since the server started; null before the first one. */
    public InvariantReportDto last() {
        return last;
    }

    private static void add(List<InvariantProblemDto> problems, String kind, List<String> found) {
        if (!found.isEmpty()) {
            problems.add(
                    new InvariantProblemDto(
                            kind, found.size(), found.stream().limit(EXAMPLES).toList()));
        }
    }

    private List<String> villageIds(String condition) {
        return ids("select 'village ' || id from village where " + condition + " order by id");
    }

    private List<String> ids(String sql) {
        return jdbc.sql(sql).query(String.class).list();
    }

    private String levelsOutOfRange() {
        return Stream.of(BuildingType.values())
                .map(
                        type -> {
                            String column = "b_" + type.name().toLowerCase(Locale.ROOT);
                            return column
                                    + " < 0 or "
                                    + column
                                    + " > "
                                    + gameData.building(type).maxLevel();
                        })
                .collect(Collectors.joining(" or "));
    }

    /**
     * Farm list entries the API can't make (02-game-rules §18): a template with a count outside 0 …
     * 30 000, no unit at all, a noble, the paladin or a banner thief, an entry that targets its own
     * village, and lists with more than 50 entries.
     */
    private static String badFarmTargets() {
        List<String> columns =
                Stream.of(UnitType.values())
                        .map(type -> "u_" + type.name().toLowerCase(Locale.ROOT))
                        .toList();
        String outOfRange =
                columns.stream()
                        .map(c -> c + " < 0 or " + c + " > " + RecruitRules.MAX_COUNT)
                        .collect(Collectors.joining(" or "));
        return "select 'farm target ' || id from farm_target where "
                + outOfRange
                + " or "
                + String.join(" + ", columns)
                + " = 0 or u_noble > 0 or u_paladin > 0 or u_banner_thief > 0"
                + " or village_id = target_village_id"
                + " union all select 'farm list of village ' || village_id from farm_target"
                + " group by village_id having count(*) > "
                + FarmService.MAX_TARGETS;
    }

    private static String anyUnitNegative() {
        return Stream.of(UnitType.values())
                .map(type -> "u_" + type.name().toLowerCase(Locale.ROOT) + " < 0")
                .collect(Collectors.joining(" or ", "(", ")"));
    }

    /** Rows of {@code table} (alias {@code t}) matching {@code condition} with no live event. */
    private static String withoutEvent(String table, String kind, String condition) {
        return "select '"
                + table.replace('_', ' ')
                + " ' || t.id from "
                + table
                + " t where "
                + condition
                + " and not exists (select 1 from game_event e where e.kind = '"
                + kind
                + "' and e.ref_id = t.id and e.status in ('PENDING', 'FAILED')) order by t.id";
    }

    private List<String> resourcesAboveCapacity() {
        return jdbc
                .sql("select id, wood, clay, iron, b_warehouse from village order by id")
                .query(
                        (rs, row) -> {
                            double limit =
                                    (double) CAPACITY_FACTOR
                                            * Storage.warehouseCapacity(
                                                    Math.max(1, rs.getInt("b_warehouse")));
                            boolean above =
                                    rs.getDouble("wood") > limit
                                            || rs.getDouble("clay") > limit
                                            || rs.getDouble("iron") > limit;
                            return above ? "village " + rs.getLong("id") : null;
                        })
                .list()
                .stream()
                .filter(id -> id != null)
                .toList();
    }
}
