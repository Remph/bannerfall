package de.bannerfall.rules;

import de.bannerfall.gamedata.UnitType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The research level of every unit type of one player (M30, 02-game-rules §5.4): a missing type is
 * level 1. Unit levels turned off in the world, barbarian villages and NPC lords are {@link #NONE}.
 * Immutable.
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

    /** True if every unit is at level 1. */
    public boolean isNone() {
        return levels.isEmpty();
    }
}
