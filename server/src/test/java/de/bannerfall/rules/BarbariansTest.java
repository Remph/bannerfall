package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BarbariansTest {

    @Test
    void startLevelsWithTheLowestDice() {
        Map<BuildingType, Integer> levels = Barbarians.startLevels(FixedRandomSource.constant(0));

        assertThat(levels).hasSize(BuildingType.values().length);
        assertThat(levels.get(BuildingType.WOODCUTTER)).isEqualTo(1);
        assertThat(levels.get(BuildingType.CLAY_PIT)).isEqualTo(1);
        assertThat(levels.get(BuildingType.IRON_MINE)).isEqualTo(1);
        assertThat(levels.get(BuildingType.WALL)).isZero();
        assertThat(levels.get(BuildingType.WAREHOUSE)).isEqualTo(5);
        assertThat(levels.get(BuildingType.FARM)).isEqualTo(1);
        assertThat(levels.get(BuildingType.HEADQUARTERS)).isEqualTo(1);
        assertThat(levels.get(BuildingType.BARRACKS)).isZero();
        assertThat(levels.get(BuildingType.RALLY_POINT)).isZero();
    }

    @Test
    void startLevelsWithTheHighestDice() {
        Map<BuildingType, Integer> levels =
                Barbarians.startLevels(FixedRandomSource.constant(0.999));

        assertThat(levels.get(BuildingType.WOODCUTTER)).isEqualTo(5);
        assertThat(levels.get(BuildingType.CLAY_PIT)).isEqualTo(5);
        assertThat(levels.get(BuildingType.IRON_MINE)).isEqualTo(5);
        assertThat(levels.get(BuildingType.WALL)).isEqualTo(2);
    }

    @Test
    void theDiceGoToWoodClayIronThenTheWall() {
        // nextInt(1, 5) of 0.0, 0.2, 0.4 → 1, 2, 3; nextInt(0, 2) of 0.7 → 2
        Map<BuildingType, Integer> levels =
                Barbarians.startLevels(FixedRandomSource.sequence(0.0, 0.2, 0.4, 0.7));

        assertThat(levels.get(BuildingType.WOODCUTTER)).isEqualTo(1);
        assertThat(levels.get(BuildingType.CLAY_PIT)).isEqualTo(2);
        assertThat(levels.get(BuildingType.IRON_MINE)).isEqualTo(3);
        assertThat(levels.get(BuildingType.WALL)).isEqualTo(2);
    }

    @Test
    void withWallsGrowingLaterTheWallStartsAtZeroAndTheOtherDiceStayWhereTheyWere() {
        // the wall die is rolled anyway (0.7 would give 2): the dice of a seeded run don't shift
        Map<BuildingType, Integer> levels =
                Barbarians.startLevels(FixedRandomSource.sequence(0.0, 0.2, 0.4, 0.7), true);

        assertThat(levels.get(BuildingType.WOODCUTTER)).isEqualTo(1);
        assertThat(levels.get(BuildingType.CLAY_PIT)).isEqualTo(2);
        assertThat(levels.get(BuildingType.IRON_MINE)).isEqualTo(3);
        assertThat(levels.get(BuildingType.WALL)).isZero();
        assertThat(levels.get(BuildingType.WAREHOUSE)).isEqualTo(5);
    }

    @Test
    void wallsAreBuiltFromTheStartDayOnAndOnlyWhenSwitchedOn() {
        Instant opened = Instant.parse("2026-10-14T12:00:00Z");
        WorldSettings on = WorldSettings.defaults().withBarbarianWalls(true, 3, 5, false);
        Instant atDelay = opened.plus(Duration.ofDays(3));

        assertThat(Barbarians.wallsBuildAt(on, opened, atDelay.minusSeconds(1))).isFalse();
        assertThat(Barbarians.wallsBuildAt(on, opened, atDelay)).isTrue();
        assertThat(Barbarians.wallsBuildAt(on, opened, atDelay.plus(Duration.ofDays(9)))).isTrue();
        // a delay of 0 days: from the opening on
        assertThat(
                        Barbarians.wallsBuildAt(
                                WorldSettings.defaults().withBarbarianWalls(true, 0, 5, false),
                                opened,
                                opened))
                .isTrue();
        // off (the default), or no opening yet (pre-game)
        assertThat(Barbarians.wallsBuildAt(WorldSettings.defaults(), opened, atDelay)).isFalse();
        assertThat(Barbarians.wallsBuildAt(on, null, atDelay)).isFalse();
    }

    @Test
    void aWallGrowsBelowTheCapAndNeverPastIt() {
        WorldSettings world = WorldSettings.defaults().withBarbarianWalls(true, 3, 5, false);

        assertThat(Barbarians.wallGrows(world, 0, false)).isTrue();
        assertThat(Barbarians.wallGrows(world, 4, false)).isTrue();
        assertThat(Barbarians.wallGrows(world, 5, false)).isFalse();
        // above the cap (a deleted player's village): left alone, not lowered
        assertThat(Barbarians.wallGrows(world, 12, false)).isFalse();
        assertThat(Barbarians.wallGrows(world.withBarbarianWalls(true, 3, 0, false), 0, false))
                .isFalse();
    }

    @Test
    void aWallThatRamsKnockedDownGrowsBackOnlyWithRebuild() {
        WorldSettings off = WorldSettings.defaults().withBarbarianWalls(true, 3, 5, false);
        WorldSettings rebuild = WorldSettings.defaults().withBarbarianWalls(true, 3, 5, true);

        assertThat(Barbarians.wallGrows(off, 2, true)).isFalse();
        assertThat(Barbarians.wallGrows(off, 0, true)).isFalse();
        assertThat(Barbarians.wallGrows(rebuild, 2, true)).isTrue();
        assertThat(Barbarians.wallGrows(rebuild, 5, true)).isFalse();
    }

    @Test
    void startResourcesAreHalfTheWarehouse() {
        assertThat(Barbarians.startResources(5)).isEqualTo(Storage.warehouseCapacity(5) / 2.0);
    }

    @Test
    void growthPicksAmongTheMinesBelowTwelve() {
        Map<BuildingType, Integer> levels = mines(12, 3, 12);

        assertThat(Barbarians.mineToGrow(levels, FixedRandomSource.constant(0)))
                .contains(BuildingType.CLAY_PIT);
        assertThat(Barbarians.mineToGrow(levels, FixedRandomSource.constant(0.999)))
                .contains(BuildingType.CLAY_PIT);
    }

    @Test
    void growthUsesOneDrawOverTheCandidates() {
        Map<BuildingType, Integer> levels = mines(1, 12, 11);

        assertThat(Barbarians.mineToGrow(levels, FixedRandomSource.constant(0)))
                .contains(BuildingType.WOODCUTTER);
        assertThat(Barbarians.mineToGrow(levels, FixedRandomSource.constant(0.6)))
                .contains(BuildingType.IRON_MINE);
    }

    @Test
    void nothingGrowsWhenAllMinesAreAtTwelve() {
        assertThat(Barbarians.mineToGrow(mines(12, 12, 12), FixedRandomSource.constant(0)))
                .isEmpty();
    }

    @Test
    void growthIntervalIsADayDividedBySpeed() {
        assertThat(Barbarians.growthInterval(WorldSettings.defaults()))
                .isEqualTo(Duration.ofHours(24));
        assertThat(Barbarians.growthInterval(WorldSettings.defaults().withSpeed(3)))
                .isEqualTo(Duration.ofHours(8));
        assertThat(Barbarians.growthInterval(WorldSettings.defaults().withSpeed(7)))
                .isEqualTo(Duration.ofSeconds(12_343)); // 86 400 / 7 = 12 342.86
    }

    private static Map<BuildingType, Integer> mines(int wood, int clay, int iron) {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        levels.put(BuildingType.WOODCUTTER, wood);
        levels.put(BuildingType.CLAY_PIT, clay);
        levels.put(BuildingType.IRON_MINE, iron);
        return levels;
    }
}
