package de.bannerfall.npc;

import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Population;
import de.bannerfall.rules.Storage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure decisions of an NPC lord's economy (09-npc-lords §5, §3): which building comes next,
 * which unit to recruit, quiet hours and round timing. No Spring, no database: plain values in, a
 * decision out, so they are unit-tested like the rules ({@code NpcPlanningTest}).
 */
public final class NpcPlanning {

    /** The population kept free for troops' sake: the farm rule wants this share on top (§5.1). */
    static final double BUILD_POP_RESERVE = 0.10;

    /** Troops stop filling the farm at this share of its capacity (§5.3). */
    static final double TROOP_POP_RESERVE = 0.05;

    /** One chunk of a recruit order is this share of the farm capacity (§5.3). */
    static final double CHUNK_SHARE = 0.01;

    private NpcPlanning() {}

    // ---- building (§5.1) ----

    /** What the village should do about its next building. */
    public enum Kind {
        /** Build {@link Next#type()} (if the queue, stock and population allow). */
        BUILD,
        /** Wait: an upgrade the next one depends on (warehouse, farm) is already queued. */
        WAIT,
        /** The template is done, the village is fully built. */
        DONE,
        /** Recruit one noble (§5.1, 5): the village saves for nobles instead of building. */
        NOBLE
    }

    /** The result of {@link #nextBuild}. */
    public record Next(Kind kind, BuildingType type) {
        static final Next DONE = new Next(Kind.DONE, null);
        static final Next WAIT = new Next(Kind.WAIT, null);
        static final Next NOBLE = new Next(Kind.NOBLE, null);

        static Next build(BuildingType type) {
            return new Next(Kind.BUILD, type);
        }
    }

    /**
     * The building of the template's current step (§5.1, 1–2): the first step with an entry below
     * its target, and in it the building with the lowest level below its target (ties: enum order).
     *
     * @param levels current levels plus queued orders
     * @return null if every step is done
     */
    public static BuildingType stepCandidate(
            List<Map<BuildingType, Integer>> steps, Map<BuildingType, Integer> levels) {
        for (Map<BuildingType, Integer> step : steps) {
            BuildingType best = null;
            for (BuildingType type : BuildingType.values()) {
                Integer target = step.get(type);
                if (target != null && levels.get(type) < target) {
                    if (best == null || levels.get(type) < levels.get(best)) {
                        best = type;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * The next building with the warehouse and farm rules (§5.1, 1–4). The nobles rule (5) is
     * {@link #nextNoble}.
     *
     * @param levels current levels plus queued orders
     * @param current the current levels (a queued upgrade doesn't raise the capacity yet)
     * @param freePop free population (capacity of the current farm minus what buildings with their
     *     queued levels and all units use)
     */
    public static Next nextBuild(
            List<Map<BuildingType, Integer>> steps,
            Map<BuildingType, Integer> levels,
            Map<BuildingType, Integer> current,
            GameData data,
            int freePop) {
        BuildingType candidate = stepCandidate(steps, levels);
        if (candidate == null) {
            return Next.DONE;
        }
        Next next = warehouseRule(candidate, levels, current, data);
        if (next.kind() == Kind.BUILD) {
            next = farmRule(next.type(), levels, current, data, freePop);
        }
        if (next.kind() == Kind.BUILD && next.type() != BuildingType.WAREHOUSE) {
            // A farm upgrade can be too expensive for the warehouse as well.
            next = warehouseRule(next.type(), levels, current, data);
        }
        return next;
    }

    /**
     * The nobles rule (§5.1, 5): while the village wants nobles, the candidate is one NOBLE, and
     * the warehouse and farm rules apply to it (its 50,000 clay need a warehouse of level 20, its
     * 100 population room in the farm). A warehouse or farm upgrade that replaces it goes through
     * the rules like any candidate.
     *
     * @param levels current levels plus queued orders
     * @param current the current levels
     */
    public static Next nextNoble(
            Map<BuildingType, Integer> levels,
            Map<BuildingType, Integer> current,
            GameData data,
            int freePop) {
        UnitDef noble = data.unit(UnitType.NOBLE);
        int capacity = Storage.warehouseCapacity(current.get(BuildingType.WAREHOUSE));
        int farm = Population.farmCapacity(current.get(BuildingType.FARM));
        Next next;
        if (largest(noble.cost()) > capacity) {
            next = upgradeOf(BuildingType.WAREHOUSE, levels, current, data, null);
        } else if (freePop < noble.pop() + BUILD_POP_RESERVE * farm) {
            next = upgradeOf(BuildingType.FARM, levels, current, data, null);
        } else {
            return Next.NOBLE;
        }
        if (next.kind() == Kind.BUILD && next.type() == null) {
            return Next.NOBLE; // the supporting building is at its max level
        }
        if (next.kind() == Kind.BUILD) {
            next = farmRule(next.type(), levels, current, data, freePop);
            if (next.kind() == Kind.BUILD && next.type() != BuildingType.WAREHOUSE) {
                next = warehouseRule(next.type(), levels, current, data);
            }
        }
        return next;
    }

    /** Rule 3: a candidate that costs more than the warehouse holds becomes a warehouse upgrade. */
    private static Next warehouseRule(
            BuildingType candidate,
            Map<BuildingType, Integer> levels,
            Map<BuildingType, Integer> current,
            GameData data) {
        BuildingDef def = data.building(candidate);
        ResourceAmounts cost = BuildRules.cost(def, levels.get(candidate) + 1);
        int capacity = Storage.warehouseCapacity(current.get(BuildingType.WAREHOUSE));
        if (largest(cost) <= capacity) {
            return Next.build(candidate);
        }
        return upgradeOf(BuildingType.WAREHOUSE, levels, current, data, candidate);
    }

    /** Rule 4: without room for the candidate and 10 % of the farm, the farm comes first. */
    private static Next farmRule(
            BuildingType candidate,
            Map<BuildingType, Integer> levels,
            Map<BuildingType, Integer> current,
            GameData data,
            int freePop) {
        BuildingDef def = data.building(candidate);
        int extra = BuildRules.popDelta(def, levels.get(candidate) + 1);
        int capacity = Population.farmCapacity(current.get(BuildingType.FARM));
        if (freePop >= extra + BUILD_POP_RESERVE * capacity) {
            return Next.build(candidate);
        }
        return upgradeOf(BuildingType.FARM, levels, current, data, candidate);
    }

    /**
     * The upgrade of a supporting building ({@code support}) that replaces {@code candidate}: waits
     * if one is already queued, and doesn't replace anything at the max level (nothing is more than
     * that).
     */
    private static Next upgradeOf(
            BuildingType support,
            Map<BuildingType, Integer> levels,
            Map<BuildingType, Integer> current,
            GameData data,
            BuildingType candidate) {
        if (candidate == support) {
            return levels.get(support) > current.get(support) ? Next.WAIT : Next.build(support);
        }
        if (levels.get(support) >= data.building(support).maxLevel()) {
            return Next.build(candidate);
        }
        if (levels.get(support) > current.get(support)) {
            return Next.WAIT;
        }
        return Next.build(support);
    }

    private static int largest(ResourceAmounts cost) {
        return Math.max(cost.wood(), Math.max(cost.clay(), cost.iron()));
    }

    // ---- quiet hours (§5.1, §7) and round timing (§3) ----

    /**
     * Whether {@code now} lies in the window from {@code from} (inclusive) to {@code to}
     * (exclusive) on the clock of {@code zone}. A window that crosses midnight (22:00–08:00) works;
     * {@code from == to} is no window at all.
     */
    public static boolean inQuietWindow(Instant now, LocalTime from, LocalTime to, ZoneId zone) {
        if (from.equals(to)) {
            return false;
        }
        LocalTime time = now.atZone(zone).toLocalTime();
        if (from.isBefore(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        }
        return !time.isBefore(from) || time.isBefore(to);
    }

    /**
     * Whether a round at {@code now} is the last one before the window (§5.1, M13c): {@code now} is
     * outside it and the window starts within {@code seconds} (the longest the next round can take
     * to come). Such a round may fill the paid queue slots so the queue lasts through the window.
     * Off when {@code from == to}.
     */
    public static boolean quietWindowStartsWithin(
            Instant now, LocalTime from, LocalTime to, ZoneId zone, long seconds) {
        if (from.equals(to) || inQuietWindow(now, from, to, zone)) {
            return false;
        }
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime start = local.with(from);
        if (!start.toInstant().isAfter(now)) {
            start = local.plusDays(1).with(from);
        }
        return !start.toInstant().isAfter(now.plusSeconds(seconds));
    }

    /** The longest the next round can take: the interval with the full jitter, rounded up (§3). */
    public static long maxThinkDelaySeconds(long intervalSeconds, double jitter) {
        return (long) Math.ceil(intervalSeconds * (1 + jitter));
    }

    /** The think interval in seconds: {@code npcThinkMinutes ÷ speed} (§3). */
    public static long thinkIntervalSeconds(int thinkMinutes, double speed) {
        return Math.max(1, Math.round(thinkMinutes * 60.0 / speed));
    }

    /** {@code round(interval × uniform(1 − jitter, 1 + jitter))}, at least 1 second. */
    public static long jitteredSeconds(long intervalSeconds, double jitter, RandomSource random) {
        return Math.max(1, Math.round(intervalSeconds * random.nextDouble(1 - jitter, 1 + jitter)));
    }

    // ---- recruiting (§5.2, §5.3) ----

    /**
     * How much more the lord should spend on troops, in resources: {@code troopShare × (build +
     * troops) − troops} (§5.2). Positive = the army is behind.
     */
    public static double troopDebt(double troopShare, double build, double troops) {
        return troopShare * (build + troops) - troops;
    }

    /**
     * The unit with the largest gap between its share of the mix and its share of the army's
     * population (§5.3).
     *
     * @param mix the stage's mix, already limited to the units the village can recruit now
     * @param havePop population of each unit type the village owns (home, supports, on the way, in
     *     orders)
     * @param excluded units that may not be picked now (an order this round, a full queue)
     * @return null if no unit is left
     */
    public static UnitType pickUnit(
            Map<UnitType, Integer> mix, Map<UnitType, Integer> havePop, Set<UnitType> excluded) {
        double shareSum = 0;
        double haveSum = 0;
        for (var entry : mix.entrySet()) {
            shareSum += entry.getValue();
            haveSum += havePop.getOrDefault(entry.getKey(), 0);
        }
        UnitType best = null;
        double bestGap = Double.NEGATIVE_INFINITY;
        for (UnitType type : UnitType.values()) { // enum order = tie-break
            Integer share = mix.get(type);
            if (share == null || excluded.contains(type)) {
                continue;
            }
            double have = haveSum == 0 ? 0 : havePop.getOrDefault(type, 0) / haveSum;
            double gap = share / shareSum - have;
            if (gap > bestGap) {
                bestGap = gap;
                best = type;
            }
        }
        return best;
    }

    /** One order's chunk of {@code unit}: {@code max(1, floor(1 % of the farm capacity / pop))}. */
    public static int chunk(int farmCapacity, UnitDef unit) {
        return Math.max(1, (int) Math.floor(CHUNK_SHARE * farmCapacity / unit.pop()));
    }

    /** The stock left after keeping {@code reserve} aside, per resource, never negative. */
    public static ResourceAmounts minusReserve(ResourceAmounts stock, ResourceAmounts reserve) {
        return new ResourceAmounts(
                Math.max(0, stock.wood() - reserve.wood()),
                Math.max(0, stock.clay() - reserve.clay()),
                Math.max(0, stock.iron() - reserve.iron()));
    }

    /** Whether any resource is at 90 % of the warehouse capacity or more (§5.2). */
    public static boolean nearCapacity(ResourceAmounts stock, int capacity) {
        return largest(stock) >= 0.9 * capacity;
    }

    /** The population troops may not use: 5 % of the farm plus what the next building needs. */
    public static int troopPopReserve(int farmCapacity, int nextBuildingPop) {
        return (int) Math.ceil(TROOP_POP_RESERVE * farmCapacity) + nextBuildingPop;
    }
}
