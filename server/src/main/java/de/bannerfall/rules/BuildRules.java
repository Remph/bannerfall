package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Building cost, time, population, requirements and points (02-game-rules §4.2). Pure functions.
 */
public final class BuildRules {

    /**
     * Build time shrinks by this factor per Town Hall level (the Town Hall preview shows it too).
     */
    static final double HQ_FACTOR = 0.952;

    private BuildRules() {}

    /** Cost to reach {@code level ≥ 1}: each resource {@code round(base × factor^(L−1))}. */
    public static ResourceAmounts cost(BuildingDef def, int level) {
        requireTargetLevel(level);
        double scale = Math.pow(def.costFactor(), level - 1);
        ResourceAmounts base = def.baseCost();
        return new ResourceAmounts(
                (int) Math.round(base.wood() * scale),
                (int) Math.round(base.clay() * scale),
                (int) Math.round(base.iron() * scale));
    }

    /**
     * The surcharge in percent on an order queued at {@code position} (1-based, the running order
     * is 1; 02-game-rules §4.3): none up to {@code buildQueueSize}, then {@code 25 % × 2^(p −
     * buildQueueSize − 1)}, so +25 %, +50 %, +100 %, +200 %, +400 % for the paid slots 6–10.
     */
    public static int slotSurchargePercent(int position, int buildQueueSize) {
        if (position < 1) {
            throw new IllegalArgumentException("Queue position must be >= 1, was " + position);
        }
        int paidSlot = position - buildQueueSize;
        return paidSlot <= 0 ? 0 : 25 << (paidSlot - 1);
    }

    /**
     * The cost of an order queued at {@code position} (1-based): {@link #cost} plus the slot's
     * surcharge, each resource {@code round(cost × (100 + surcharge) / 100)}.
     */
    public static ResourceAmounts cost(
            BuildingDef def, int level, int position, int buildQueueSize) {
        ResourceAmounts cost = cost(def, level);
        int percent = slotSurchargePercent(position, buildQueueSize);
        if (percent == 0) {
            return cost;
        }
        return new ResourceAmounts(
                surcharged(cost.wood(), percent),
                surcharged(cost.clay(), percent),
                surcharged(cost.iron(), percent));
    }

    private static int surcharged(int amount, int percent) {
        return Math.toIntExact(Math.round(amount * (100L + percent) / 100.0));
    }

    /**
     * Build time in seconds to reach {@code level ≥ 1}: {@code max(1, round(baseTime × 1.18^(L−1) ×
     * 0.952^hq / speed))}. {@code hqLevel} is the Town Hall level when the order is enqueued.
     */
    public static long buildSeconds(BuildingDef def, int level, int hqLevel, double speed) {
        requireTargetLevel(level);
        double seconds =
                def.baseSeconds()
                        * Math.pow(1.18, level - 1)
                        * Math.pow(HQ_FACTOR, hqLevel)
                        / speed;
        return Math.max(1, Math.round(seconds));
    }

    /**
     * Extra population needed for an upgrade to {@code toLevel}: {@code popTotal(L) −
     * popTotal(L−1)}.
     */
    public static int popDelta(BuildingDef def, int toLevel) {
        requireTargetLevel(toLevel);
        return Population.buildingPop(def, toLevel) - Population.buildingPop(def, toLevel - 1);
    }

    /** Convenience: build time with the speed of the world. */
    public static long buildSeconds(BuildingDef def, int level, int hqLevel, WorldSettings world) {
        return buildSeconds(def, level, hqLevel, world.speed());
    }

    /**
     * The requirements that are not met yet, in canonical building order (empty = all met).
     *
     * @param requires what is needed (from {@code BuildingDef.requires()} or {@code
     *     UnitDef.requires()})
     * @param levels current plus queued levels of the village; a missing entry counts as level 0
     */
    public static List<Requirement> missingRequirements(
            Map<BuildingType, Integer> requires, Map<BuildingType, Integer> levels) {
        List<Requirement> missing = new ArrayList<>();
        for (var entry : requires.entrySet()) {
            if (levels.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                missing.add(new Requirement(entry.getKey(), entry.getValue()));
            }
        }
        return missing;
    }

    /**
     * The levels of a village with its queued orders counted in: the current level plus one per
     * queued order of that building (02-game-rules §4.3, §5.3). Every building has an entry.
     *
     * @param current the current levels (a missing entry counts as level 0)
     * @param queued the building of every queued order, in any order
     */
    public static Map<BuildingType, Integer> levelsWithQueued(
            Map<BuildingType, Integer> current, Collection<BuildingType> queued) {
        var levels = new EnumMap<BuildingType, Integer>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, current.getOrDefault(type, 0));
        }
        for (BuildingType type : queued) {
            levels.merge(type, 1, Integer::sum);
        }
        return levels;
    }

    /**
     * The new start of a queued order after an order before it was cancelled (02-game-rules §4.3):
     * right after the previous order that stays, but not before {@code now}, and never later than
     * the old start. The last rule only matters for an order that should already have started (the
     * event worker is behind, e.g. after downtime): it keeps its time instead of being pushed back.
     * It also means an order whose completion is due is never moved, so a re-timing that comes too
     * late for its event ({@code EventScheduler.reschedule} answers false) changes nothing.
     *
     * @param oldStart where the order started before
     * @param previousEnd end of the order in front of it that stays; null if there is none
     */
    public static Instant startAfterCancel(Instant oldStart, Instant previousEnd, Instant now) {
        Instant earliest = startAfter(previousEnd, now);
        return earliest.isBefore(oldStart) ? earliest : oldStart;
    }

    /**
     * The start of an order that runs right behind the order in front of it, but not before {@code
     * now}. Used when the waiting orders are re-timed after a move (02-game-rules §4.3,
     * <i>Move</i>).
     *
     * @param previousEnd end of the order in front of it; null if there is none
     */
    public static Instant startAfter(Instant previousEnd, Instant now) {
        return previousEnd == null || previousEnd.isBefore(now) ? now : previousEnd;
    }

    /** Points of a building at {@code level}: {@code Σ_{l=1..L} round(ptsBase × 1.2^(l−1))}. */
    public static int points(BuildingDef def, int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative: " + level);
        }
        int sum = 0;
        for (int l = 1; l <= level; l++) {
            sum += (int) Math.round(def.pointsBase() * Math.pow(1.2, l - 1));
        }
        return sum;
    }

    /** Village points: the points of all buildings (missing entries count as level 0). */
    public static int villagePoints(GameData data, Map<BuildingType, Integer> levels) {
        int sum = 0;
        for (BuildingType type : BuildingType.values()) {
            sum += points(data.building(type), levels.getOrDefault(type, 0));
        }
        return sum;
    }

    private static void requireTargetLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("target level must be at least 1: " + level);
        }
    }
}
