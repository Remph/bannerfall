package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import java.util.Map;

/** Population: farm capacity and what buildings and units use (02-game-rules §4.2, §5.3). */
public final class Population {

    private Population() {}

    /** Extra farm capacity in % at levels 10–15 (1.2.3: the mid game got too little room). */
    static final double MID_BONUS_PERCENT = 15;

    /** Extra farm capacity in % from level 22 on (room for thieves, paladin and Banner Hall). */
    static final double LATE_BONUS_PERCENT = 3;

    /**
     * Farm capacity at {@code level ≥ 1}: {@code round(round(240 × 1.172^(L−1)) × (1 + bonus(L) /
     * 100))}, see {@link #bonusPercent(int)}.
     */
    public static int farmCapacity(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("farm level must be at least 1: " + level);
        }
        long base = Math.round(240 * Math.pow(1.172, level - 1));
        return (int) Math.round(base * (1 + bonusPercent(level) / 100));
    }

    /**
     * The farm bonus in %: 0 up to level 5, rising evenly to {@link #MID_BONUS_PERCENT} at 10, held
     * to 15, falling evenly to {@link #LATE_BONUS_PERCENT} at 22 and held from there.
     */
    static double bonusPercent(int level) {
        if (level <= 5) {
            return 0;
        }
        if (level < 10) {
            return MID_BONUS_PERCENT * (level - 5) / 5;
        }
        if (level <= 15) {
            return MID_BONUS_PERCENT;
        }
        if (level < 22) {
            return MID_BONUS_PERCENT + (LATE_BONUS_PERCENT - MID_BONUS_PERCENT) * (level - 15) / 7;
        }
        return LATE_BONUS_PERCENT;
    }

    /**
     * Population used by a building at {@code level}: {@code L == 0 ? 0 : round(popBase ×
     * 1.17^(L−1))}.
     */
    public static int buildingPop(BuildingDef def, int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative: " + level);
        }
        return level == 0 ? 0 : (int) Math.round(def.popBase() * Math.pow(1.17, level - 1));
    }

    /**
     * Population used by all buildings. {@code levels} should contain current plus queued levels
     * (missing entries count as level 0).
     */
    public static int usedByBuildings(GameData data, Map<BuildingType, Integer> levels) {
        int sum = 0;
        for (BuildingType type : BuildingType.values()) {
            sum += buildingPop(data.building(type), levels.getOrDefault(type, 0));
        }
        return sum;
    }

    /**
     * Population used by units. {@code counts} must contain <b>all</b> units of the village: home
     * stack, stacks supporting elsewhere, units in movements and units in unfinished recruit
     * orders.
     */
    public static int usedByUnits(GameData data, Map<UnitType, Integer> counts) {
        int sum = 0;
        for (var entry : counts.entrySet()) {
            sum =
                    Math.addExact(
                            sum,
                            Math.multiplyExact(data.unit(entry.getKey()).pop(), entry.getValue()));
        }
        return sum;
    }

    /** {@code farmCapacity − used}. A queued farm upgrade doesn't count until it completes. */
    public static int free(int farmLevel, int used) {
        return farmCapacity(farmLevel) - used;
    }
}
