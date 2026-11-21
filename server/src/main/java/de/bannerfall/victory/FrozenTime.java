package de.bannerfall.victory;

import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * When the host takes the end of a world back (v1.3, 02-game-rules §22.4), the world picks up
 * exactly where it stopped: every game clock moves on by the time it stood frozen. Attacks keep the
 * travel time they had left, builds, recruits, dismissals and researches their remaining time, the
 * pending events (NPC rounds, barbarian growth, the victory check) their distance from now;
 * villages get no resources and no loyalty for the frozen time; beginner protection and the
 * cool-downs (banner chances, battle credits) keep what they had left.
 *
 * <p>Moving every row by the same amount keeps each one's distance from "now" what it was from the
 * end, so rows long in the past stay in the past. History (reports, messages, chat, when something
 * was created) keeps its real time.
 *
 * <p>Since 2.1 the same move wakes a solo world that slept (12-solo-and-allies §2) and makes the
 * host's fast forward ({@link #shift} with a negative amount: every game clock comes closer). For
 * that it also moves the game clocks of the settings row (when the world opened, the end date, the
 * victory hold time: {@link WorldService#shiftClocks}), the guild banners' lock after a member left
 * and every time inside the NPC lords' memory (grudges, the attacks of the 24-hour caps, intel,
 * farm ages, conquest targets, wall goals). {@code FrozenTimeColumnsTest} fails on a timestamp
 * column that is neither moved here nor listed as real time.
 */
@Component
public class FrozenTime {

    /** Each statement moves the game clocks of one table; {@code :secs} is the frozen time. */
    private static final List<String> SHIFTS =
            List.of(
                    "update game_event set due_at = due_at + make_interval(secs => :secs)"
                            + " where status = 'PENDING'",
                    "update build_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update recruit_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update dismiss_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update research_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update movement set depart_at = depart_at + make_interval(secs => :secs),"
                            + " arrive_at = arrive_at + make_interval(secs => :secs)",
                    "update trade_transport"
                            + " set depart_at = depart_at + make_interval(secs => :secs),"
                            + " arrive_at = arrive_at + make_interval(secs => :secs),"
                            + " return_at = return_at + make_interval(secs => :secs)",
                    // never past now: a village caught up after the end must not stand still
                    "update village set resources_updated_at ="
                            + " least(resources_updated_at + make_interval(secs => :secs), :now),"
                            + " loyalty_updated_at ="
                            + " least(loyalty_updated_at + make_interval(secs => :secs), :now)",
                    "update player set protected_until ="
                            + " protected_until + make_interval(secs => :secs)",
                    "update banner_roll set rolled_at = rolled_at + make_interval(secs => :secs)",
                    "update battle_credit set counted_at = counted_at + make_interval(secs =>"
                            + " :secs)",
                    "update loyalty_sighting set seen_at = seen_at + make_interval(secs => :secs)",
                    "update guild_banner set locked_until ="
                            + " locked_until + make_interval(secs => :secs)",
                    // the Avenger window (24 h ÷ speed) after a defeat
                    "update achievement_grudge set beaten_at ="
                            + " beaten_at + make_interval(secs => :secs)",
                    // trust between lords and humans (2.1 S2b, 12 §5): drift anchor, the wait
                    // after a refusal, the day of the caps, the grudge a credit was for
                    "update npc_trust set trust_at = trust_at + make_interval(secs => :secs),"
                            + " invite_after = invite_after + make_interval(secs => :secs),"
                            + " day_start = day_start + make_interval(secs => :secs)",
                    "update npc_trust_credit set grudge_until ="
                            + " grudge_until + make_interval(secs => :secs)",
                    // orders to allies (2.1 S3, 12 §8): a planned march's departure and every
                    // arrival, and when the order was given and answered (the page's order)
                    "update npc_order set given_at = given_at + make_interval(secs => :secs),"
                            + " answered_at = answered_at + make_interval(secs => :secs),"
                            + " depart_at = depart_at + make_interval(secs => :secs),"
                            + " arrive_at = arrive_at + make_interval(secs => :secs)",
                    // help requests (2.1 S4, 12 §9): the deadline, the daily cap's window and
                    // the once-a-day thank-you shipment
                    "update npc_help_request set created_at = created_at + make_interval(secs =>"
                            + " :secs), deadline = deadline + make_interval(secs => :secs),"
                            + " thanked_at = thanked_at + make_interval(secs => :secs)");

    /** An instant as Jackson writes it into the lords' memory: ISO-8601 in UTC. */
    private static final Pattern INSTANT =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final WorldService worldService;

    FrozenTime(JdbcClient jdbc, WorldService worldService) {
        this.jdbc = jdbc;
        this.worldService = worldService;
    }

    /** Moves the game clocks on by the time from {@code endedAt} to {@code now}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resume(Instant endedAt, Instant now) {
        Duration frozen = Duration.between(endedAt, now);
        if (frozen.isNegative() || frozen.isZero()) {
            return;
        }
        shift(frozen, now);
    }

    /**
     * Moves every game clock by {@code by}: on (positive) after the world stood still, or closer
     * (negative) for the host's fast forward. The caller holds the settings row's lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shift(Duration by, Instant now) {
        if (by.isZero()) {
            return;
        }
        double secs = by.toNanos() / 1e9;
        for (String sql : SHIFTS) {
            jdbc.sql(sql).param("secs", secs).param("now", Timestamp.from(now)).update();
        }
        worldService.shiftClocks(by);
        shiftLordMemories(by);
    }

    /**
     * Every instant inside {@code npc_brain.memory}, found by its form, so a time a later version
     * adds to {@code NpcMemory} moves too. The version goes up, so a lord's round that loaded the
     * memory before fails instead of writing the old times back.
     */
    private void shiftLordMemories(Duration by) {
        record Memory(long playerId, String json) {}
        List<Memory> memories =
                // locked: a lord's round that commits between this read and the write below
                // would otherwise lose its new memory to the shifted old one (fast forward runs
                // every 10 seconds while the lords think)
                jdbc.sql(
                                "select player_id, memory::text from npc_brain order by player_id"
                                        + " for update")
                        .query((rs, row) -> new Memory(rs.getLong(1), rs.getString(2)))
                        .list();
        for (Memory memory : memories) {
            String shifted = shiftInstants(memory.json(), by);
            if (!shifted.equals(memory.json())) {
                jdbc.sql(
                                "update npc_brain set memory = cast(:memory as jsonb),"
                                        + " version = version + 1 where player_id = :id")
                        .param("memory", shifted)
                        .param("id", memory.playerId())
                        .update();
            }
        }
    }

    /** {@code json} with every ISO instant (a string value or a map key) moved by {@code by}. */
    static String shiftInstants(String json, Duration by) {
        JsonNode tree = MAPPER.readTree(json);
        JsonNode shifted = shiftNode(tree, by);
        return MAPPER.writeValueAsString(shifted);
    }

    private static JsonNode shiftNode(JsonNode node, Duration by) {
        if (node.isString()) {
            String moved = shiftText(node.asString(), by);
            return moved == null ? node : StringNode.valueOf(moved);
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(element -> copy.add(shiftNode(element, by)));
            return copy;
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = shiftText(field.getKey(), by);
                copy.set(key == null ? field.getKey() : key, shiftNode(field.getValue(), by));
            }
            return copy;
        }
        return node;
    }

    /** The text moved by {@code by} if it is an instant, else null. */
    private static String shiftText(String text, Duration by) {
        if (!INSTANT.matcher(text).matches()) {
            return null;
        }
        try {
            return Instant.parse(text).plus(by).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
