package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuildRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final GameData data = TestGameData.load();

    // ---- cost ----

    @ParameterizedTest(name = "{0} level {1} → {2}/{3}/{4}")
    @CsvSource({
        "HEADQUARTERS, 1, 90, 80, 70",
        "HEADQUARTERS, 10, 720, 640, 560",
        "WOODCUTTER, 5, 122, 146, 98",
        "IRON_MINE, 7, 289, 250, 270",
        "CLAY_PIT, 12, 901, 693, 554",
        "WALL, 3, 79, 159, 32",
        "FARM, 30, 90692, 80615, 60461",
        "ACADEMY, 1, 15000, 25000, 10000"
    })
    void costIsBaseTimesFactorToTheLevelMinusOne(
            BuildingType type, int level, int wood, int clay, int iron) {
        assertThat(BuildRules.cost(data.building(type), level))
                .isEqualTo(new ResourceAmounts(wood, clay, iron));
    }

    @Test
    void costNeedsATargetLevelOfAtLeastOne() {
        assertThatThrownBy(() -> BuildRules.cost(data.building(BuildingType.WALL), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- paid queue slots (M13c) ----

    @ParameterizedTest(name = "position {0} of 5 → +{1} %")
    @CsvSource({"1, 0", "5, 0", "6, 25", "7, 50", "8, 100", "9, 200", "10, 400"})
    void theSurchargeDoublesFromThe25PercentOfTheFirstPaidSlot(int position, int percent) {
        assertThat(BuildRules.slotSurchargePercent(position, 5)).isEqualTo(percent);
    }

    @ParameterizedTest(name = "woodcutter 5 at position {0} → {1}/{2}/{3}")
    @CsvSource({
        "5, 122, 146, 98", // the normal price
        "6, 153, 183, 123", // × 1.25, halves round up: 152.5, 182.5, 122.5
        "7, 183, 219, 147",
        "10, 610, 730, 490"
    })
    void theCostAtAPositionAddsItsSurchargeToEveryResource(
            int position, int wood, int clay, int iron) {
        assertThat(BuildRules.cost(data.building(BuildingType.WOODCUTTER), 5, position, 5))
                .isEqualTo(new ResourceAmounts(wood, clay, iron));
    }

    @Test
    void aQueuePositionStartsAtOne() {
        assertThatThrownBy(() -> BuildRules.slotSurchargePercent(0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- build time ----

    @Test
    void buildTimeReferenceValuesOfTheRulebook() {
        // Town Hall to level 2 with Town Hall 1 → 1011 s.
        assertThat(buildSeconds(BuildingType.HEADQUARTERS, 2, 1, 1.0)).isEqualTo(1011);
        // Woodcutter 30 with Town Hall 20 → 40 885 s (≈ 11.4 h).
        assertThat(buildSeconds(BuildingType.WOODCUTTER, 30, 20, 1.0)).isEqualTo(40_885);
    }

    @ParameterizedTest(name = "{0} level {1}, Town Hall {2}, speed {3} → {4} s")
    @CsvSource({
        "HEADQUARTERS, 1, 0, 1.0, 900",
        "HEADQUARTERS, 1, 0, 3.0, 300",
        "HEADQUARTERS, 30, 0, 1.0, 109350",
        "BARRACKS, 1, 3, 1.0, 1553",
        "STABLE, 2, 10, 3.0, 1443"
    })
    void buildTimeScalesWithLevelTownHallAndSpeed(
            BuildingType type, int level, int hqLevel, double speed, long expected) {
        assertThat(buildSeconds(type, level, hqLevel, speed)).isEqualTo(expected);
    }

    @Test
    void buildTimeIsAtLeastOneSecond() {
        assertThat(buildSeconds(BuildingType.RALLY_POINT, 1, 30, 1_000_000.0)).isEqualTo(1);
    }

    // ---- population ----

    @ParameterizedTest(name = "{0} to level {1} → +{2}")
    @CsvSource({
        "HEADQUARTERS, 1, 5",
        "HEADQUARTERS, 2, 1",
        "BARRACKS, 1, 7",
        "BARRACKS, 10, 4",
        "HEADQUARTERS, 30, 69",
        "FARM, 5, 0"
    })
    void popDeltaIsTheDifferenceOfTwoLevels(BuildingType type, int toLevel, int expected) {
        assertThat(BuildRules.popDelta(data.building(type), toLevel)).isEqualTo(expected);
    }

    // ---- requirements ----

    @Test
    void missingRequirementsListsWhatIsStillNeededInBuildingOrder() {
        var stable = data.building(BuildingType.STABLE); // Town Hall 10, Barracks 5, Smithy 5
        Map<BuildingType, Integer> levels =
                Map.of(BuildingType.HEADQUARTERS, 10, BuildingType.BARRACKS, 3);

        assertThat(BuildRules.missingRequirements(stable.requires(), levels))
                .containsExactly(
                        new Requirement(BuildingType.BARRACKS, 5),
                        new Requirement(BuildingType.SMITHY, 5));
    }

    @Test
    void missingRequirementsIsEmptyWhenEverythingIsMet() {
        var stable = data.building(BuildingType.STABLE);
        Map<BuildingType, Integer> levels =
                Map.of(
                        BuildingType.HEADQUARTERS, 12,
                        BuildingType.BARRACKS, 5,
                        BuildingType.SMITHY, 5);

        assertThat(BuildRules.missingRequirements(stable.requires(), levels)).isEmpty();
    }

    @Test
    void missingRequirementsCountsAMissingBuildingAsLevelZero() {
        var barracks = data.building(BuildingType.BARRACKS); // Town Hall 3

        assertThat(BuildRules.missingRequirements(barracks.requires(), Map.of()))
                .isEqualTo(List.of(new Requirement(BuildingType.HEADQUARTERS, 3)));
    }

    @Test
    void buildingsWithoutRequirementsAreNeverMissingAnything() {
        var farm = data.building(BuildingType.FARM);

        assertThat(BuildRules.missingRequirements(farm.requires(), Map.of())).isEmpty();
    }

    @Test
    void unitRequirementsUseTheSameCheck() {
        var heavy = data.unit(UnitType.HEAVY_CAVALRY); // Stable 10, Smithy 15

        assertThat(
                        BuildRules.missingRequirements(
                                heavy.requires(),
                                Map.of(BuildingType.STABLE, 10, BuildingType.SMITHY, 14)))
                .containsExactly(new Requirement(BuildingType.SMITHY, 15));
    }

    // ---- points ----

    @ParameterizedTest(name = "{0} level {1} → {2} points")
    @CsvSource({
        "HEADQUARTERS, 0, 0",
        "HEADQUARTERS, 1, 10",
        "HEADQUARTERS, 2, 22",
        "HEADQUARTERS, 5, 74",
        "HEADQUARTERS, 10, 260",
        "HEADQUARTERS, 30, 11817",
        "BARRACKS, 3, 58",
        "WALL, 20, 1495",
        "RALLY_POINT, 1, 0"
    })
    void buildingPointsAreTheSumOverAllLevels(BuildingType type, int level, int expected) {
        assertThat(BuildRules.points(data.building(type), level)).isEqualTo(expected);
    }

    @Test
    void villagePointsOfANewVillage() {
        // Town Hall 10 + rally point 0 + woodcutter 6 + clay pit 6 + iron mine 6 + farm 5 +
        // warehouse 6
        Map<BuildingType, Integer> start =
                Map.of(
                        BuildingType.HEADQUARTERS, 1,
                        BuildingType.RALLY_POINT, 1,
                        BuildingType.WOODCUTTER, 1,
                        BuildingType.CLAY_PIT, 1,
                        BuildingType.IRON_MINE, 1,
                        BuildingType.FARM, 1,
                        BuildingType.WAREHOUSE, 1);

        assertThat(BuildRules.villagePoints(data, start)).isEqualTo(39);
    }

    // ---- queued levels ----

    @Test
    void levelsWithQueuedAddsOneLevelPerQueuedOrder() {
        Map<BuildingType, Integer> current =
                Map.of(BuildingType.HEADQUARTERS, 2, BuildingType.WOODCUTTER, 1);

        Map<BuildingType, Integer> levels =
                BuildRules.levelsWithQueued(
                        current,
                        List.of(
                                BuildingType.HEADQUARTERS,
                                BuildingType.BARRACKS,
                                BuildingType.HEADQUARTERS));

        assertThat(levels)
                .containsEntry(BuildingType.HEADQUARTERS, 4)
                .containsEntry(BuildingType.BARRACKS, 1)
                .containsEntry(BuildingType.WOODCUTTER, 1)
                .containsEntry(BuildingType.WALL, 0)
                .hasSize(BuildingType.values().length);
    }

    @Test
    void levelsWithQueuedWithoutOrdersAreTheCurrentLevels() {
        Map<BuildingType, Integer> levels =
                BuildRules.levelsWithQueued(Map.of(BuildingType.FARM, 3), List.of());

        assertThat(levels).containsEntry(BuildingType.FARM, 3).containsEntry(BuildingType.WALL, 0);
    }

    // ---- re-timing after a cancel ----

    @Test
    void anOrderMovesUpToNowWhenNothingIsInFrontOfItAnymore() {
        Instant oldStart = NOW.plusSeconds(600);

        assertThat(BuildRules.startAfterCancel(oldStart, null, NOW)).isEqualTo(NOW);
    }

    @Test
    void anOrderFollowsTheOrderInFrontOfItThatStays() {
        Instant oldStart = NOW.plusSeconds(3600);
        Instant previousEnd = NOW.plusSeconds(1200);

        assertThat(BuildRules.startAfterCancel(oldStart, previousEnd, NOW)).isEqualTo(previousEnd);
    }

    @Test
    void anOrderNeverStartsBeforeNow() {
        Instant oldStart = NOW.plusSeconds(600);
        Instant previousEnd = NOW.minusSeconds(60);

        assertThat(BuildRules.startAfterCancel(oldStart, previousEnd, NOW)).isEqualTo(NOW);
    }

    @Test
    void anOrderIsNeverPushedBack() {
        // Its time had come already (the worker is behind): it keeps its start.
        Instant oldStart = NOW.minusSeconds(300);

        assertThat(BuildRules.startAfterCancel(oldStart, null, NOW)).isEqualTo(oldStart);
        assertThat(BuildRules.startAfterCancel(oldStart, NOW.minusSeconds(900), NOW))
                .isEqualTo(oldStart);
    }

    @Test
    void anOrderThatAlreadyFollowsItsPredecessorStaysWhereItIs() {
        Instant oldStart = NOW.plusSeconds(1200);

        assertThat(BuildRules.startAfterCancel(oldStart, oldStart, NOW)).isEqualTo(oldStart);
    }

    @Test
    void aMovedOrderStartsRightBehindTheOrderInFrontOfIt() {
        Instant previousEnd = NOW.plusSeconds(1200);

        assertThat(BuildRules.startAfter(previousEnd, NOW)).isEqualTo(previousEnd);
        // nothing in front of it, or the order in front is already over: now
        assertThat(BuildRules.startAfter(null, NOW)).isEqualTo(NOW);
        assertThat(BuildRules.startAfter(NOW.minusSeconds(60), NOW)).isEqualTo(NOW);
    }

    private long buildSeconds(BuildingType type, int level, int hqLevel, double speed) {
        return BuildRules.buildSeconds(data.building(type), level, hqLevel, speed);
    }
}
