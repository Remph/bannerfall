package de.bannerfall.gamedata;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One research step of a unit (02-game-rules §5.4, loaded from the {@code levels} list of an entry
 * in {@code gamedata/units.yml}; the first entry is level 2, the unit starts at level 1 with the
 * numbers of its {@link UnitDef}).
 *
 * @param attack, defenseInfantry, defenseCavalry the unit's stats on this level (whole numbers,
 *     never below the level before)
 * @param cost what the research costs
 * @param baseSeconds research time at Smithy level 1, world speed 1 (the smithy level and the speed
 *     shorten it, {@code ResearchRules})
 * @param requires buildings (and minimum levels) the village that researches needs, besides the
 *     requirements of the unit itself
 */
public record UnitLevelDef(
        int attack,
        int defenseInfantry,
        int defenseCavalry,
        ResourceAmounts cost,
        int baseSeconds,
        Map<BuildingType, Integer> requires) {

    public UnitLevelDef {
        var copy = new EnumMap<BuildingType, Integer>(BuildingType.class);
        if (requires != null) {
            copy.putAll(requires);
        }
        requires = Collections.unmodifiableMap(copy);
    }

    public UnitStats stats() {
        return new UnitStats(attack, defenseInfantry, defenseCavalry);
    }
}
