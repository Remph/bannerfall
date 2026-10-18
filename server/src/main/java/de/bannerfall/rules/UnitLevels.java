package de.bannerfall.rules;

import de.bannerfall.gamedata.UnitType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The research level of every unit type of one player (M30, 02-game-rules §5.4): a missing type is
 * level 1. Barbarian villages and NPC lords are {@link #NONE}; with unit levels turned off in the
 * world only the Banner Hall's units keep their trained levels (1.4). Immutable.
 */
public record UnitLevels(Map<UnitType, Integer> levels) {

    /** Everything at level 1. */
    public static final UnitLevels NONE = new UnitLevels(Map.of());

    public UnitLevels {
        var copy = new EnumMap<UnitType, Integer>(UnitType.class);
        levels.forEach(
                (type, level) -> {
                    if (level < 1) {
                        throw new IllegalArgumentException(
                                "level must be at least 1: " + type + " " + level);
                    }
                    if (level > 1) {
                        copy.put(type, level);
                    }
                });
        levels = Collections.unmodifiableMap(copy);
    }

    /** The level of a unit type, 1 if it was never researched. */
    public int get(UnitType type) {
        return levels.getOrDefault(type, 1);
    }

    /** The levels of the unit types that pass {@code keep}; every other type is level 1. */
    public UnitLevels only(Predicate<UnitType> keep) {
        var kept = new EnumMap<UnitType, Integer>(UnitType.class);
        levels.forEach(
                (type, level) -> {
                    if (keep.test(type)) {
                        kept.put(type, level);
                    }
                });
        return new UnitLevels(kept);
    }

    /** True if every unit is at level 1. */
    public boolean isNone() {
        return levels.isEmpty();
    }
}
