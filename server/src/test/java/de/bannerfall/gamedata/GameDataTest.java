package de.bannerfall.gamedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The yml files contain exactly the numbers of 02-game-rules §4.1 and §5.1. */
class GameDataTest {

    private final GameData data = TestGameData.load();

    @Test
    void everyBuildingAndUnitHasADefinition() {
        assertThat(data.buildings()).containsOnlyKeys(BuildingType.values());
        assertThat(data.units()).containsOnlyKeys(UnitType.values());
    }

    // type | max | start | wood | clay | iron | factor | pop | seconds | points | requires
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    HEADQUARTERS | 30 | 1 |    80 |    85 |    75 | 1.26  |  5 |   900 |  10 | -
                    BARRACKS     | 25 | 0 |   180 |   180 |   100 | 1.26  |  7 |  1800 |  16 | HEADQUARTERS:3
                    STABLE       | 20 | 0 |   270 |   240 |   260 | 1.26  |  8 |  6000 |  20 | HEADQUARTERS:10;BARRACKS:5;SMITHY:5
                    WORKSHOP     | 15 | 0 |   300 |   240 |   260 | 1.26  |  8 |  6000 |  24 | HEADQUARTERS:10;SMITHY:10
                    ACADEMY      |  1 | 0 | 15000 | 25000 | 10000 | 1.0   | 80 | 64800 | 512 | HEADQUARTERS:20;SMITHY:20;MARKET:10
                    SMITHY       | 20 | 0 |   220 |   180 |   240 | 1.26  | 20 |  6000 |  19 | HEADQUARTERS:5;BARRACKS:1
                    RALLY_POINT  |  1 | 1 |    10 |    40 |    30 | 1.0   |  0 |   600 |   0 | -
                    MARKET       | 25 | 0 |   100 |   100 |   100 | 1.26  | 20 |  2700 |  10 | HEADQUARTERS:3;WAREHOUSE:2
                    WOODCUTTER   | 30 | 1 |    50 |    60 |    40 | 1.25  |  5 |   900 |   6 | -
                    CLAY_PIT     | 30 | 1 |    55 |    55 |    45 | 1.27  | 10 |   900 |   6 | -
                    IRON_MINE    | 30 | 1 |    65 |    70 |    75 | 1.252 | 10 |  1080 |   6 | -
                    FARM         | 30 | 1 |    40 |    40 |    35 | 1.30  |  0 |  1200 |   5 | -
                    WAREHOUSE    | 30 | 1 |    50 |    55 |    45 | 1.265 |  0 |  1020 |   6 | -
                    HIDING_PLACE | 10 | 0 |    50 |    60 |    50 | 1.25  |  2 |  1800 |   5 | -
                    WALL         | 20 | 0 |    50 |   100 |    20 | 1.26  |  5 |  3600 |   8 | BARRACKS:1
                    BANNER_HALL  |  3 | 0 |   300 |   350 |   250 | 1.5   |  8 |  7200 |  20 | HEADQUARTERS:8;BARRACKS:5
                    """)
    void buildingsMatchTheRulebook(
            BuildingType type,
            int maxLevel,
            int startLevel,
            int wood,
            int clay,
            int iron,
            double costFactor,
            int popBase,
            int baseSeconds,
            int pointsBase,
            String requires) {
        var def = data.building(type);

        assertThat(def.maxLevel()).isEqualTo(maxLevel);
        assertThat(def.startLevel()).isEqualTo(startLevel);
        assertThat(def.baseCost()).isEqualTo(new ResourceAmounts(wood, clay, iron));
        assertThat(def.costFactor()).isEqualTo(costFactor);
        assertThat(def.popBase()).isEqualTo(popBase);
        assertThat(def.baseSeconds()).isEqualTo(baseSeconds);
        assertThat(def.pointsBase()).isEqualTo(pointsBase);
        assertThat(def.requires()).isEqualTo(parseRequires(requires));
    }

    // type | building | requires | wood | clay | iron | pop | att | defInf | defCav | attackType |
    // speed | carry | seconds
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    SPEARMAN      | BARRACKS | BARRACKS:1           |    40 |    35 |    15 |   1 |  10 |  15 |  40 | INFANTRY | 18 | 25 |  1000
                    SWORDSMAN     | BARRACKS | BARRACKS:1;SMITHY:1  |    30 |    30 |    70 |   1 |  20 |  50 |  20 | INFANTRY | 22 | 15 |  1450
                    AXEMAN        | BARRACKS | BARRACKS:2;SMITHY:2  |    50 |    35 |    45 |   1 |  40 |  10 |   5 | INFANTRY | 18 | 10 |  1300
                    SCOUT         | STABLE   | STABLE:1             |    50 |    50 |    20 |   2 |   0 |   2 |   1 | INFANTRY |  9 |  0 |   900
                    LIGHT_CAVALRY | STABLE   | STABLE:3             |   125 |   100 |   250 |   4 | 120 |  30 |  40 | CAVALRY  | 10 | 80 |  1800
                    HEAVY_CAVALRY | STABLE   | STABLE:10;SMITHY:15  |   200 |   150 |   600 |   6 | 150 | 200 |  80 | CAVALRY  | 11 | 50 |  3600
                    RAM           | WORKSHOP | WORKSHOP:1           |   300 |   200 |   200 |   5 |   2 |  20 |  50 | INFANTRY | 30 |  0 |  4800
                    CATAPULT      | WORKSHOP | WORKSHOP:2;SMITHY:12 |   320 |   400 |   100 |   8 | 100 | 100 |  50 | INFANTRY | 30 |  0 |  7200
                    NOBLE         | ACADEMY  | ACADEMY:1            | 40000 | 50000 | 50000 | 100 |  30 | 100 |  50 | INFANTRY | 35 |  0 | 10800
                    PALADIN       | BARRACKS | BARRACKS:5           |  1000 |  1000 |  1000 |  10 | 120 | 150 | 150 | INFANTRY | 14 |  0 | 21600
                    BANNER_THIEF  | BANNER_HALL | BANNER_HALL:1     |   100 |   120 |   150 |   2 |   0 |   3 |   3 | INFANTRY | 18 |  0 |  1500
                    """)
    void unitsMatchTheRulebook(
            UnitType type,
            BuildingType building,
            String requires,
            int wood,
            int clay,
            int iron,
            int pop,
            int attack,
            int defenseInfantry,
            int defenseCavalry,
            AttackType attackType,
            double minutesPerField,
            int carry,
            int baseSeconds) {
        var def = data.unit(type);

        assertThat(def.building()).isEqualTo(building);
        assertThat(def.requires()).isEqualTo(parseRequires(requires));
        assertThat(def.cost()).isEqualTo(new ResourceAmounts(wood, clay, iron));
        assertThat(def.pop()).isEqualTo(pop);
        assertThat(def.attack()).isEqualTo(attack);
        assertThat(def.defenseInfantry()).isEqualTo(defenseInfantry);
        assertThat(def.defenseCavalry()).isEqualTo(defenseCavalry);
        assertThat(def.attackType()).isEqualTo(attackType);
        assertThat(def.minutesPerField()).isEqualTo(minutesPerField);
        assertThat(def.carry()).isEqualTo(carry);
        assertThat(def.baseSeconds()).isEqualTo(baseSeconds);
    }

    // type | att 2 | defInf 2 | defCav 2 | att 3 | defInf 3 | defCav 3 (02-game-rules §5.4)
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    SPEARMAN      |  11 |  17 |  44 |  12 |  18 |  48
                    SWORDSMAN     |  22 |  55 |  22 |  24 |  60 |  24
                    AXEMAN        |  44 |  11 |   6 |  48 |  12 |   7
                    LIGHT_CAVALRY | 132 |  33 |  44 | 144 |  36 |  48
                    HEAVY_CAVALRY | 165 | 220 |  88 | 180 | 240 |  96
                    """)
    void unitLevelsMatchTheRulebook(
            UnitType type, int att2, int inf2, int cav2, int att3, int inf3, int cav3) {
        var def = data.unit(type);

        assertThat(def.maxLevel()).isEqualTo(3);
        assertThat(def.stats(1))
                .isEqualTo(
                        new UnitStats(def.attack(), def.defenseInfantry(), def.defenseCavalry()));
        assertThat(def.stats(2)).isEqualTo(new UnitStats(att2, inf2, cav2));
        assertThat(def.stats(3)).isEqualTo(new UnitStats(att3, inf3, cav3));
        // level 2 costs 20 times and level 3 50 times the recruit cost, 12 h and 30 h at Smithy 1
        assertThat(def.levelDef(2).cost()).isEqualTo(def.cost().times(20));
        assertThat(def.levelDef(3).cost()).isEqualTo(def.cost().times(50));
        assertThat(def.levelDef(2).baseSeconds()).isEqualTo(12 * 3600);
        assertThat(def.levelDef(3).baseSeconds()).isEqualTo(30 * 3600);
        assertThat(def.levelDef(2).requires()).isEqualTo(Map.of(BuildingType.SMITHY, 10));
        assertThat(def.levelDef(3).requires()).isEqualTo(Map.of(BuildingType.SMITHY, 15));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"RAM", "CATAPULT", "NOBLE", "PALADIN", "BANNER_THIEF"})
    void siegeNobleAndPaladinHaveNoLevels(UnitType type) {
        var def = data.unit(type);

        assertThat(def.levels()).isEmpty();
        assertThat(def.maxLevel()).isEqualTo(1);
        assertThat(def.stats(1)).isEqualTo(def.stats(3));
    }

    @Test
    void theScoutHasOneLevelThatMakesItAnUpgradedScout() {
        var def = data.unit(UnitType.SCOUT);

        assertThat(def.maxLevel()).isEqualTo(2);
        assertThat(def.stats(2)).isEqualTo(new UnitStats(0, 3, 2));
        assertThat(def.levelDef(2).cost()).isEqualTo(def.cost().times(20));
        assertThat(def.levelDef(2).requires()).isEqualTo(Map.of(BuildingType.SMITHY, 10));
    }

    @Test
    void aLevelBelowOneHasNoStats() {
        assertThatThrownBy(() -> data.unit(UnitType.SPEARMAN).stats(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** "BARRACKS:5;SMITHY:5" → map; "-" → empty map. */
    private static Map<BuildingType, Integer> parseRequires(String text) {
        var result = new EnumMap<BuildingType, Integer>(BuildingType.class);
        if (!text.equals("-")) {
            for (String part : text.split(";")) {
                String[] pair = part.split(":");
                result.put(BuildingType.valueOf(pair[0]), Integer.parseInt(pair[1]));
            }
        }
        return result;
    }
}
