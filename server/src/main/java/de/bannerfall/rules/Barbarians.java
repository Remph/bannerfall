package de.bannerfall.rules;

import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Barbarian villages (02-game-rules §11): how they start and how they grow. Pure functions. */
public final class Barbarians {

    /** The three mines, in the order the dice pick from. */
    public static final List<BuildingType> MINES =
            List.of(BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE);

    /** Growth stops at this mine level. */
    public static final int MAX_MINE_LEVEL = 12;

    static final int WAREHOUSE_LEVEL = 5;

    private static final long SECONDS_PER_DAY = 86_400;

    private Barbarians() {}

    /**
     * Levels of a new barbarian village: each mine 1..5, wall 0..2 (dice in this order: woodcutter,
     * clay pit, iron mine, wall), warehouse 5, farm 1, Town Hall 1, everything else 0.
     */
    public static Map<BuildingType, Integer> startLevels(RandomSource random) {
        return startLevels(random, false);
    }

    /**
     * Same, but with {@code wallsGrowLater} (M29, "barbarians build walls" is on) the wall is 0:
     * the growth round builds it later. The wall die is rolled anyway, so the other dice of a
     * seeded run stay where they were.
     */
    public static Map<BuildingType, Integer> startLevels(
            RandomSource random, boolean wallsGrowLater) {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, 0);
        }
        for (BuildingType mine : MINES) {
            levels.put(mine, random.nextInt(1, 5));
        }
        int startWall = random.nextInt(0, 2);
        levels.put(BuildingType.WALL, wallsGrowLater ? 0 : startWall);
        levels.put(BuildingType.WAREHOUSE, WAREHOUSE_LEVEL);
        levels.put(BuildingType.FARM, 1);
        levels.put(BuildingType.HEADQUARTERS, 1);
        return levels;
    }

    /** Each resource at spawn: half the warehouse capacity. */
    public static double startResources(int warehouseLevel) {
        return Storage.warehouseCapacity(warehouseLevel) / 2.0;
    }

    /**
     * The mine that grows by one level in a growth round: a random one of the mines still below
     * {@link #MAX_MINE_LEVEL} (one draw). Empty when all three are there; then nothing grows.
     */
    public static Optional<BuildingType> mineToGrow(
            Map<BuildingType, Integer> levels, RandomSource random) {
        List<BuildingType> candidates = new ArrayList<>();
        for (BuildingType mine : MINES) {
            if (levels.getOrDefault(mine, 0) < MAX_MINE_LEVEL) {
                candidates.add(mine);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(0, candidates.size() - 1)));
    }

    /**
     * Whether the growth round at {@code at} may build walls (M29): "barbarians build walls" is on,
     * the world is open, and {@code barbarianWallStartDays} real days have passed since it opened.
     * The delay is real time, like the beginner protection, not scaled by {@code speed}; it is
     * measured at the round's due time, so a server that was down catches up the same way.
     */
    public static boolean wallsBuildAt(WorldSettings world, Instant openedAt, Instant at) {
        return world.barbarianWallsEnabled()
                && openedAt != null
                && !at.isBefore(openedAt.plus(Duration.ofDays(world.barbarianWallStartDays())));
    }

    /**
     * Whether a barbarian village's wall grows by one level in a round that may build walls: it is
     * below {@code barbarianWallMaxLevel}, and if rams have knocked it down before ({@code razed}),
     * only when "barbarians rebuild walls" is on. The cap only limits what barbarians build, it
     * never lowers a wall: a village that turned barbarian from a banned or deleted player keeps
     * whatever wall level it had above the cap.
     */
    public static boolean wallGrows(WorldSettings world, int wallLevel, boolean razed) {
        return wallLevel < world.barbarianWallMaxLevel()
                && (!razed || world.barbarianWallsRebuild());
    }

    /** Time between two growth rounds: {@code 24 h / speed}, rounded to whole seconds. */
    public static Duration growthInterval(WorldSettings world) {
        return Duration.ofSeconds(Math.max(1, Math.round(SECONDS_PER_DAY / world.speed())));
    }
}
