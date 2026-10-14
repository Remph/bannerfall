package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.common.Coordinates;
import java.util.Set;
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
        var spot = MapPlacement.findPlayerSpot(MAP, 0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(42, 50));
    }

    @Test
    void theAngleComesFromTheRandomSource() {
        // 1st draw: radius jitter 0 → r = 8; 2nd draw 0.25 → angle π/2 → straight "down".
        var spot =
                MapPlacement.findPlayerSpot(MAP, 0, EMPTY, FixedRandomSource.sequence(0.5, 0.25));

        assertThat(spot).contains(new Coordinates(50, 58));
    }

    @Test
    void theRadiusGrowsWithThePlayerVillageCount() {
        // r = 8 + 2.5 × sqrt(4) = 13
        var spot = MapPlacement.findPlayerSpot(MAP, 4, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(37, 50));
    }

    @Test
    void anOccupiedSpotMakesTheSearchWalkOutwardsInQuarterFieldSteps() {
        var occupied = occupiedBy(new Coordinates(42, 50));

        var spot = MapPlacement.findPlayerSpot(MAP, 0, occupied, FixedRandomSource.constant(0.5));

        // r = 8, 8.25 … 9.5 all land within one field of (42|50); r = 9.75 gives (40|50).
        assertThat(spot).contains(new Coordinates(40, 50));
    }

    @Test
    void aDiagonalNeighbourAlsoBlocksASpot() {
        var occupied = occupiedBy(new Coordinates(43, 51));

        var spot = MapPlacement.findPlayerSpot(MAP, 0, occupied, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(41, 50));
    }

    @Test
    void spotsOutsideTheMapAreSkippedAndATinyMapIsFull() {
        // Map 10×10: center 5, but r starts at 8 → every try is left of the map.
        var spot = MapPlacement.findPlayerSpot(10, 0, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).isEmpty();
    }

    @Test
    void aFullWorldGivesNoSpot() {
        assertThat(
                        MapPlacement.findPlayerSpot(
                                MAP, 0, spot -> true, FixedRandomSource.constant(0.5)))
                .isEmpty();
    }

    // ---- barbarians ----

    @Test
    void barbarianNearAPointUsesRadiusThreeToEight() {
        // r = 3 + 5 × 0.5 = 5.5 → 50 − 5.5 = 44.5 rounds half up to 45.
        var spot =
                MapPlacement.findBarbarianSpotNear(
                        new Coordinates(50, 50), MAP, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(45, 50));
    }

    @Test
    void barbarianNearAPointKeepsItsDistanceFromTheVillage() {
        var village = new Coordinates(45, 50);

        var spot =
                MapPlacement.findBarbarianSpotNear(
                        new Coordinates(50, 50),
                        MAP,
                        occupiedBy(village),
                        FixedRandomSource.constant(0.5));

        assertThat(spot).isPresent();
        assertThat(spot.get().distanceTo(village)).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void initialBarbariansUseRadiusFourToThirty() {
        // r = 4 + 26 × 0.5 = 17 → 50 − 17 = 33
        var spot =
                MapPlacement.findInitialBarbarianSpot(MAP, EMPTY, FixedRandomSource.constant(0.5));

        assertThat(spot).contains(new Coordinates(33, 50));
    }
}
