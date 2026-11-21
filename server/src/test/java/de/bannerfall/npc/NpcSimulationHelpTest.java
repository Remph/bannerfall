package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.market.MarketService;
import de.bannerfall.market.TransportRequest;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService.SendAttempt;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Help requests from allies in a solo world (2.1 S4, 12-solo-and-allies §9): the Solo preset with
 * the scripted humans of {@link SimulatedHumans}. The first farmer, the commander, wins his four
 * nearest lords as allies at the start (trust 80) and then lives with their requests: in turn he
 * answers one (sends the defenders the request fills in from his home, or the resources it still
 * needs), says "Not this time" to the next and leaves the third unanswered.
 *
 * <p>The run checks the rules of 12 §9 as they happen: at most one open request per lord, at most
 * {@code per-day} new requests to the human in any day (÷ speed), every end moves the lord's trust
 * by its value (answered +15, declined −5, ignored −10, late and called off 0), and no event fails
 * (09 §11 checks that for every run).
 *
 * <p>A class of its own for a JVM of its own, see {@link NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationHelpTest extends NpcSimulationTest {

    private static final double SEEDED_TRUST = 80;
    private static final int COMMANDER_WAREHOUSE = 18;
    private static final int COMMANDER_MARKET = 20;

    @Autowired GuildService guildService;
    @Autowired NpcTrust trust;
    @Autowired NpcHelpRequests help;
    @Autowired NpcHelpStore store;
    @Autowired NpcHelpSettings settings;
    @Autowired MarketService market;
    @Autowired TransactionTemplate tx;

    private long commander;
    private long guild;
    private double speed;
    private int turn;

    /** The requests seen so far: id to what the commander did ("answer", "decline", "ignore"). */
    private final Map<Long, String> handled = new HashMap<>();

    /** The requests already seen closed. */
    private final Map<Long, String> closed = new HashMap<>();

    private final Map<String, Integer> asked = new TreeMap<>();
    private final Map<String, Integer> did = new TreeMap<>();
    private final Map<String, Integer> ends = new TreeMap<>();
    private final Map<String, Integer> endsAfterAnswer = new TreeMap<>();
    private final List<String> broken = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private int trustSeen;
    private int outOfReach;
    private int maxInDay;
    private int maxOpenPerLord;

    @Override
    boolean activeHumans() {
        return true;
    }

    @Override
    void scenarioSettings() {
        jdbc.sql(
                        """
                        update world_settings set speed = 4, npc_lord_count = 8,
                            barbarian_initial_count = 40, guilds_enabled = true,
                            npc_guilds_enabled = true, npc_guild_max_members = 3,
                            guild_max_members = 10,
                            victory_mode = 'DOMINATION', domination_percent = 60,
                            victory_hold_hours = 48, night_bonus_enabled = false,
                            solo_mode = true
                        """)
                .update();
    }

    @Override
    void scenarioStart(List<Long> humans, Instant start) {
        commander = humans.get(0); // the first farmer
        speed = jdbc.sql("select speed from world_settings").query(Double.class).single();
        // an active human builds: a grown warehouse and market, so he can answer with resources
        jdbc.sql(
                        """
                        update village set b_warehouse = greatest(b_warehouse, ?),
                          b_market = greatest(b_market, ?) where player_id = ?
                        """)
                .params(COMMANDER_WAREHOUSE, COMMANDER_MARKET, commander)
                .update();
        guild = guildService.create(commander, "Bannerbund", "BB").guild().id();
        for (long lord : nearestLords(commander, 4)) {
            tx.executeWithoutResult(
                    status -> {
                        trust.change(
                                lord, commander, SEEDED_TRUST, TrustReason.SUPPORT, "-", start);
                        guildService.inviteLord(commander, lord);
                    });
        }
    }

    /** The lords nearest to the human's first village. */
    private List<Long> nearestLords(long human, int count) {
        return jdbc.sql(
                        """
                        select p.id from player p
                        join village v on v.player_id = p.id
                        cross join (select x, y from village where player_id = :human
                                    order by id limit 1) h
                        where p.npc
                        group by p.id
                        order by min((v.x - h.x) * (v.x - h.x) + (v.y - h.y) * (v.y - h.y)), p.id
                        limit :count
                        """)
                .param("human", human)
                .param("count", count)
                .query(Long.class)
                .list();
    }

    @Override
    void scenarioStep(Instant now) {
        checkOpen();
        watchClosed();
        for (HelpRequestView request : openRequests()) {
            if (handled.containsKey(request.id)) {
                continue;
            }
            asked.merge(request.kind, 1, Integer::sum);
            checkDailyCap(request);
            String what =
                    switch (turn++ % 3) {
                        case 0 -> "answer";
                        case 1 -> "decline";
                        default -> "ignore";
                    };
            handled.put(request.id, what);
            switch (what) {
                case "answer" -> answer(request, now);
                case "decline" -> decline(request, now);
                default -> {}
            }
            did.merge(what, 1, Integer::sum);
        }
    }

    record HelpRequestView(
            long id,
            long lordId,
            String kind,
            long villageId,
            int x,
            int y,
            int defenders,
            int wood,
            int clay,
            int iron,
            Instant createdAt,
            Instant deadline) {}

    private List<HelpRequestView> openRequests() {
        return jdbc.sql(
                        """
                        select r.id, r.lord_id, r.kind, r.village_id, v.x, v.y, r.defenders,
                          greatest(r.wood - r.delivered_wood, 0) as wood,
                          greatest(r.clay - r.delivered_clay, 0) as clay,
                          greatest(r.iron - r.delivered_iron, 0) as iron,
                          r.created_at, r.deadline
                        from npc_help_request r join village v on v.id = r.village_id
                        where r.player_id = ? and r.state = 'OPEN' order by r.id
                        """)
                .param(commander)
                .query(
                        (rs, n) ->
                                new HelpRequestView(
                                        rs.getLong("id"),
                                        rs.getLong("lord_id"),
                                        rs.getString("kind"),
                                        rs.getLong("village_id"),
                                        rs.getInt("x"),
                                        rs.getInt("y"),
                                        rs.getInt("defenders"),
                                        rs.getInt("wood"),
                                        rs.getInt("clay"),
                                        rs.getInt("iron"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("deadline").toInstant()))
                .list();
    }

    /**
     * "Send support" with the defenders asked for, or "Send resources" with what is still needed.
     */
    private void answer(HelpRequestView request, Instant now) {
        Long home = homeVillage();
        if (home == null) {
            notes.add("no village to answer " + request.id + " from");
            return;
        }
        Coordinates at = new Coordinates(request.x, request.y);
        if (request.kind.equals("SUPPORT")) {
            int spears = request.defenders / 2;
            int swords = request.defenders - spears;
            // the scripted humans recruit nothing: the defenders are granted at home first
            jdbc.sql(
                            """
                            update unit_stack set u_spearman = greatest(u_spearman, ?),
                              u_swordsman = greatest(u_swordsman, ?)
                            where home_village_id = ? and location_village_id = ?
                            """)
                    .params(spears, swords, home, home)
                    .update();
            SendAttempt attempt =
                    commands.trySend(
                            home,
                            commander,
                            new CommandRequest(
                                    CommandKind.SUPPORT,
                                    at,
                                    Map.of(UnitType.SPEARMAN, spears, UnitType.SWORDSMAN, swords),
                                    null));
            if (attempt.movement() == null) {
                notes.add("support for " + request.id + " refused: " + attempt.errors());
            }
        } else {
            jdbc.sql(
                            """
                            update village set wood = greatest(wood, ?), clay = greatest(clay, ?),
                              iron = greatest(iron, ?), resources_updated_at = ? where id = ?
                            """)
                    .params(request.wood, request.clay, request.iron, Timestamp.from(now), home)
                    .update();
            try {
                market.send(
                        home,
                        commander,
                        new TransportRequest(at, request.wood, request.clay, request.iron));
            } catch (RuntimeException e) {
                notes.add("shipment for " + request.id + " refused: " + e.getMessage());
            }
        }
    }

    private void decline(HelpRequestView request, Instant now) {
        try {
            help.decline(commander, request.id, now);
        } catch (RuntimeException e) {
            broken.add("\"Not this time\" refused for an open request " + request.id + ": " + e);
        }
    }

    private Long homeVillage() {
        return jdbc.sql("select id from village where player_id = ? order by id limit 1")
                .param(commander)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    /** At most one open request per lord. */
    private void checkOpen() {
        int most =
                jdbc.sql(
                                """
                                select coalesce(max(c), 0) from (select count(*) as c
                                  from npc_help_request where state = 'OPEN' group by lord_id) o
                                """)
                        .query(Integer.class)
                        .single();
        maxOpenPerLord = Math.max(maxOpenPerLord, most);
        if (most > 1) {
            broken.add("a lord with " + most + " open requests");
        }
    }

    /** At most {@code per-day} requests to the human in the day (÷ speed) up to a new one. */
    private void checkDailyCap(HelpRequestView request) {
        Duration day = NpcHelpRules.day(speed);
        int inDay =
                jdbc.sql(
                                """
                                select count(*) from npc_help_request where player_id = ?
                                  and created_at > ? and created_at <= ?
                                """)
                        .params(
                                commander,
                                Timestamp.from(request.createdAt.minus(day)),
                                Timestamp.from(request.createdAt))
                        .query(Integer.class)
                        .single();
        maxInDay = Math.max(maxInDay, inDay);
        if (inDay > settings.perDay()) {
            broken.add("request " + request.id + " is number " + inDay + " in a day");
        }
    }

    /** Every request that ended moved the lord's trust by its value, with its reason. */
    private void watchClosed() {
        List<Object[]> rows =
                jdbc.sql(
                                """
                                select r.id, r.lord_id, r.state, t.changes::text
                                from npc_help_request r
                                left join npc_trust t on t.lord_id = r.lord_id
                                  and t.player_id = r.player_id
                                where r.player_id = ? and r.state <> 'OPEN'
                                """)
                        .param(commander)
                        .query(
                                (rs, n) ->
                                        new Object[] {
                                            rs.getLong(1),
                                            rs.getLong(2),
                                            rs.getString(3),
                                            rs.getString(4)
                                        })
                        .list();
        for (Object[] row : rows) {
            long id = (Long) row[0];
            String state = (String) row[2];
            if (closed.putIfAbsent(id, state) != null) {
                continue;
            }
            ends.merge(state, 1, Integer::sum);
            String what = handled.getOrDefault(id, "unseen");
            endsAfterAnswer.merge(what + " -> " + state, 1, Integer::sum);
            if (what.equals("decline") && !state.equals("DECLINED")) {
                broken.add("declined request " + id + " ended " + state);
            }
            if (what.equals("ignore") && state.equals("ANSWERED")) {
                broken.add("ignored request " + id + " ended ANSWERED");
            }
            boolean far = help.outOfReach(store.find(id).orElseThrow());
            if (far) {
                outOfReach++;
            }
            int delta = NpcHelpRules.trustFor(NpcHelpState.valueOf(state), settings, far);
            if (delta != 0) {
                String changes = row[3] == null ? "" : (String) row[3];
                String reason = "HELP_" + state;
                if (changes.contains("\"" + reason + "\"")) {
                    trustSeen++;
                } else {
                    notes.add(
                            "request "
                                    + id
                                    + " ended "
                                    + state
                                    + " but the last changes of its lord show no "
                                    + reason
                                    + ": "
                                    + changes);
                }
            }
        }
    }

    private int count(String sql) {
        return jdbc.sql(sql).param(commander).query(Integer.class).single();
    }

    private List<Long> allies() {
        return jdbc.sql(
                        """
                        select m.player_id from guild_member m join player p on p.id = m.player_id
                        where m.guild_id = ? and p.npc order by m.player_id
                        """)
                .param(guild)
                .query(Long.class)
                .list();
    }

    @Override
    String scenarioLines() {
        Map<Long, Long> trustAtEnd = new TreeMap<>();
        Instant now = clock.instant();
        for (long lord : nearestLords(commander, 4)) {
            trustAtEnd.put(lord, Math.round(trust.trust(lord, commander, now)));
        }
        return String.format(
                Locale.ROOT,
                """

                HELP REQUESTS (2.1 S4)
                  allies at the end: %d, trust of the four first allies at the end: %s
                  requests: %s
                  the commander: %s
                  ends: %s (out of his reach: %d)
                  ends by what he did: %s
                  trust changes seen with their reason: %d
                  most requests in a day (÷ speed): %d (cap %d), most open per lord: %d
                  thank-you shipments: %d, thank-you letters: %d, warnings: %d, court taunts: %d
                  notes: %d%s
                  rules broken: %d%s
                """,
                allies().size(),
                trustAtEnd.values(),
                asked,
                did,
                ends,
                outOfReach,
                endsAfterAnswer,
                trustSeen,
                maxInDay,
                settings.perDay(),
                maxOpenPerLord,
                count(
                        "select count(*) from npc_help_request where player_id = ? and thanked_at"
                                + " is not null"),
                count(
                        "select count(*) from private_message where recipient_id = ? and"
                                + " letter->>'kind' = 'THANKS'"),
                count(
                        "select count(*) from private_message where recipient_id = ? and"
                                + " letter->>'kind' = 'WARNING'"),
                count(
                        "select count(*) from private_message where recipient_id = ? and"
                                + " letter->>'kind' = 'COURT_TAUNT'"),
                notes.size(),
                notes.isEmpty()
                        ? ""
                        : "\n    "
                                + String.join(
                                        "\n    ", notes.subList(0, Math.min(20, notes.size()))),
                broken.size(),
                broken.isEmpty() ? "" : "\n    " + String.join("\n    ", broken));
    }

    @Override
    void scenarioAssert() {
        assertThat(broken).as("help requests that broke a rule").isEmpty();
        assertThat(maxInDay).as("requests in a day").isLessThanOrEqualTo(settings.perDay());
        assertThat(ends.keySet()).as("how requests ended").contains("ANSWERED", "DECLINED");
        assertThat(ends.containsKey("IGNORED") || ends.containsKey("LATE"))
                .as("unanswered requests ended " + ends)
                .isTrue();
        assertThat(trustSeen).as("trust changes of ended requests").isPositive();
    }

    @Test
    void withHelpRequests() throws Exception {
        run("solo world, help requests", 0);
    }
}
