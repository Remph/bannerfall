package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitLevelDef;
import java.util.EnumMap;
import java.util.Map;

/** Research time of a unit level (02-game-rules §5.4). Pure functions. */
public final class ResearchRules {

    /** Research time shrinks by this factor per Smithy level above 1 (like the recruit time). */
    static final double LEVEL_FACTOR = 0.937;

    private ResearchRules() {}

    /**
     * Seconds of one research: {@code max(1, round(baseSeconds × 0.937^(smithyLevel−1) / speed))}
     * with the level of the smithy in the village that researches, at the start of the order.
     *
     * @param smithyLevel 1 or more
     */
    public static long seconds(UnitLevelDef level, int smithyLevel, double speed) {
        if (smithyLevel < 1) {
            throw new IllegalArgumentException("smithy level must be at least 1: " + smithyLevel);
        }
        double seconds = level.baseSeconds() * Math.pow(LEVEL_FACTOR, smithyLevel - 1) / speed;
        return Math.max(1, Math.round(seconds));
    }

    /**
     * What the village needs to research {@code level} of a unit: the requirements of the unit
     * itself (it has to be recruitable there) and those of the level, the higher level of a
     * building named by both.
     */
    public static Map<BuildingType, Integer> requirements(UnitDef unit, int level) {
        var requires = new EnumMap<BuildingType, Integer>(BuildingType.class);
        requires.putAll(unit.requires());
        unit.levelDef(level)
                .requires()
                .forEach((type, needed) -> requires.merge(type, needed, Math::max));
        return requires;
    }
}
