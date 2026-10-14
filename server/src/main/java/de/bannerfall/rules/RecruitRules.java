package de.bannerfall.rules;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;

/** Recruit time, cost and lazy completion (02-game-rules §5.2). Pure functions. */
public final class RecruitRules {

    /**
     * Most units in one order (an API limit, not a game rule): more than a farm at level 30 feeds
     * (about 24 000 population), and it keeps {@code count × cost} and {@code count × pop} far
     * inside {@code int} (a noble costs 50 000 iron).
     */
    public static final int MAX_COUNT = 30_000;

    /** Units are dismissed this many times faster than they are recruited (02-game-rules §5.2a). */
    public static final int DISMISS_SPEEDUP = 5;

    /** Recruit time shrinks by this factor per level of the producing building above 1. */
    static final double LEVEL_FACTOR = 0.937;

    private RecruitRules() {}

    /**
     * Seconds per unit: {@code max(1, round(baseTime × 0.937^(buildingLevel−1) / speed))}, with the
     * level of the producing building at enqueue time ({@code ≥ 1}).
     */
    public static long secondsPerUnit(UnitDef unit, int buildingLevel, double speed) {
        if (buildingLevel < 1) {
            throw new IllegalArgumentException(
                    "building level must be at least 1: " + buildingLevel);
        }
        double seconds = unit.baseSeconds() * Math.pow(LEVEL_FACTOR, buildingLevel - 1) / speed;
        return Math.max(1, Math.round(seconds));
    }

    /** Convenience: seconds per unit with the speed of the world. */
    public static long secondsPerUnit(UnitDef unit, int buildingLevel, WorldSettings world) {
        return secondsPerUnit(unit, buildingLevel, world.speed());
    }

    /**
     * Seconds per unit when dismissing: a fifth of the recruit time at the building's level, {@code
     * max(1, round(secondsPerUnit / 5))}. The level is the producing building's current one; for a
     * building that was razed (level 0) the time at level 1 counts.
     */
    public static long dismissSecondsPerUnit(UnitDef unit, int buildingLevel, double speed) {
        double recruit = secondsPerUnit(unit, Math.max(1, buildingLevel), speed);
        return Math.max(1, Math.round(recruit / DISMISS_SPEEDUP));
    }

    /** Convenience: with the speed of the world. */
    public static long dismissSecondsPerUnit(UnitDef unit, int buildingLevel, WorldSettings world) {
        return dismissSecondsPerUnit(unit, buildingLevel, world.speed());
    }

    /** Cost of {@code count ≥ 1} units, paid at enqueue. */
    public static ResourceAmounts cost(UnitDef unit, int count) {
        requireCount(count);
        return unit.cost().times(count);
    }

    /** Population of {@code count ≥ 1} units, paid at enqueue. */
    public static int pop(UnitDef unit, int count) {
        requireCount(count);
        return Math.multiplyExact(unit.pop(), count);
    }

    /**
     * Units of an order finished at {@code now}: {@code clamp(floor((now − startAt) /
     * secondsPerUnit), 0, count)}.
     */
    public static int finishedBy(Instant startAt, long secondsPerUnit, int count, Instant now) {
        long elapsed = Duration.between(startAt, now).toSeconds();
        if (elapsed <= 0) {
            return 0;
        }
        return (int) Math.min(count, elapsed / secondsPerUnit);
    }

    /**
     * How many units of one kind the stock and the free population pay for: the smallest of {@code
     * floor(stock / cost)} per resource and {@code floor(freePop / pop)}, never negative and at
     * most {@link #MAX_COUNT}. Requirements and queues are not looked at.
     *
     * @param stock the village's stock rounded down (what costs are checked against)
     * @param freePop free population, may be negative (then nothing is affordable)
     */
    public static int maxAffordable(UnitDef unit, ResourceAmounts stock, int freePop) {
        // cost and pop are positive (checked when the game data is loaded)
        int max = Math.min(MAX_COUNT, freePop / unit.pop());
        for (ResourceType resource : ResourceType.values()) {
            max = Math.min(max, stock.get(resource) / unit.cost().get(resource));
        }
        return Math.max(0, max);
    }

    private static void requireCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1: " + count);
        }
    }
}
