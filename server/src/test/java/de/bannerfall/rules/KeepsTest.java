package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeepsTest {

    @Test
    void fiveKeepsGoOnARingOfThreeAroundTheCentreTheFirstNorth() {
        List<Coordinates> ring = Keeps.ring(100, 1.0, 5);
        assertThat(ring).hasSize(5).first().isEqualTo(new Coordinates(50, 47));
        for (Coordinates spot : ring) {
            double distance = Math.hypot(spot.x() - 50, spot.y() - 50);
            assertThat(distance).isBetween(2.5, 3.6);
        }
        assertThat(new HashSet<>(ring)).hasSize(5);
    }

    @Test
    void aSingleKeepSitsOnTheCentre() {
        assertThat(Keeps.ring(100, 1.0, 1)).containsExactly(new Coordinates(50, 50));
        assertThat(Keeps.ring(41, 1.0, 1)).containsExactly(new Coordinates(20, 20));
    }

    @Test
    void manyKeepsGetAWiderRing() {
        List<Coordinates> ring = Keeps.ring(100, 1.0, 12);
        for (Coordinates spot : ring) {
            assertThat(Math.hypot(spot.x() - 50, spot.y() - 50)).isGreaterThan(4.2);
        }
    }

    @Test
    void aWiderSpreadWidensTheRing() {
        // radius 3 × 1.6 = 4.8: the first keep straight north, 5 fields up after rounding
        List<Coordinates> ring = Keeps.ring(160, 1.6, 5);
        assertThat(ring.getFirst()).isEqualTo(new Coordinates(80, 75));
        for (Coordinates spot : ring) {
            assertThat(Math.hypot(spot.x() - 80, spot.y() - 80)).isBetween(4.2, 5.4);
        }
    }

    @Test
    void theKeepTakesItsIdealFieldWhenTheSpacingAllowsIt() {
        assertThat(Keeps.spotNear(new Coordinates(50, 47), 100, spot -> false))
                .contains(new Coordinates(50, 47));
    }

    @Test
    void anOccupiedFieldPushesTheKeepToTheNearestFreeOne() {
        Set<Coordinates> occupied = Set.of(new Coordinates(50, 47));
        Coordinates spot =
                Keeps.spotNear(new Coordinates(50, 47), 100, occupied::contains).orElseThrow();
        assertThat(Math.max(Math.abs(spot.x() - 50), Math.abs(spot.y() - 47))).isEqualTo(2);
        assertThat(MapPlacement.spacingOk(spot, occupied::contains)).isTrue();
    }

    @Test
    void theMapEdgeIsRespected() {
        Coordinates spot = Keeps.spotNear(new Coordinates(0, 0), 5, c -> false).orElseThrow();
        assertThat(spot).isEqualTo(new Coordinates(0, 0));
        assertThat(Keeps.spotNear(new Coordinates(0, 0), 3, c -> true)).isEmpty();
    }

    @Test
    void aKeepStartsStrong() {
        var levels = Keeps.startLevels();
        assertThat(levels.get(BuildingType.WALL)).isEqualTo(10);
        assertThat(levels.get(BuildingType.WAREHOUSE)).isEqualTo(10);
        assertThat(levels.get(BuildingType.WOODCUTTER)).isEqualTo(10);
        assertThat(levels.get(BuildingType.BARRACKS)).isZero();
        assertThat(Keeps.GARRISON.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(2100);
    }
}
