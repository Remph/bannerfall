package de.bannerfall.report;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Copies for the maps inside payloads: fixed order (enum order), can't be changed, and a missing
 * map (an old stored payload) reads as empty.
 */
final class ReportMaps {

    private ReportMaps() {}

    /** Only the unit types with a count &gt; 0, like everywhere in the API. */
    static Map<UnitType, Integer> units(Map<UnitType, Integer> units) {
        var copy = new EnumMap<UnitType, Integer>(UnitType.class);
        if (units != null) {
            units.forEach(
                    (type, count) -> {
                        if (count != null && count > 0) {
                            copy.put(type, count);
                        }
                    });
        }
        return Collections.unmodifiableMap(copy);
    }

    /** The building levels as given (the scouts see every building, level 0 included). */
    static Map<BuildingType, Integer> buildings(Map<BuildingType, Integer> levels) {
        var copy = new EnumMap<BuildingType, Integer>(BuildingType.class);
        if (levels != null) {
            copy.putAll(levels);
        }
        return Collections.unmodifiableMap(copy);
    }
}
