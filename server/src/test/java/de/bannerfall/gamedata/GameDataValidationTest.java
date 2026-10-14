package de.bannerfall.gamedata;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Tests for the validation in {@code GameData}: each "…isRejected" test shows one thing the
 * validation must catch. Messages must start with "Invalid game data:" and name the broken
 * building/unit; all problems are reported at once.
 */
class GameDataValidationTest {

    private final GameData valid = TestGameData.load();

    @Test
    void theRealGameDataIsAccepted() {
        assertThatCode(() -> new GameData(valid.buildings(), valid.units()))
                .doesNotThrowAnyException();
    }

    @Test
    void missingBuildingIsRejected() {
        assertInvalid(buildings -> buildings.remove(BuildingType.WALL), units -> {}, "WALL");
    }

    @Test
    void missingUnitIsRejected() {
        assertInvalid(buildings -> {}, units -> units.remove(UnitType.SPEARMAN), "SPEARMAN");
    }

    @Test
    void nullDefinitionIsRejected() {
        assertInvalid(buildings -> buildings.put(BuildingType.MARKET, null), units -> {}, "MARKET");
    }

    @Test
    void nonPositiveBuildingCostIsRejected() {
        assertInvalid(
                buildings -> {
                    var hq = valid.building(BuildingType.HEADQUARTERS);
                    buildings.put(
                            BuildingType.HEADQUARTERS,
                            new BuildingDef(
                                    hq.maxLevel(),
                                    hq.startLevel(),
                                    new ResourceAmounts(0, 80, 70),
                                    hq.costFactor(),
                                    hq.popBase(),
                                    hq.baseSeconds(),
                                    hq.pointsBase(),
                                    hq.requires()));
                },
                units -> {},
                "HEADQUARTERS");
    }

    @Test
    void nonPositiveUnitBuildTimeIsRejected() {
        assertInvalid(
                buildings -> {},
                units -> {
                    var axe = valid.unit(UnitType.AXEMAN);
                    units.put(
                            UnitType.AXEMAN,
                            new UnitDef(
                                    axe.building(),
                                    axe.requires(),
                                    axe.cost(),
                                    axe.pop(),
                                    axe.attack(),
                                    axe.defenseInfantry(),
                                    axe.defenseCavalry(),
                                    axe.attackType(),
                                    axe.minutesPerField(),
                                    axe.carry(),
                                    0,
                                    axe.levels()));
                },
                "AXEMAN");
    }

    @Test
    void startLevelAboveMaxLevelIsRejected() {
        assertInvalid(
                buildings -> {
                    var wall = valid.building(BuildingType.WALL);
                    buildings.put(
                            BuildingType.WALL,
                            new BuildingDef(
                                    wall.maxLevel(),
                                    wall.maxLevel() + 1,
                                    wall.baseCost(),
                                    wall.costFactor(),
                                    wall.popBase(),
                                    wall.baseSeconds(),
                                    wall.pointsBase(),
                                    wall.requires()));
                },
                units -> {},
                "WALL");
    }

    @Test
    void requirementAboveTheMaxLevelOfTheRequiredBuildingIsRejected() {
        // The Town Hall only goes to 30.
        assertInvalid(
                buildings ->
                        buildings.put(
                                BuildingType.BARRACKS,
                                withRequires(
                                        valid.building(BuildingType.BARRACKS),
                                        Map.of(BuildingType.HEADQUARTERS, 31))),
                units -> {},
                "BARRACKS");
    }

    @Test
    void requirementLevelZeroIsRejected() {
        assertInvalid(
                buildings ->
                        buildings.put(
                                BuildingType.BARRACKS,
                                withRequires(
                                        valid.building(BuildingType.BARRACKS),
                                        Map.of(BuildingType.HEADQUARTERS, 0))),
                units -> {},
                "BARRACKS");
    }

    @Test
    void buildingRequiringItselfIsRejected() {
        assertInvalid(
                buildings ->
                        buildings.put(
                                BuildingType.SMITHY,
                                withRequires(
                                        valid.building(BuildingType.SMITHY),
                                        Map.of(BuildingType.SMITHY, 1))),
                units -> {},
                "SMITHY");
    }

    @Test
    void unitThatDoesNotRequireItsBuildingIsRejected() {
        // without "BARRACKS: 1" a spearman could be recruited in a village without barracks
        assertInvalid(
                buildings -> {},
                units -> {
                    var spear = valid.unit(UnitType.SPEARMAN);
                    units.put(
                            UnitType.SPEARMAN,
                            new UnitDef(
                                    spear.building(),
                                    Map.of(),
                                    spear.cost(),
                                    spear.pop(),
                                    spear.attack(),
                                    spear.defenseInfantry(),
                                    spear.defenseCavalry(),
                                    spear.attackType(),
                                    spear.minutesPerField(),
                                    spear.carry(),
                                    spear.baseSeconds(),
                                    spear.levels()));
                },
                "SPEARMAN");
    }

    @Test
    void aLevelThatIsWorseThanTheLevelBeforeIsRejected() {
        assertInvalid(
                buildings -> {},
                units -> {
                    var axe = valid.unit(UnitType.AXEMAN);
                    var level2 = axe.levels().get(0);
                    var weaker =
                            new UnitLevelDef(
                                    axe.attack() - 1,
                                    level2.defenseInfantry(),
                                    level2.defenseCavalry(),
                                    level2.cost(),
                                    level2.baseSeconds(),
                                    level2.requires());
                    units.put(
                            UnitType.AXEMAN, withLevels(axe, List.of(weaker, axe.levels().get(1))));
                },
                "AXEMAN",
                "levels[2]");
    }

    @Test
    void aLevelWithoutCostOrTimeOrWithAnUnreachableRequirementIsRejected() {
        assertInvalid(
                buildings -> {},
                units -> {
                    var spear = valid.unit(UnitType.SPEARMAN);
                    var level2 = spear.levels().get(0);
                    var broken =
                            new UnitLevelDef(
                                    level2.attack(),
                                    level2.defenseInfantry(),
                                    level2.defenseCavalry(),
                                    new ResourceAmounts(0, 10, 10),
                                    0,
                                    Map.of(BuildingType.SMITHY, 99));
                    units.put(UnitType.SPEARMAN, withLevels(spear, List.of(broken)));
                },
                "SPEARMAN.levels[2]",
                "cost.wood",
                "baseSeconds",
                "SMITHY");
    }

    @Test
    void allProblemsAreReportedAtOnce() {
        assertInvalid(
                buildings -> buildings.remove(BuildingType.WALL),
                units -> units.remove(UnitType.NOBLE),
                "WALL",
                "NOBLE");
    }

    // ---- helpers ----

    /** Builds a GameData from the real one, changed by the two lambdas, and expects it to fail. */
    private void assertInvalid(
            Consumer<Map<BuildingType, BuildingDef>> changeBuildings,
            Consumer<Map<UnitType, UnitDef>> changeUnits,
            String... expectedInMessage) {
        var buildings = new EnumMap<>(valid.buildings());
        var units = new EnumMap<>(valid.units());
        changeBuildings.accept(buildings);
        changeUnits.accept(units);

        assertThatThrownBy(() -> new GameData(buildings, units))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Invalid game data:")
                .hasMessageContainingAll(expectedInMessage);
    }

    private static UnitDef withLevels(UnitDef def, List<UnitLevelDef> levels) {
        return new UnitDef(
                def.building(),
                def.requires(),
                def.cost(),
                def.pop(),
                def.attack(),
                def.defenseInfantry(),
                def.defenseCavalry(),
                def.attackType(),
                def.minutesPerField(),
                def.carry(),
                def.baseSeconds(),
                levels);
    }

    private static BuildingDef withRequires(BuildingDef def, Map<BuildingType, Integer> requires) {
        return new BuildingDef(
                def.maxLevel(),
                def.startLevel(),
                def.baseCost(),
                def.costFactor(),
                def.popBase(),
                def.baseSeconds(),
                def.pointsBase(),
                requires);
    }
}
