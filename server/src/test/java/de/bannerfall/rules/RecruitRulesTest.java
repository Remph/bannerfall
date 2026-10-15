package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecruitRulesTest {

    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");

    private final GameData data = TestGameData.load();

    @ParameterizedTest(name = "{0} at building level {1}, speed {2} → {3} s per unit")
    @CsvSource({
        "SPEARMAN, 1, 1.0, 1000", // rulebook: barracks 1
        "SPEARMAN, 25, 1.0, 210", // rulebook: barracks 25
        "SWORDSMAN, 10, 1.0, 807",
        "CATAPULT, 15, 3.0, 965",
        "SCOUT, 20, 1000.0, 1" // never below one second
    })
    void secondsPerUnit(UnitType type, int buildingLevel, double speed, long expected) {
        assertThat(RecruitRules.secondsPerUnit(data.unit(type), buildingLevel, speed))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "dismissing {0} at building level {1}, speed {2} → {3} s per unit")
    @CsvSource({
        "SPEARMAN, 1, 1.0, 200", // a fifth of 1000
        "SPEARMAN, 25, 1.0, 42", // a fifth of 210
        "SPEARMAN, 0, 1.0, 200", // a razed building: the time at level 1
        "SCOUT, 1, 1.0, 180", // 900 / 5
        "SCOUT, 20, 1000.0, 1" // never below one second
    })
    void dismissSecondsPerUnitIsAFifthOfTheRecruitTime(
            UnitType type, int buildingLevel, double speed, long expected) {
        assertThat(RecruitRules.dismissSecondsPerUnit(data.unit(type), buildingLevel, speed))
                .isEqualTo(expected);
    }

    @Test
    void secondsPerUnitNeedsABuilding() {
        assertThatThrownBy(() -> RecruitRules.secondsPerUnit(data.unit(UnitType.SPEARMAN), 0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void costAndPopulationAreMultipliedByTheCount() {
        assertThat(RecruitRules.cost(data.unit(UnitType.SPEARMAN), 10))
                .isEqualTo(new ResourceAmounts(400, 350, 150));
        assertThat(RecruitRules.cost(data.unit(UnitType.NOBLE), 2))
                .isEqualTo(new ResourceAmounts(80_000, 100_000, 100_000));
        assertThat(RecruitRules.pop(data.unit(UnitType.SCOUT), 3)).isEqualTo(6);
    }

    @Test
    void anOrderNeedsAtLeastOneUnit() {
        assertThatThrownBy(() -> RecruitRules.cost(data.unit(UnitType.SPEARMAN), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecruitRules.pop(data.unit(UnitType.SPEARMAN), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCheapestResourceOrThePopulationLimitsHowManyArePaidFor() {
        var spearman = data.unit(UnitType.SPEARMAN); // 40 / 35 / 15, 1 pop
        // wood: 500 / 40 = 12, clay 14, iron 33, population 203
        assertThat(RecruitRules.maxAffordable(spearman, new ResourceAmounts(500, 500, 500), 203))
                .isEqualTo(12);
        // iron: 99 / 15 = 6 (rounded down)
        assertThat(RecruitRules.maxAffordable(spearman, new ResourceAmounts(5000, 5000, 99), 203))
                .isEqualTo(6);
        // population
        assertThat(RecruitRules.maxAffordable(spearman, new ResourceAmounts(5000, 5000, 5000), 7))
                .isEqualTo(7);
        // a scout needs 2 population: 7 / 2 = 3
        assertThat(
                        RecruitRules.maxAffordable(
                                data.unit(UnitType.SCOUT),
                                new ResourceAmounts(5000, 5000, 5000),
                                7))
                .isEqualTo(3);
    }

    @Test
    void nothingIsAffordableWithoutRoomOrResources() {
        var noble = data.unit(UnitType.NOBLE);
        assertThat(RecruitRules.maxAffordable(noble, new ResourceAmounts(500, 500, 500), 200))
                .isZero();
        // more population in use than the farm has (e.g. after a dev cheat)
        assertThat(
                        RecruitRules.maxAffordable(
                                data.unit(UnitType.SPEARMAN),
                                new ResourceAmounts(5000, 5000, 5000),
                                -5))
                .isZero();
    }

    @Test
    void neverMoreThanOneOrderMayHave() {
        var max = Integer.MAX_VALUE;
        assertThat(
                        RecruitRules.maxAffordable(
                                data.unit(UnitType.SPEARMAN),
                                new ResourceAmounts(max, max, max),
                                max))
                .isEqualTo(RecruitRules.MAX_COUNT);
    }

    @Test
    void theLargestOrderFitsIntoTheArithmetic() {
        // count × cost and count × pop must not overflow (ArithmeticException) for any unit
        for (UnitType type : UnitType.values()) {
            var unit = data.unit(type);
            assertThatCode(() -> RecruitRules.cost(unit, RecruitRules.MAX_COUNT))
                    .as(type.name())
                    .doesNotThrowAnyException();
            assertThatCode(() -> RecruitRules.pop(unit, RecruitRules.MAX_COUNT))
                    .as(type.name())
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest(name = "{0} s after start → {1} of 5 finished")
    @CsvSource({"-50, 0", "0, 0", "99, 0", "100, 1", "250, 2", "499, 4", "500, 5", "100000, 5"})
    void unitsFinishOneByOneAndNeverMoreThanTheOrder(long secondsAfterStart, int expected) {
        assertThat(RecruitRules.finishedBy(START, 100, 5, START.plusSeconds(secondsAfterStart)))
                .isEqualTo(expected);
    }
}
