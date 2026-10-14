package de.bannerfall.gamedata;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One row of 02-game-rules §4.1 (loaded from {@code gamedata/buildings.yml}).
 *
 * @param maxLevel highest reachable level
 * @param startLevel level in a new player village
 * @param baseCost cost (each resource) of reaching level 1
 * @param costFactor cost multiplier per level
 * @param popBase population base
 * @param baseSeconds build time in seconds for level 1 at speed 1, headquarters level 0
 * @param pointsBase points base
 * @param requires other buildings (and their minimum levels) needed before this one can be built
 */
public record BuildingDef(
        int maxLevel,
        int startLevel,
        ResourceAmounts baseCost,
        double costFactor,
        int popBase,
        int baseSeconds,
        int pointsBase,
        Map<BuildingType, Integer> requires) {

    public BuildingDef {
        // An empty "requires: {}" binds to null; keep the canonical enum order for stable output.
        var copy = new EnumMap<BuildingType, Integer>(BuildingType.class);
        if (requires != null) {
            copy.putAll(requires);
        }
        requires = Collections.unmodifiableMap(copy);
    }
}
