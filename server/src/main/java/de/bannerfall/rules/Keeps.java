package de.bannerfall.rules;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Banner keeps (02-game-rules §22): strong barbarian villages in a ring around the map centre that
 * count for the KEEPS victory. Where they go and how they start. Pure functions.
 */
public final class Keeps {

    /** Every keep starts with this wall. */
    public static final int WALL = 10;

    /** Mines, warehouse, farm and Town Hall of a new keep. */
    static final int BUILDING_LEVEL = 10;

    /** The garrison a new keep starts with. */
    public static final Map<UnitType, Integer> GARRISON =
            Map.of(UnitType.SPEARMAN, 1000, UnitType.SWORDSMAN, 1000, UnitType.HEAVY_CAVALRY, 100);

    /** The ring is at least this far from the centre (the initial barbarians start at 4). */
    static final double MIN_RADIUS = 3;

    /** Fields between two neighbouring keeps on the ring, about. */
    static final double SPACING = 2.5;

    private Keeps() {}

    /** Levels of a new keep: mines, warehouse, farm and Town Hall 10, wall 10, nothing else. */
    public static Map<BuildingType, Integer> startLevels() {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, 0);
        }
        for (BuildingType mine : Barbarians.MINES) {
            levels.put(mine, BUILDING_LEVEL);
        }
        levels.put(BuildingType.WAREHOUSE, BUILDING_LEVEL);
        levels.put(BuildingType.FARM, BUILDING_LEVEL);
        levels.put(BuildingType.HEADQUARTERS, BUILDING_LEVEL);
        levels.put(BuildingType.WALL, WALL);
        return levels;
    }

    /**
     * The ideal fields of {@code count} keeps: evenly on a ring around the centre, the first one
     * straight north; a single keep sits on the centre itself. The ring grows with the count so
     * neighbours keep their distance.
     */
    public static List<Coordinates> ring(int mapSize, int count) {
        int c = mapSize / 2;
        List<Coordinates> spots = new ArrayList<>();
        if (count == 1) {
            spots.add(new Coordinates(c, c));
            return spots;
        }
        double radius = Math.max(MIN_RADIUS, count * SPACING / (2 * Math.PI));
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / count;
            spots.add(
                    new Coordinates(
                            (int) Math.round(c + radius * Math.cos(angle)),
                            (int) Math.round(c + radius * Math.sin(angle))));
        }
        return spots;
    }

    /**
     * The field for a keep that should go on {@code ideal}: the field itself if the spacing rule
     * allows it, otherwise the nearest free one (rings of growing distance, scanned row by row so
     * the result does not depend on chance). Empty if nothing within 10 fields is free.
     */
    public static Optional<Coordinates> spotNear(
            Coordinates ideal, int mapSize, Predicate<Coordinates> occupied) {
        for (int distance = 0; distance <= 10; distance++) {
            for (int dy = -distance; dy <= distance; dy++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != distance) {
                        continue;
                    }
                    var spot = new Coordinates(ideal.x() + dx, ideal.y() + dy);
                    if (spot.x() >= 0
                            && spot.x() < mapSize
                            && spot.y() >= 0
                            && spot.y() < mapSize
                            && MapPlacement.spacingOk(spot, occupied)) {
                        return Optional.of(spot);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
