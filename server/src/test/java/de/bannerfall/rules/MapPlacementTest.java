package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.common.Coordinates;
import de.bannerfall.common.RandomSource;
import de.bannerfall.world.MapSpread;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * With a constant 0.5 the dice are easy to follow on a 100×100 map (center 50|50): the radius
 * jitter is 0, the angle is π, so the search walks west from the center.
 */
class MapPlacementTest {

    private static final int MAP = 100;
    private static final Predicate<Coordinates> EMPTY = spot -> false;

    private static Predicate<Coordinates> occupiedBy(Coordinates... villages) {
        return Set.of(villages)::contains;
    }

    // ---- spacing rule ----

    @Test
    void spacingRuleForbidsTheFieldAndItsEightNeighbours() {
        var occupied = occupiedBy(new Coordinates(10, 10));

        assertThat(MapPlacement.spacingOk(new Coordinates(10, 10), occupied)).isFalse();
        assertThat(MapPlacement.spacingOk(new Coordinates(11, 11), occupied)).isFalse(); // √2 < 2
        assertThat(MapPlacement.spacingOk(new Coordinates(9, 10), occupied)).isFalse();
    }

    @Test
    void spacingRuleAllowsDistanceTwoOrMore() {
        var occupied = occupiedBy(new Coordinates(10, 10));

        assertThat(MapPlacement.spacingOk(new Coordinates(12, 10), occupied)).isTrue(); // = 2
        assertThat(MapPlacement.spacingOk(new Coordinates(11, 12), occupied)).isTrue(); // √5
        assertThat(MapPlacement.spacingOk(new Coordinates(30, 30), occupied)).isTrue();
    }

    // ---- player spot ----

    @Test
    void firstPlayerIsEightFieldsFromTheCenter() {
        var spot = MapPlacement.findPlayerSpot(MAP, 1.0, 0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(42, 50));
    }

    @Test
    void theAngleComesFromTheRandomSource() {
        // 1st draw: radius jitter 0 → r = 8; 2nd draw 0.25 → angle π/2 → straight "down".
        var spot =
                MapPlacement.findPlayerSpot(
                        MAP, 1.0, 0, EMPTY, FixedRandomSource.sequence(0.5, 0.25));

        assertThat(spot).contains(new Coordinates(50, 58));
    }

    @Test
    void theRadiusGrowsWithThePlayerVillageCount() {
        // r = 8 + 2.5 × sqrt(4) = 13
        var spot = MapPlacement.findPlayerSpot(MAP, 1.0, 4, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(37, 50));
    }

    @Test
    void anOccupiedSpotMakesTheSearchWalkOutwardsInQuarterFieldSteps() {
        var occupied = occupiedBy(new Coordinates(42, 50));

        var spot =
                MapPlacement.findPlayerSpot(MAP, 1.0, 0, occupied, FixedRandomSource.constant(0.5));

        // r = 8, 8.25 … 9.5 all land within one field of (42|50); r = 9.75 gives (40|50).
        assertThat(spot).contains(new Coordinates(40, 50));
    }

    @Test
    void aDiagonalNeighbourAlsoBlocksASpot() {
        var occupied = occupiedBy(new Coordinates(43, 51));

        var spot =
                MapPlacement.findPlayerSpot(MAP, 1.0, 0, occupied, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(41, 50));
    }

    // ---- next to a guild mate (1.6) ----

    @Test
    void aGuildMateStartsFourToSixFieldsAway() {
        // radius draw 0.5 → r = 5; angle draw 0.5 → π → straight "left" of the mate
        var mate = new Coordinates(20, 20);
        var spot =
                MapPlacement.findSpotNearGuildMate(
                        mate, MAP, occupiedBy(mate), FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(15, 20));
    }

    @Test
    void aTakenSpotNextToTheMateMakesTheSearchWalkOutwards() {
        var mate = new Coordinates(20, 20);
        var spot =
                MapPlacement.findSpotNearGuildMate(
                        mate,
                        MAP,
                        occupiedBy(mate, new Coordinates(15, 20)),
                        FixedRandomSource.constant(0.5));

        // 5 → 5.25 → … the first spot two fields clear of (15|20) is at r = 6.75
        assertThat(spot).contains(new Coordinates(13, 20));
    }

    @Test
    void spotsOutsideTheMapAreSkippedAndATinyMapIsFull() {
        // Map 10×10: center 5, but r starts at 8 → every try is left of the map.
        var spot = MapPlacement.findPlayerSpot(10, 1.0, 0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).isEmpty();
    }

    @Test
    void aFullWorldGivesNoSpot() {
        assertThat(
                        MapPlacement.findPlayerSpot(
                                MAP, 1.0, 0, spot -> true, FixedRandomSource.constant(0.5)))
                .isEmpty();
    }

    @Test
    void aWiderSpreadPutsThePlayerFurtherOut() {
        // r = 8 × 1.6 = 12.8 → 50 − 12.8 = 37.2 rounds to 37; CLOSE: 8 × 0.8 = 6.4 → 43.6 → 44
        assertThat(MapPlacement.findPlayerSpot(MAP, 1.6, 0, EMPTY, FixedRandomSource.constant(0.5)))
                .contains(new Coordinates(37, 50));
        assertThat(MapPlacement.findPlayerSpot(MAP, 0.8, 0, EMPTY, FixedRandomSource.constant(0.5)))
                .contains(new Coordinates(44, 50));
    }

    @Test
    void theSpreadScalesTheWholeRadiusOfLaterPlayers() {
        // n = 4: (8 + 2.5 × 2) × 1.3 = 16.9 → 50 − 16.9 = 33.1 → 33
        var spot = MapPlacement.findPlayerSpot(MAP, 1.3, 4, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(33, 50));
    }

    // ---- barbarians ----

    @Test
    void barbarianNearAPointUsesRadiusThreeToEight() {
        // r = 3 + 5 × 0.5 = 5.5 → 50 − 5.5 = 44.5 rounds half up to 45.
        var spot =
                MapPlacement.findBarbarianSpotNear(
                        new Coordinates(50, 50), MAP, 1.0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(45, 50));
    }

    @Test
    void barbarianNearAPointKeepsItsDistanceFromTheVillage() {
        var village = new Coordinates(45, 50);

        var spot =
                MapPlacement.findBarbarianSpotNear(
                        new Coordinates(50, 50),
                        MAP,
                        1.0,
                        occupiedBy(village),
                        FixedRandomSource.constant(0.5));

        assertThat(spot).isPresent();
        assertThat(spot.get().distanceTo(village)).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void barbarianRingsFollowTheSpread() {
        // near a point: 5.5 × 1.6 = 8.8 → 41.2 → 41; initial: 17 × 1.6 = 27.2 → 22.8 → 23
        assertThat(
                        MapPlacement.findBarbarianSpotNear(
                                new Coordinates(50, 50),
                                MAP,
                                1.6,
                                EMPTY,
                                FixedRandomSource.constant(0.5)))
                .contains(new Coordinates(41, 50));
        assertThat(
                        MapPlacement.findInitialBarbarianSpot(
                                MAP, 1.6, EMPTY, FixedRandomSource.constant(0.5)))
                .contains(new Coordinates(23, 50));
    }

    @Test
    void initialBarbariansUseRadiusFourToThirty() {
        // r = 4 + 26 × 0.5 = 17 → 50 − 17 = 33
        var spot =
                MapPlacement.findInitialBarbarianSpot(
                        MAP, 1.0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(33, 50));
    }

    // ---- map spread at the edges (1.6) ----

    /**
     * A crowded world on each spread's own map size: 5 keeps, 30 initial barbarians, then 100 sides
     * with 3 barbarians each (far more than a world of friends has). Every village finds a spot
     * inside the map, and the outermost side sits about as far towards the edge on every spread, so
     * no preset runs out of room before NORMAL does.
     */
    @Test
    void everySpreadFitsACrowdedWorldOnItsOwnMapSize() {
        Map<MapSpread, Double> outermost = new EnumMap<>(MapSpread.class);
        for (MapSpread spread : MapSpread.values()) {
            int size = spread.defaultMapSize();
            double f = spread.factor();
            RandomSource dice = seeded(7);
            Set<Coordinates> occupied = new HashSet<>();
            Coordinates centre = new Coordinates(size / 2, size / 2);
            for (Coordinates ideal : Keeps.ring(size, f, 5)) {
                place(occupied, size, Keeps.spotNear(ideal, size, occupied::contains));
            }
            for (int i = 0; i < 30; i++) {
                place(
                        occupied,
                        size,
                        MapPlacement.findInitialBarbarianSpot(size, f, occupied::contains, dice));
            }
            double furthest = 0;
            for (int side = 0; side < 100; side++) {
                Coordinates home =
                        place(
                                occupied,
                                size,
                                MapPlacement.findPlayerSpot(
                                        size, f, side, occupied::contains, dice));
                furthest = Math.max(furthest, centre.distanceTo(home));
                for (int b = 0; b < 3; b++) {
                    place(
                            occupied,
                            size,
                            MapPlacement.findBarbarianSpotNear(
                                    home, size, f, occupied::contains, dice));
                }
            }
            outermost.put(spread, furthest / (size / 2.0));
        }
        double normal = outermost.get(MapSpread.NORMAL);
        assertThat(normal).isLessThan(0.8);
        for (double share : outermost.values()) {
            assertThat(share).isCloseTo(normal, within(0.1));
        }
    }

    private static Coordinates place(
            Set<Coordinates> occupied, int size, Optional<Coordinates> found) {
        assertThat(found).isPresent();
        Coordinates spot = found.orElseThrow();
        assertThat(spot.x()).isBetween(0, size - 1);
        assertThat(spot.y()).isBetween(0, size - 1);
        assertThat(MapPlacement.spacingOk(spot, occupied::contains)).isTrue();
        occupied.add(spot);
        return spot;
    }

    /** Real-looking dice that give the same world on every run. */
    private static RandomSource seeded(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        return new RandomSource() {
            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public int nextInt(int minIncl, int maxIncl) {
                return random.nextInt(minIncl, maxIncl + 1);
            }
        };
    }
}
