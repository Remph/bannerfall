package de.bannerfall.movement;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.unit.UnitCounts;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The checks of 02-game-rules §6, without database. */
class CommandRulesTest {

    private static final Instant ARRIVAL = Instant.parse("2026-10-04T12:00:00Z");
    private static final int NOBLE_MAX_DISTANCE = 70;

    private final UnitCounts home =
            UnitCounts.of(
                    Map.of(
                            UnitType.SPEARMAN, 100,
                            UnitType.AXEMAN, 50,
                            UnitType.CATAPULT, 5,
                            UnitType.NOBLE, 1));

    @Test
    void aValidAttackOrSupportHasNoErrors() {
        assertThat(attack(units(UnitType.AXEMAN, 50), 10, null)).isEmpty();
        assertThat(support(units(UnitType.SPEARMAN, 100), false)).isEmpty();
    }

    @Test
    void aCommandNeedsAtLeastOneUnit() {
        assertThat(
                        CommandRules.check(
                                MovementKind.ATTACK,
                                new UnitCounts(),
                                home,
                                false,
                                false,
                                10,
                                null,
                                null,
                                NOBLE_MAX_DISTANCE))
                .containsExactly(ErrorCode.EMPTY_COMMAND);
    }

    @Test
    void onlyUnitsAtHomeCanBeSent() {
        assertThat(attack(units(UnitType.AXEMAN, 51), 10, null))
                .containsExactly(ErrorCode.NOT_ENOUGH_UNITS);
        assertThat(attack(units(UnitType.SCOUT, 1), 10, null))
                .containsExactly(ErrorCode.NOT_ENOUGH_UNITS);
    }

    @Test
    void theSourceVillageIsNoTarget() {
        assertThat(check(MovementKind.ATTACK, units(UnitType.AXEMAN, 1), true, true, 0, null))
                .containsExactly(ErrorCode.SAME_VILLAGE);
        assertThat(check(MovementKind.SUPPORT, units(UnitType.AXEMAN, 1), true, true, 0, null))
                .containsExactly(ErrorCode.SAME_VILLAGE);
    }

    @Test
    void ownVillagesCanBeSupportedButNotAttacked() {
        assertThat(check(MovementKind.ATTACK, units(UnitType.AXEMAN, 1), false, true, 5, null))
                .containsExactly(ErrorCode.OWN_VILLAGE);
        assertThat(support(units(UnitType.AXEMAN, 1), true)).isEmpty();
    }

    @Test
    void noblesOnlyGoInAttacks() {
        assertThat(support(units(UnitType.NOBLE, 1), false))
                .containsExactly(ErrorCode.NOBLE_ONLY_IN_ATTACK);
    }

    @Test
    void noblesGoAtMostTheMaximumDistance() {
        assertThat(attack(units(UnitType.NOBLE, 1), 70.0, null)).isEmpty();
        assertThat(attack(units(UnitType.NOBLE, 1), 70.01, null))
                .containsExactly(ErrorCode.NOBLE_TOO_FAR);
        // other units have no limit
        assertThat(attack(units(UnitType.AXEMAN, 1), 140, null)).isEmpty();
    }

    @Test
    void aTargetProtectedBeyondTheArrivalCanNotBeAttacked() {
        assertThat(attack(units(UnitType.AXEMAN, 1), 10, ARRIVAL.plusSeconds(1)))
                .containsExactly(ErrorCode.PROTECTED_TARGET);
        // protection that ends at the arrival (or before) doesn't count
        assertThat(attack(units(UnitType.AXEMAN, 1), 10, ARRIVAL)).isEmpty();
        assertThat(attack(units(UnitType.AXEMAN, 1), 10, ARRIVAL.minusSeconds(1))).isEmpty();
        // supports may go to protected players
        assertThat(
                        check(
                                MovementKind.SUPPORT,
                                units(UnitType.AXEMAN, 1),
                                false,
                                false,
                                10,
                                ARRIVAL.plusSeconds(3600)))
                .isEmpty();
    }

    @Test
    void allErrorsComeInTheOrderSendChecksThem() {
        UnitCounts tooMany =
                UnitCounts.of(Map.of(UnitType.NOBLE, 2, UnitType.SCOUT, 1, UnitType.AXEMAN, 1));

        assertThat(check(MovementKind.SUPPORT, tooMany, true, true, 0, null))
                .containsExactly(
                        ErrorCode.NOT_ENOUGH_UNITS,
                        ErrorCode.SAME_VILLAGE,
                        ErrorCode.NOBLE_ONLY_IN_ATTACK);
        assertThat(check(MovementKind.ATTACK, tooMany, false, false, 80, ARRIVAL.plusSeconds(1)))
                .containsExactly(
                        ErrorCode.NOT_ENOUGH_UNITS,
                        ErrorCode.NOBLE_TOO_FAR,
                        ErrorCode.PROTECTED_TARGET);
    }

    @Test
    void onlyAnAttackWithCatapultsHasACatapultTarget() {
        UnitCounts withCatapults = units(UnitType.CATAPULT, 5);
        UnitCounts without = units(UnitType.AXEMAN, 5);

        assertThat(CommandRules.catapultTarget(MovementKind.ATTACK, withCatapults, null))
                .isEqualTo(BuildingType.HEADQUARTERS);
        assertThat(
                        CommandRules.catapultTarget(
                                MovementKind.ATTACK, withCatapults, BuildingType.FARM))
                .isEqualTo(BuildingType.FARM);
        assertThat(CommandRules.catapultTarget(MovementKind.ATTACK, without, BuildingType.FARM))
                .isNull();
        assertThat(
                        CommandRules.catapultTarget(
                                MovementKind.SUPPORT, withCatapults, BuildingType.FARM))
                .isNull();
    }

    // ---- helpers ----

    private List<ErrorCode> attack(UnitCounts units, double distance, Instant protectedUntil) {
        return check(MovementKind.ATTACK, units, false, false, distance, protectedUntil);
    }

    private List<ErrorCode> support(UnitCounts units, boolean ownTarget) {
        return check(MovementKind.SUPPORT, units, false, ownTarget, 10, null);
    }

    private List<ErrorCode> check(
            MovementKind kind,
            UnitCounts units,
            boolean sameVillage,
            boolean ownTarget,
            double distance,
            Instant protectedUntil) {
        return CommandRules.check(
                kind,
                units,
                home,
                sameVillage,
                ownTarget,
                distance,
                protectedUntil,
                ARRIVAL,
                NOBLE_MAX_DISTANCE);
    }

    private static UnitCounts units(UnitType type, int count) {
        return UnitCounts.of(Map.of(type, count));
    }
}
