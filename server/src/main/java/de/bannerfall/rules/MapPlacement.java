package de.bannerfall.rules;

import de.bannerfall.common.Coordinates;
import de.bannerfall.common.RandomSource;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where new villages go (02-game-rules §2). The search is a spiral outwards; randomness comes from
 * the injected {@link RandomSource}, the map from an occupancy check ("is there a village on this
 * field?"), so the class needs neither Spring nor a database.
 *
 * <p>An empty result means "no free spot found" (the service turns it into {@code WORLD_FULL}).
 */
public final class MapPlacement {

    private static final int MAX_ATTEMPTS = 200;
    private static final double RADIUS_STEP = 0.25;

    private MapPlacement() {}

    /**
     * Spot for a new player: around the map center, further out with every player village ({@code r
     * = 8 + 2.5 × sqrt(n) + random(−2, 2)}).
     *
     * @param playerVillageCount number of player-owned villages so far
     */
    public static Optional<Coordinates> findPlayerSpot(
            int mapSize,
            int playerVillageCount,
            Predicate<Coordinates> occupied,
            RandomSource random) {
        double radius = 8 + 2.5 * Math.sqrt(playerVillageCount) + random.nextDouble(-2.0, 2.0);
        return spiral(center(mapSize), radius, mapSize, occupied, random);
    }

    /** Spot for a barbarian village near {@code around} ({@code r = random(3, 8)}). */
    public static Optional<Coordinates> findBarbarianSpotNear(
            Coordinates around, int mapSize, Predicate<Coordinates> occupied, RandomSource random) {
        double radius = random.nextDouble(3, 8);
        return spiral(around, radius, mapSize, occupied, random);
    }

    /**
     * Spot for one of the initial barbarian villages around the center ({@code r = random(4, 30)}).
     */
    public static Optional<Coordinates> findInitialBarbarianSpot(
            int mapSize, Predicate<Coordinates> occupied, RandomSource random) {
        double radius = random.nextDouble(4, 30);
        return spiral(center(mapSize), radius, mapSize, occupied, random);
    }

    /**
     * The spacing rule: no village within distance &lt; 2, i.e. the field itself and its eight
     * neighbours must be free.
     */
    public static boolean spacingOk(Coordinates spot, Predicate<Coordinates> occupied) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (occupied.test(new Coordinates(spot.x() + dx, spot.y() + dy))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Coordinates center(int mapSize) {
        return new Coordinates(mapSize / 2, mapSize / 2);
    }

    private static Optional<Coordinates> spiral(
            Coordinates around,
            double startRadius,
            int mapSize,
            Predicate<Coordinates> occupied,
            RandomSource random) {
        double radius = startRadius;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0, 2 * Math.PI);
            var spot =
                    new Coordinates(
                            (int) Math.round(around.x() + radius * Math.cos(angle)),
                            (int) Math.round(around.y() + radius * Math.sin(angle)));
            if (insideMap(spot, mapSize) && spacingOk(spot, occupied)) {
                return Optional.of(spot);
            }
            radius += RADIUS_STEP;
        }
        return Optional.empty();
    }

    private static boolean insideMap(Coordinates spot, int mapSize) {
        return spot.x() >= 0 && spot.x() < mapSize && spot.y() >= 0 && spot.y() < mapSize;
    }
}
