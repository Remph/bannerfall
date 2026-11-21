package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.common.Coordinates;
import de.bannerfall.diplomacy.OrderRequest;
import de.bannerfall.diplomacy.OrderService;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.message.LordLetter;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService.SendAttempt;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders to allies in a solo world (2.1 S3, 12-solo-and-allies §8): the Solo preset with the
 * scripted humans of {@link SimulatedHumans}. The first farmer, the commander, wins four lords as
 * allies at the start and keeps three of them with his support (+10 every game day); the fourth's
 * trust is set to 10 after it joined. Every six game hours he gives each ally an order, in turn an
 * attack on a barbarian village next to the ally ("land together" with a slow attack of his own on
 * the way), an attack on the nearest lord outside his guild, a support of his village, resources
 * for it and a hold on the nearest lord.
 *
 * <p>The lords must never act on an order that breaks a rule: the low-trust ally refuses all of
 * them, no ordered army is bigger than its personality's share of the village's units, no ally
 * attacks its own guild, a hold keeps its lord away from the village, a planned march leaves as
 * planned, and no event fails (09 §11 checks that for every run).
 *
 * <p>A class of its own for a JVM of its own, see {@link NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationOrdersTest extends NpcSimulationTest {

    private static final double SEEDED_TRUST = 80;
    private static final double LOW_TRUST = 10;
    private static final double SUPPORT = 10;
    private static final Duration ORDER_EVERY = Duration.ofHours(6);
    private static final int SPENT_TO = 2000;
    private static final int COMMANDER_WAREHOUSE = 18;
    private static final int NO_SURPLUS_SAMPLES = 12;

    @Autowired GuildService guildService;
    @Autowired NpcTrust trust;
    @Autowired OrderService orderService;
    @Autowired NpcOrderStore store;
    @Autowired NpcOrderSettings settings;
    @Autowired NpcBrainRepository brains;
    @Autowired TransactionTemplate tx;

    private long commander;
    private long guild;
    private long distrusted;
    private Instant nextOrder;
    private Instant nextSupport;
    private int turn;

    /** The orders by lord as the last step saw them (state, answer, given at). */
    private final Map<Long, String> seen = new HashMap<>();

    private final Map<String, Integer> given = new TreeMap<>();
    private final Map<String, Integer> answers = new TreeMap<>();
    private final List<String> broken = new ArrayList<>();

    /**
     * The last samples of "nothing to send": per village market level/offer merchants/road/stock.
     */
    private final List<String> noSurplus = new ArrayList<>();

    private int marches;
    private int holds;

    /** Active holds: lord to village and since when. */
    private final Map<Long, long[]> holding = new HashMap<>();

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
        // the scripted humans build nothing; a commander who orders shipments has a grown warehouse
        jdbc.sql("update village set b_warehouse = greatest(b_warehouse, ?) where player_id = ?")
                .params(COMMANDER_WAREHOUSE, commander)
                .update();
        guild = guildService.create(commander, "Bannerbund", "BB").guild().id();
        for (long lord : nearestLords(commander, 4)) {
            tx.executeWithoutResult(
                    status -> {
                        trust.change(
                                lord, commander, SEEDED_TRUST, TrustReason.SUPPORT, "-", start);
                        guildService.inviteLord(commander, lord);
                    });
            distrusted = lord; // the farthest of the four
        }
        nextOrder = start.plus(Duration.ofHours(1)); // after the lords answered the invitations
        nextSupport = start;
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
        List<Long> allies = allies();
        watchOrders(now);
        watchHolds();
        if (!now.isBefore(nextSupport)) {
            nextSupport = now.plus(Duration.ofHours(6));
            for (long lord : allies) {
                if (lord == distrusted) {
                    // its trust stays at 10: no to every order, but it doesn't leave
                    tx.executeWithoutResult(
                            status -> {
                                double value = trust.trust(lord, commander, now);
                                trust.change(
                                        lord,
                                        commander,
                                        LOW_TRUST - value,
                                        TrustReason.SUPPORT,
                                        "-",
                                        now);
                            });
                } else {
                    tx.executeWithoutResult(
                            status ->
                                    trust.change(
                                            lord,
                                            commander,
                                            SUPPORT,
                                            TrustReason.SUPPORT,
                                            "-",
                                            now));
                }
            }
        }
        if (now.isBefore(nextOrder) || allies.isEmpty()) {
            return;
        }
        nextOrder = now.plus(ORDER_EVERY);
        Long home = homeVillage();
        if (home == null) {
            return;
        }
        int i = 0;
        for (long lord : allies) {
            int pick = (turn + i++) % 5;
            OrderRequest request =
                    switch (pick) {
                        case 0 -> attackFarm(lord, home);
                        case 1 -> attackLord(lord, NpcOrderKind.ATTACK);
                        case 2 ->
                                new OrderRequest(NpcOrderKind.SUPPORT, home, List.of(lord), false);
                        case 3 -> sendTo(home, now, lord);
                        default -> attackLord(lord, NpcOrderKind.HOLD);
                    };
            if (request == null) {
                continue;
            }
            try {
                orderService.give(commander, request);
                given.merge(
                        request.kind() + (request.landTogether() ? " (land together)" : ""),
                        1,
                        Integer::sum);
            } catch (RuntimeException e) {
                broken.add("order refused by the API: " + request + ": " + e.getMessage());
            }
        }
        turn++;
    }

    /**
     * "Land together": the commander sends a slow attack of his own (rams with an escort) on the
     * barbarian village nearest to the ally, then orders the ally to land with it, so the lord's
     * shorter march is planned to leave later. Without such a village, a plain attack on the
     * barbarian village nearest to him.
     */
    private OrderRequest attackFarm(long lord, long home) {
        Long target =
                jdbc.sql(
                                """
                                select v.id from village v
                                cross join (select x, y from village where player_id = :lord
                                            order by id limit 1) l
                                where v.player_id is null
                                order by (v.x - l.x) * (v.x - l.x) + (v.y - l.y) * (v.y - l.y), v.id
                                limit 1
                                """)
                        .param("lord", lord)
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        if (target != null) {
            Coordinates at =
                    jdbc.sql("select x, y from village where id = ?")
                            .param(target)
                            .query((rs, n) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                            .single();
            Map<UnitType, Integer> army = Map.of(UnitType.RAM, 2, UnitType.AXEMAN, 60);
            army.forEach(
                    (type, count) ->
                            jdbc.sql(
                                            "update unit_stack set u_%s = greatest(u_%s, ?) where home_village_id = ? and location_village_id = ?"
                                                    .formatted(
                                                            type.name().toLowerCase(Locale.ROOT),
                                                            type.name().toLowerCase(Locale.ROOT)))
                                    .params(count, home, home)
                                    .update());
            SendAttempt attempt =
                    commands.trySend(
                            home,
                            commander,
                            new CommandRequest(CommandKind.ATTACK, at, army, null));
            if (attempt.movement() != null) {
                return new OrderRequest(NpcOrderKind.ATTACK, target, List.of(lord), true);
            }
        }
        Long nearest = nearest("v.player_id is null");
        return nearest == null
                ? null
                : new OrderRequest(NpcOrderKind.ATTACK, nearest, List.of(lord), false);
    }

    /** An order on the village of the nearest lord outside the commander's guild. */
    /**
     * A Send: the commander has just spent most of his stock (an active human builds), so his
     * warehouse has room; the run then shows whether the lords have a surplus to ship.
     */
    private OrderRequest sendTo(long home, Instant now, long lord) {
        jdbc.sql(
                        """
                        update village set wood = least(wood, ?), clay = least(clay, ?),
                          iron = least(iron, ?), resources_updated_at = ? where id = ?
                        """)
                .params(SPENT_TO, SPENT_TO, SPENT_TO, Timestamp.from(now), home)
                .update();
        return new OrderRequest(NpcOrderKind.SEND, home, List.of(lord), false);
    }

    /**
     * Why a lord had nothing to send: per village, its market level, the merchants its own trade
     * offers hold, the merchants on the road and its stock.
     */
    private void noteNoSurplus(long lord, Instant now) {
        List<String> villages =
                jdbc.sql(
                                """
                                select v.b_market,
                                  coalesce((select sum(ceil(o.offer_amount / 1000.0)
                                    * o.remaining_count) from trade_offer o
                                    where o.village_id = v.id), 0) as offers,
                                  coalesce((select sum(t.merchants) from trade_transport t
                                    where t.source_village_id = v.id and t.return_at > ?), 0)
                                    as road,
                                  floor(v.wood + v.clay + v.iron) as stock
                                from village v where v.player_id = ? order by v.id
                                """)
                        .params(Timestamp.from(now), lord)
                        .query(
                                (rs, row) ->
                                        "m"
                                                + rs.getInt(1)
                                                + "/o"
                                                + rs.getInt(2)
                                                + "/r"
                                                + rs.getInt(3)
                                                + "/"
                                                + rs.getLong(4))
                        .list();
        noSurplus.add(now + " " + lord + ": " + String.join(" ", villages));
        if (noSurplus.size() > NO_SURPLUS_SAMPLES) {
            noSurplus.remove(0); // the last ones: the lords have grown by then
        }
    }

    private OrderRequest attackLord(long lord, NpcOrderKind kind) {
        Long target =
                nearest(
                        """
                        v.player_id in (select id from player where npc) and v.player_id not in
                            (select player_id from guild_member where guild_id = %d)
                        """
                                .formatted(guild));
        return target == null ? null : new OrderRequest(kind, target, List.of(lord), false);
    }

    private Long nearest(String where) {
        return jdbc.sql(
                        """
                        select v.id from village v
                        cross join (select x, y from village where player_id = :human
                                    order by id limit 1) h
                        where %s
                        order by (v.x - h.x) * (v.x - h.x) + (v.y - h.y) * (v.y - h.y), v.id
                        limit 1
                        """
                                .formatted(where))
                .param("human", commander)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private Long homeVillage() {
        return jdbc.sql("select id from village where player_id = ? order by id limit 1")
                .param(commander)
                .query(Long.class)
                .optional()
                .orElse(null);
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

    /** Each order that changed since the last step is checked against the rules once. */
    private void watchOrders(Instant now) {
        for (NpcOrderStore.Order order : store.ofPlayer(commander)) {
            String key = order.state() + " " + order.answer() + " " + order.givenAt();
            if (key.equals(seen.put(order.lordId(), key))) {
                continue;
            }
            if (order.answer() != null
                    && (order.state() == NpcOrderState.ACTIVE
                            || order.state() == NpcOrderState.REFUSED
                            || order.state() == NpcOrderState.PLANNED)) {
                answers.merge(order.answer().name(), 1, Integer::sum);
                if (order.answer() == LordLetter.Kind.ORDER_NO_SURPLUS) {
                    noteNoSurplus(order.lordId(), now);
                }
            }
            check(order, now);
        }
    }

    private void check(NpcOrderStore.Order order, Instant now) {
        long lord = order.lordId();
        boolean carriedOut =
                order.state() == NpcOrderState.ACTIVE || order.state() == NpcOrderState.PLANNED;
        if (lord == distrusted
                && carriedOut
                && order.answer() != LordLetter.Kind.ORDER_REFUSED_TRUST) {
            broken.add("the distrusted ally carried out " + order);
        }
        if (!carriedOut) {
            return;
        }
        if (order.kind() == NpcOrderKind.HOLD) {
            holding.put(
                    lord, new long[] {order.targetVillageId(), order.answeredAt().toEpochMilli()});
            holds++;
            return;
        }
        holding.remove(lord);
        if (order.kind() == NpcOrderKind.ATTACK) {
            Long owner =
                    jdbc.sql("select player_id from village where id = ?")
                            .param(order.targetVillageId())
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (owner != null
                    && (owner == lord || allies().contains(owner) || owner == commander)) {
                broken.add("an ally attacked its own guild: " + order);
            }
        }
        if (order.state() == NpcOrderState.ACTIVE
                && order.answer() == LordLetter.Kind.ORDER_ATTACK_PLANNED) {
            marches++;
            // it left when it was due and lands as planned (a second or two of the worker)
            Instant landing =
                    jdbc.sql(
                                    """
                                    select max(arrive_at) from movement where owner_player_id = ?
                                      and target_village_id = ? and kind = 'ATTACK'
                                    """)
                            .params(lord, order.targetVillageId())
                            .query(Timestamp.class)
                            .optional()
                            .map(Timestamp::toInstant)
                            .orElse(null);
            if (landing == null
                    || Math.abs(Duration.between(order.arriveAt(), landing).toSeconds()) > 30) {
                broken.add("a planned march landed off its time: " + order + " at " + landing);
            }
        }
        if (order.army() != null && order.sourceVillageId() != null) {
            checkShare(order);
        }
    }

    /** No ordered army is bigger than its share of the village's units (home, away, on the way). */
    private void checkShare(NpcOrderStore.Order order) {
        NpcBrain brain = brains.findById(order.lordId()).orElse(null);
        if (brain == null) {
            return;
        }
        double share =
                order.kind() == NpcOrderKind.SUPPORT
                        ? 1.0 // checked loosely: up to all its support units
                        : NpcOrderRules.attackShare(brain.getPersonality(), settings);
        Map<de.bannerfall.gamedata.UnitType, Integer> total =
                new EnumMap<>(de.bannerfall.gamedata.UnitType.class);
        for (de.bannerfall.gamedata.UnitType type : de.bannerfall.gamedata.UnitType.values()) {
            String column = "u_" + type.name().toLowerCase(Locale.ROOT);
            int units =
                    jdbc.sql(
                                    ("select coalesce((select sum(%1$s) from unit_stack where"
                                         + " home_village_id = :v), 0) + coalesce((select sum(%1$s)"
                                         + " from movement where home_village_id = :v), 0)")
                                            .formatted(column))
                            .param("v", order.sourceVillageId())
                            .query(Integer.class)
                            .single();
            total.put(type, units);
        }
        order.army()
                .forEach(
                        (type, count) -> {
                            if (count > Math.ceil(share * total.getOrDefault(type, 0)) + 1) {
                                broken.add(
                                        "an ordered army above its share: "
                                                + order
                                                + " of "
                                                + total);
                            }
                        });
    }

    /** While a hold stands, its lord sends nothing to that village. */
    private void watchHolds() {
        Set<Long> gone = new HashSet<>();
        for (Map.Entry<Long, long[]> hold : holding.entrySet()) {
            NpcOrderStore.Order order = store.find(hold.getKey()).orElse(null);
            if (order == null
                    || order.kind() != NpcOrderKind.HOLD
                    || order.state() != NpcOrderState.ACTIVE) {
                gone.add(hold.getKey());
                continue;
            }
            int sent =
                    jdbc.sql(
                                    """
                                    select count(*) from movement where owner_player_id = ?
                                      and target_village_id = ? and kind = 'ATTACK'
                                      and depart_at > ?
                                    """)
                            .params(
                                    hold.getKey(),
                                    hold.getValue()[0],
                                    Timestamp.from(Instant.ofEpochMilli(hold.getValue()[1])))
                            .query(Integer.class)
                            .single();
            if (sent > 0) {
                broken.add("a lord attacked a village it holds off: " + order);
                gone.add(hold.getKey());
            }
        }
        gone.forEach(holding::remove);
    }

    @Override
    String scenarioLines() {
        return String.format(
                Locale.ROOT,
                """

                ORDERS (2.1 S3)
                  allies at the end: %d (the distrusted one included: %s)
                  orders given: %s
                  answers: %s
                  planned marches that left: %d, holds: %d
                  nothing to send (lord: per village m<market>/o<offer merchants>/r<on the road>/<stock>):%s
                  rules broken: %d%s
                """,
                allies().size(),
                allies().contains(distrusted) ? "yes" : "no",
                given,
                answers,
                marches,
                holds,
                noSurplus.isEmpty() ? " none" : "\n    " + String.join("\n    ", noSurplus),
                broken.size(),
                broken.isEmpty() ? "" : "\n    " + String.join("\n    ", broken));
    }

    @Override
    void scenarioAssert() {
        assertThat(broken).as("orders that broke a rule").isEmpty();
        assertThat(answers).as("answers").containsKey("ORDER_REFUSED_TRUST");
        assertThat(answers.keySet())
                .as("orders carried out")
                .containsAnyOf("ORDER_ATTACK", "ORDER_ATTACK_PLANNED")
                .contains("ORDER_SUPPORT", "ORDER_HOLD");
    }

    @Test
    void withOrdersToAllies() throws Exception {
        run("solo world, orders", 0);
    }
}
