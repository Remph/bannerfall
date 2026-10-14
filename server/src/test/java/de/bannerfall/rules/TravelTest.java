package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.TestGameData;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TravelTest {

    private final GameData data = TestGameData.load();
    private final WorldSettings world = WorldSettings.defaults();

    @Test
    void hasteShortensATravelTimeButNeverBelowOneSecond() {
        // 02 §21.2: 20 fields at 18 min/field = 6 h; Haste III takes 54 minutes off
        assertThat(Travel.withHaste(21_600, 0.15)).isEqualTo(21_600 - 54 * 60);
        assertThat(Travel.withHaste(21_600, 0)).isEqualTo(21_600);
        assertThat(Travel.withHaste(1, 0.15)).isEqualTo(1);
    }

    @Test
    void theBannerThiefWalksLikeInfantry() {
        assertThat(Travel.armyMinutesPerField(data, Map.of(UnitType.BANNER_THIEF, 5)))
                .isEqualTo(18.0);
        // with light cavalry the thieves slow the raid down to their pace
        assertThat(
                        Travel.armyMinutesPerField(
                                data, Map.of(UnitType.LIGHT_CAVALRY, 50, UnitType.BANNER_THIEF, 1)))
                .isEqualTo(18.0);
    }

    @Test
    void distanceIsEuclidean() {
        assertThat(Travel.distance(new Coordinates(0, 0), new Coordinates(3, 4)))
                .isCloseTo(5.0, within(1e-9));
        assertThat(Travel.distance(new Coordinates(10, 10), new Coordinates(10, 10))).isZero();
        assertThat(Travel.distance(new Coordinates(0, 0), new Coordinates(1, 1)))
                .isCloseTo(Math.sqrt(2), within(1e-9));
    }

    @ParameterizedTest(name = "{0} fields at {1} min/field, speed {2}, unit speed {3} → {4} s")
    @CsvSource({
        "5.0, 18, 1.0, 1.0, 5400",
        "1.4142135623730951, 18, 1.0, 1.0, 1527", // 1527.35 rounds down
        "1.4142135623730951, 18, 3.0, 1.0, 509",
        "10.0, 30, 3.0, 2.0, 3000", // speed × unitSpeed both divide
        "5.0, 10, 1.0, 1.0, 3000",
        "0.0, 18, 1.0, 1.0, 1" // never below one second
    })
    void travelSecondsFollowTheFormula(
            double distance,
            double minutesPerField,
            double speed,
            double unitSpeed,
            long expected) {
        var settings = WorldSettings.defaults().withSpeed(speed).withUnitSpeed(unitSpeed);

        assertThat(Travel.travelSeconds(distance, minutesPerField, settings)).isEqualTo(expected);
    }

    @Test
    void aPaladinMakesASupportWalkAtHisSpeed() {
        // M31: spearmen (18) and rams (30) in a support with a paladin (14) walk at 14 …
        assertThat(
                        Travel.supportMinutesPerField(
                                data,
                                Map.of(
                                        UnitType.SPEARMAN, 10,
                                        UnitType.RAM, 1,
                                        UnitType.PALADIN, 1)))
                .isEqualTo(14.0);
        // … and so do the quicker ones: a command has one pace, the paladin's
        assertThat(
                        Travel.supportMinutesPerField(
                                data, Map.of(UnitType.LIGHT_CAVALRY, 5, UnitType.PALADIN, 1)))
                .isEqualTo(14.0);
        // without one the slowest unit decides, as in every other command
        assertThat(Travel.supportMinutesPerField(data, Map.of(UnitType.SPEARMAN, 10)))
                .isEqualTo(18.0);
        assertThat(
                        Travel.supportMinutesPerField(
                                data, Map.of(UnitType.SPEARMAN, 10, UnitType.PALADIN, 0)))
                .isEqualTo(18.0);
        // an attack has no such help: the paladin joins the slowest unit
        assertThat(
                        Travel.armyMinutesPerField(
                                data, Map.of(UnitType.SPEARMAN, 10, UnitType.PALADIN, 1)))
                .isEqualTo(18.0);
        assertThat(
                        Travel.armyMinutesPerField(
                                data, Map.of(UnitType.LIGHT_CAVALRY, 10, UnitType.PALADIN, 1)))
                .isEqualTo(14.0);
    }

    @Test
    void thePaladinIsFasterThanSpearAndAxeAndSlowerThanCavalry() {
        double paladin = data.unit(UnitType.PALADIN).minutesPerField();

        assertThat(paladin).isLessThan(data.unit(UnitType.SPEARMAN).minutesPerField());
        assertThat(paladin).isLessThan(data.unit(UnitType.AXEMAN).minutesPerField());
        assertThat(paladin).isGreaterThan(data.unit(UnitType.LIGHT_CAVALRY).minutesPerField());
        assertThat(paladin).isGreaterThan(data.unit(UnitType.HEAVY_CAVALRY).minutesPerField());
    }

    @Test
    void aSupportTravelsAtTheSupportSpeed() {
        var from = new Coordinates(0, 0);
        var to = new Coordinates(3, 4);

        // 5 fields × 14 min = 4200 s with a paladin; 5 × 18 min = 5400 s without
        assertThat(
                        Travel.supportTravelSeconds(
                                data,
                                world,
                                from,
                                to,
                                Map.of(UnitType.SPEARMAN, 5, UnitType.PALADIN, 1)))
                .isEqualTo(4200);
        assertThat(Travel.supportTravelSeconds(data, world, from, to, Map.of(UnitType.SPEARMAN, 5)))
                .isEqualTo(5400);
    }

    @Test
    void theSlowestUnitDecidesTheArmySpeed() {
        assertThat(Travel.armyMinutesPerField(data, Map.of(UnitType.SPEARMAN, 10, UnitType.RAM, 1)))
                .isEqualTo(30.0);
        assertThat(
                        Travel.armyMinutesPerField(
                                data, Map.of(UnitType.SCOUT, 5, UnitType.LIGHT_CAVALRY, 2)))
                .isEqualTo(10.0);
    }

    @Test
    void unitTypesWithCountZeroAreNotInTheArmy() {
        assertThat(Travel.armyMinutesPerField(data, Map.of(UnitType.RAM, 0, UnitType.SCOUT, 3)))
                .isEqualTo(9.0);
    }

    @Test
    void anArmyNeedsAtLeastOneUnit() {
        assertThatThrownBy(() -> Travel.armyMinutesPerField(data, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Travel.armyMinutesPerField(data, Map.of(UnitType.RAM, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWholeTripWithTheDefaultWorld() {
        // Spearmen from (50|50) to (53|54): 5 fields at 18 min/field.
        double distance = Travel.distance(new Coordinates(50, 50), new Coordinates(53, 54));
        double speed = Travel.armyMinutesPerField(data, Map.of(UnitType.SPEARMAN, 20));

        assertThat(Travel.travelSeconds(distance, speed, world)).isEqualTo(5400);
    }

    @Test
    void aWholeTripInOneCallUsesTheSlowestUnit() {
        // 5 fields, the rams (30 min/field) are slower than the spearmen (18 min/field).
        long seconds =
                Travel.travelSeconds(
                        data,
                        world,
                        new Coordinates(50, 50),
                        new Coordinates(53, 54),
                        Map.of(UnitType.SPEARMAN, 20, UnitType.RAM, 2));

        assertThat(seconds).isEqualTo(9000);
    }
}
