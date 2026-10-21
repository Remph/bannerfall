package de.bannerfall.gamedata;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One row of 02-game-rules §5.1 (loaded from {@code gamedata/units.yml}).
 *
 * @param building the building that recruits this unit
 * @param requires buildings (and minimum levels) needed to recruit it
 * @param cost cost per unit
 * @param pop population per unit
 * @param defenseInfantry defence value against infantry
 * @param defenseCavalry defence value against cavalry
 * @param defenseArcher defence value against archers (1.6)
 * @param attackType decides which defence value of the defender counts against this unit
 * @param minutesPerField walking speed
 * @param carry loot capacity per unit
 * @param baseSeconds recruit time per unit at barracks level 1, speed 1
 * @param levels the research steps above level 1 (M30): the first entry is level 2, the second
 *     level 3, … Empty for a unit that has no levels (scout, siege, noble)
 */
public record UnitDef(
        BuildingType building,
        Map<BuildingType, Integer> requires,
        ResourceAmounts cost,
        int pop,
        int attack,
        int defenseInfantry,
        int defenseCavalry,
        int defenseArcher,
        AttackType attackType,
        double minutesPerField,
        int carry,
        int baseSeconds,
        List<UnitLevelDef> levels) {

    public UnitDef {
        var copy = new EnumMap<BuildingType, Integer>(BuildingType.class);
        if (requires != null) {
            copy.putAll(requires);
        }
        requires = Collections.unmodifiableMap(copy);
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    /** The highest level of the unit: 1 without research steps. */
    public int maxLevel() {
        return 1 + levels.size();
    }

    /**
     * The stats on a level; a level above the maximum counts as the maximum (so a changed units.yml
     * never breaks a stored level).
     *
     * @param level 1 or more
     */
    public UnitStats stats(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1: " + level);
        }
        if (level == 1 || levels.isEmpty()) {
            return new UnitStats(attack, defenseInfantry, defenseCavalry, defenseArcher);
        }
        return levels.get(Math.min(level, maxLevel()) - 2).stats();
    }

    /**
     * The research that raises the unit to {@code level} (2 or more, at most {@link #maxLevel}).
     */
    public UnitLevelDef levelDef(int level) {
        if (level < 2 || level > maxLevel()) {
            throw new IllegalArgumentException(
                    "the unit has no research for level " + level + " (max " + maxLevel() + ")");
        }
        return levels.get(level - 2);
    }
}
