package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;

/** Rams against the wall and catapults against a building (02-game-rules §9.1, §9.2). Pure. */
public final class Siege {

    private Siege() {}

    /** Rams needed to take a wall at {@code level ≥ 1} down by one level: {@code 2 + L}. */
    public static int ramsNeeded(int level) {
        requireLevel(level);
        return 2 + level;
    }

    /** Catapults needed to take a building at {@code level ≥ 1} down one level: {@code 4 + 2L}. */
    public static int catsNeeded(int level) {
        requireLevel(level);
        return 4 + 2 * level;
    }

    /**
     * The wall level after {@code rams} have worked on it: level by level from the top, as long as
     * the rams left pay for the current level. Rams that don't pay for a whole level do nothing.
     * Used twice in a battle: with half the rams sent for the effective wall during the battle, and
     * with the surviving rams for the permanent damage after a won battle.
     */
    public static int wallAfterRams(int level, int rams) {
        requireNotNegative("level", level);
        requireNotNegative("rams", rams);
        while (level > 0 && rams >= ramsNeeded(level)) {
            rams -= ramsNeeded(level);
            level--;
        }
        return level;
    }

    /**
     * The level of the catapults' target after {@code cats} surviving catapults have worked on it:
     * like {@link #wallAfterRams}, but never below {@code minLevel} ({@link #catapultMinLevel}).
     */
    public static int levelAfterCatapults(int level, int cats, int minLevel) {
        requireNotNegative("level", level);
        requireNotNegative("cats", cats);
        requireNotNegative("minLevel", minLevel);
        while (level > minLevel && cats >= catsNeeded(level)) {
            cats -= catsNeeded(level);
            level--;
        }
        return level;
    }

    /**
     * The lowest level catapults can bring a building down to: 1 for the buildings a village can't
     * work without (Town Hall, farm, warehouse, rally point), 0 for all others.
     */
    public static int catapultMinLevel(BuildingType target) {
        return switch (target) {
            case HEADQUARTERS, FARM, WAREHOUSE, RALLY_POINT -> 1;
            default -> 0;
        };
    }

    private static void requireLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1: " + level);
        }
    }

    private static void requireNotNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
