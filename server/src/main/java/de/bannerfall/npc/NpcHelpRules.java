package de.bannerfall.npc;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of help requests from allied NPC lords (2.1 S4, 12-solo-and-allies §9, 02-game-rules
 * §31), without the database: when a lord may ask, what it asks for, and what an answer is worth.
 * The numbers are in {@link NpcHelpSettings}.
 */
final class NpcHelpRules {

    private NpcHelpRules() {}

    /** A defender added per step while the lord works out how many it needs: half and half. */
    static final int STEP = 50;

    /** Resources asked for are rounded up to this. */
    static final int RESOURCE_STEP = 100;

    /**
     * Whether the lord may ask now: in a solo world, an ally of the human, no open request of its
     * own, and the human got fewer than {@code per-day} requests from all allies in the last day (÷
     * speed).
     */
    static boolean mayAsk(
            boolean solo,
            boolean ally,
            boolean openOwn,
            int requestsToHumanInDay,
            NpcHelpSettings settings) {
        return solo && ally && !openOwn && requestsToHumanInDay < settings.perDay();
    }

    /** The window of the daily cap: a game day ÷ speed. */
    static Duration day(double speed) {
        return NpcTrustRules.day(speed);
    }

    /** How long a shortage must hold a noble or the wall before the lord asks (÷ speed). */
    static Duration shortageWait(double speed, NpcHelpSettings settings) {
        return hours(settings.shortageHours(), speed);
    }

    /** How long a request for resources stays open (÷ speed). */
    static Duration resourceWindow(double speed, NpcHelpSettings settings) {
        return hours(settings.resourceHours(), speed);
    }

    /** A thank-you shipment at most once in this long (÷ speed) per lord and human. */
    static Duration thanksWindow(double speed, NpcHelpSettings settings) {
        return hours(settings.thanksHours(), speed);
    }

    private static Duration hours(double hours, double speed) {
        return NpcTargeting.scaled(Duration.ofSeconds(Math.round(hours * 3600)), speed);
    }

    /** Whether a shortage that began at {@code since} has held long enough at {@code now}. */
    static boolean shortageHeld(
            Instant since, Instant now, double speed, NpcHelpSettings settings) {
        return since != null && !now.isBefore(since.plus(shortageWait(speed, settings)));
    }

    /**
     * Whether a support that leaves {@code now} and travels {@code seconds} lands by {@code
     * arriveAt}. A request for defenders the human's spearmen could not reach in time costs him
     * nothing when he says no or leaves it ({@link #trustFor}).
     */
    static boolean reachable(Instant now, long seconds, Instant arriveAt) {
        return !now.plusSeconds(seconds).isAfter(arriveAt);
    }

    /**
     * What the village lacks for {@code cost}: of each resource {@code cost − stock} (0 if it has
     * enough), rounded up to {@link #RESOURCE_STEP}.
     */
    static ResourceAmounts shortfall(ResourceAmounts cost, ResourceAmounts stock) {
        return new ResourceAmounts(
                lack(cost.wood(), stock.wood()),
                lack(cost.clay(), stock.clay()),
                lack(cost.iron(), stock.iron()));
    }

    private static int lack(int cost, int stock) {
        int missing = cost - stock;
        return missing <= 0 ? 0 : (int) Math.ceil(missing / (double) RESOURCE_STEP) * RESOURCE_STEP;
    }

    /**
     * Whether the lord expects to lose the village (09 §6.1 turned round): the attacker's army as
     * its last scouting saw it at home in the attacking village, against what stands in the village
     * (its own units and every support) behind its wall. The lord is careful by its margin: its
     * defenders count {@code 1 / margin} (an OFFENSIVE lord trusts its walls more than a DEFENSIVE
     * one). A battle the attacker wins at any loss is a loss. Catapults among the attackers are
     * taken to aim at the wall, the worst case for the defender.
     */
    static boolean cannotHold(
            GameData data,
            Map<UnitType, Integer> attackers,
            Map<UnitType, Integer> defenders,
            int wall,
            double margin) {
        return NpcTargeting.estimate(
                        data,
                        attackers,
                        defenders,
                        NpcTargeting.Expected.at(wall).withCatapults(BuildingType.WALL, wall),
                        1 / margin,
                        1.0)
                .go();
    }

    /**
     * The defenders the lord asks for against an army it has seen: {@link #STEP} at a time (half
     * spearmen, half swordsmen) until the estimate says it holds, at least {@code min-defenders},
     * at most {@code max-defenders}.
     */
    static int defendersNeeded(
            GameData data,
            Map<UnitType, Integer> attackers,
            Map<UnitType, Integer> defenders,
            int wall,
            double margin,
            NpcHelpSettings settings) {
        int added = 0;
        while (added < settings.maxDefenders()) {
            added += STEP;
            Map<UnitType, Integer> with = new EnumMap<>(UnitType.class);
            with.putAll(defenders);
            with.merge(UnitType.SPEARMAN, added / 2, Integer::sum);
            with.merge(UnitType.SWORDSMAN, added - added / 2, Integer::sum);
            if (!cannotHold(data, attackers, with, wall, margin)) {
                break;
            }
        }
        return Math.max(settings.minDefenders(), Math.min(added, settings.maxDefenders()));
    }

    /**
     * Without intel on the attacking village the lord only knows who attacks: it fears an attacker
     * with more points than itself and asks for as many defenders again as stand in the village
     * (rounded up to {@link #STEP}), at least {@code min-defenders}, at most {@code max-defenders}.
     */
    static boolean fearsUnseen(long attackerPoints, long lordPoints) {
        return attackerPoints > lordPoints;
    }

    /** {@link #fearsUnseen}: the defenders it asks for. */
    static int unseenDefenders(int standing, NpcHelpSettings settings) {
        int rounded = (int) Math.ceil(standing / (double) STEP) * STEP;
        return Math.max(settings.minDefenders(), Math.min(rounded, settings.maxDefenders()));
    }

    /**
     * Whether the deliveries cover what was asked: every resource asked for has arrived in full.
     */
    static boolean covered(ResourceAmounts asked, ResourceAmounts delivered) {
        return delivered.wood() >= asked.wood()
                && delivered.clay() >= asked.clay()
                && delivered.iron() >= asked.iron();
    }

    /**
     * How an open request ends at its deadline: {@code LATE} when the human answered but not in
     * time (a support on the way or standing there, part of a shipment), else {@code IGNORED}.
     */
    static NpcHelpState atDeadline(boolean tried) {
        return tried ? NpcHelpState.LATE : NpcHelpState.IGNORED;
    }

    /**
     * What the end of a request does to the lord's trust in the human; a request out of his reach
     * (nothing he sent could have landed in time) costs nothing declined or ignored.
     */
    static int trustFor(NpcHelpState state, NpcHelpSettings settings, boolean outOfReach) {
        return switch (state) {
            case ANSWERED -> settings.answered();
            case DECLINED -> outOfReach ? 0 : settings.declined();
            case IGNORED -> outOfReach ? 0 : settings.ignored();
            case OPEN, LATE, CANCELLED -> 0;
        };
    }

    /** What the warning letter does: write it, take it back (ready to write again), or nothing. */
    enum Warning {
        WRITE,
        REARM,
        NONE
    }

    /**
     * An ally whose shown trust is below {@code warn-below} (and at least 0: below 0 it leaves)
     * writes once; it writes again only after its trust rose above {@code warn-again-above}.
     */
    static Warning warning(int shownTrust, boolean warned, NpcHelpSettings settings) {
        if (!warned && shownTrust >= 0 && shownTrust < settings.warnBelow()) {
            return Warning.WRITE;
        }
        if (warned && shownTrust > settings.warnAgainAbove()) {
            return Warning.REARM;
        }
        return Warning.NONE;
    }

    /** Whether a thank-you shipment is due: none sent to this human within the window. */
    static boolean shipsThanks(Instant lastThanks, Instant now, double speed, NpcHelpSettings s) {
        return lastThanks == null || !now.isBefore(lastThanks.plus(thanksWindow(speed, s)));
    }

    /**
     * The defenders the request sheet fills in from a village of the human (12 §9, UI review 2.1):
     * the fastest defender types at home ({@code types}) first, up to {@code wanted} in all, so no
     * slow unit holds the march back while a faster one could go instead. While at least one type
     * lands within {@code secondsLeft}, types that would land later stay at home.
     *
     * @param seconds the travel time of one unit of a type to the lord's village
     */
    static Map<UnitType, Integer> fill(
            Map<UnitType, Integer> home,
            java.util.Collection<UnitType> types,
            int wanted,
            java.util.function.ToLongFunction<UnitType> seconds,
            long secondsLeft) {
        List<UnitType> athome =
                java.util.Arrays.stream(UnitType.values())
                        .filter(type -> types.contains(type) && home.getOrDefault(type, 0) > 0)
                        .sorted(
                                java.util.Comparator.comparingLong(seconds)
                                        .thenComparing(UnitType::ordinal))
                        .toList();
        List<UnitType> inTime =
                athome.stream().filter(type -> seconds.applyAsLong(type) <= secondsLeft).toList();
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        int left = wanted;
        for (UnitType type : inTime.isEmpty() ? athome : inTime) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(left, home.get(type));
            result.put(type, take);
            left -= take;
        }
        return result;
    }
}
