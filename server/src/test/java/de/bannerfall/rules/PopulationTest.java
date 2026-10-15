package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PopulationTest {

    private final GameData data = TestGameData.load();

    /** The whole table of 02-game-rules §3 (1.2.3). */
    @ParameterizedTest(name = "farm {0} → {1}")
    @CsvSource({
        "1, 240",
        "2, 281",
        "3, 330",
        "4, 386",
        "5, 453",
        "6, 547",
        "7, 659",
        "8, 795",
        "9, 956",
        "10, 1151",
        "11, 1350",
        "12, 1581",
        "13, 1854",
        "14, 2172",
        "15, 2546",
        "16, 2940",
        "17, 3393",
        "18, 3915",
        "19, 4517",
        "20, 5211",
        "21, 6009",
        "22, 6927",
        "23, 8118",
        "24, 9514",
        "25, 11151",
        "26, 13069",
        "27, 15317",
        "28, 17951",
        "29, 21039",
        "30, 24657"
    })
    void farmCapacityMatchesTheRulebook(int level, int expected) {
        assertThat(Population.farmCapacity(level)).isEqualTo(expected);
    }

    @Test
    void farmCapacityNeverDropsBelowTheOldCurve() {
        // Running worlds take the new curve with an update: no village may end up over its limit.
        for (int level = 1; level <= 30; level++) {
            long old = Math.round(240 * Math.pow(1.172, level - 1));
            assertThat(Population.farmCapacity(level))
                    .as("farm %d", level)
                    .isGreaterThanOrEqualTo((int) old);
        }
    }

    @Test
    void everyFarmLevelAddsMoreThanTheOneBefore() {
        int lastGain = 0;
        for (int level = 2; level <= 30; level++) {
            int gain = Population.farmCapacity(level) - Population.farmCapacity(level - 1);
            assertThat(gain).as("farm %d", level).isGreaterThan(lastGain);
            lastGain = gain;
        }
    }

    @Test
    void farmNeedsLevelOne() {
        assertThatThrownBy(() -> Population.farmCapacity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "town hall {0} → {1}")
    @CsvSource({"0, 0", "1, 5", "2, 6", "5, 9", "10, 21", "30, 475"})
    void buildingPopFollowsTheFormula(int level, int expected) {
        assertThat(Population.buildingPop(data.building(BuildingType.HEADQUARTERS), level))
                .isEqualTo(expected);
    }

    @Test
    void buildingsWithPopBaseZeroNeverUsePopulation() {
        assertThat(Population.buildingPop(data.building(BuildingType.FARM), 30)).isZero();
        assertThat(Population.buildingPop(data.building(BuildingType.WAREHOUSE), 30)).isZero();
    }

    @Test
    void usedByBuildingsOfANewVillage() {
        // Town Hall 1 (5) + woodcutter 1 (5) + clay pit 1 (10) + iron mine 1 (10);
        // farm/warehouse/rally 0.
        Map<BuildingType, Integer> start =
                Map.of(
                        BuildingType.HEADQUARTERS, 1,
                        BuildingType.RALLY_POINT, 1,
                        BuildingType.WOODCUTTER, 1,
                        BuildingType.CLAY_PIT, 1,
                        BuildingType.IRON_MINE, 1,
                        BuildingType.FARM, 1,
                        BuildingType.WAREHOUSE, 1);

        assertThat(Population.usedByBuildings(data, start)).isEqualTo(30);
        assertThat(Population.usedByBuildings(data, Map.of())).isZero();
    }

    @Test
    void usedByUnitsMultipliesPopPerUnit() {
        var units = Map.of(UnitType.SPEARMAN, 10, UnitType.SCOUT, 2, UnitType.NOBLE, 1);

        assertThat(Population.usedByUnits(data, units)).isEqualTo(10 + 2 * 2 + 100);
    }

    @Test
    void freePopulationIsCapacityMinusUsed() {
        assertThat(Population.free(1, 30)).isEqualTo(210);
        assertThat(Population.free(1, 240)).isZero();
        assertThat(Population.free(1, 250)).isEqualTo(-10); // callers decide what negative means
    }
}
