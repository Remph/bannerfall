package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BuildingType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SiegeTest {

    @Test
    void costPerLevelFollowsTheFormulas() {
        assertThat(Siege.ramsNeeded(1)).isEqualTo(3);
        assertThat(Siege.ramsNeeded(20)).isEqualTo(22);
        assertThat(Siege.catsNeeded(1)).isEqualTo(6);
        assertThat(Siege.catsNeeded(20)).isEqualTo(44);
    }

    @ParameterizedTest(name = "wall {0} with {1} rams → {2}")
    @CsvSource({
        "20, 50, 18", // rulebook reference: 22 + 21 used, 7 left over
        "20, 250, 0", // rulebook reference: exactly enough for all 20 levels
        "20, 249, 1",
        "20, 1000, 0", // more rams than needed
        "5, 10, 4", // rulebook reference
        "5, 6, 5", // level 5 needs 7
        "5, 7, 4",
        "5, 0, 5",
        "0, 100, 0", // no wall
        "1, 3, 0"
    })
    void wallAfterRamsMatchesTheRulebook(int level, int rams, int expected) {
        assertThat(Siege.wallAfterRams(level, rams)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "level {0} with {1} catapults, min {2} → {3}")
    @CsvSource({
        "20, 100, 1, 18", // rulebook reference: Town Hall 20, 44 + 42 used, 14 left over
        "20, 43, 1, 20", // level 20 needs 44
        "20, 44, 1, 19",
        "1, 100, 1, 1", // never below the minimum
        "2, 100, 1, 1",
        "1, 6, 0, 0", // e.g. a woodcutter can go down to 0
        "1, 5, 0, 1",
        "0, 100, 0, 0", // not built
        "3, 1000, 0, 0"
    })
    void levelAfterCatapultsMatchesTheRulebook(int level, int cats, int minLevel, int expected) {
        assertThat(Siege.levelAfterCatapults(level, cats, minLevel)).isEqualTo(expected);
    }

    @Test
    void theBuildingsAVillageNeedsKeepLevelOne() {
        var keepOne =
                EnumSet.of(
                        BuildingType.HEADQUARTERS,
                        BuildingType.FARM,
                        BuildingType.WAREHOUSE,
                        BuildingType.RALLY_POINT);
        for (BuildingType type : BuildingType.values()) {
            assertThat(Siege.catapultMinLevel(type))
                    .as(type.name())
                    .isEqualTo(keepOne.contains(type) ? 1 : 0);
        }
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> Siege.ramsNeeded(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Siege.catsNeeded(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Siege.wallAfterRams(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Siege.wallAfterRams(5, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Siege.levelAfterCatapults(5, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Siege.levelAfterCatapults(5, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
