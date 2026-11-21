package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rules of help requests from allied lords (2.1 S4, 12-solo-and-allies §9). */
class NpcHelpRulesTest {

    private static final GameData DATA = TestGameData.load();
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    static final NpcHelpSettings SETTINGS =
            new NpcHelpSettings(3, 12, 12, 15, -5, -10, 24, 0.5, 100, 5000, 25, 35);

    @Test
    void aLordAsksOnlyInASoloWorldAsAnAllyWithoutAnOpenRequestAndUnderTheDailyCap() {
        assertThat(NpcHelpRules.mayAsk(true, true, false, 0, SETTINGS)).isTrue();
        assertThat(NpcHelpRules.mayAsk(true, true, false, 2, SETTINGS)).isTrue();
        assertThat(NpcHelpRules.mayAsk(true, true, false, 3, SETTINGS)).isFalse(); // three a day
        assertThat(NpcHelpRules.mayAsk(true, true, true, 0, SETTINGS)).isFalse(); // one open
        assertThat(NpcHelpRules.mayAsk(true, false, false, 0, SETTINGS)).isFalse(); // no ally
        assertThat(NpcHelpRules.mayAsk(false, true, false, 0, SETTINGS)).isFalse(); // not solo
    }

    @Test
    void aSupportLandsInTimeWhenItArrivesByTheAttack() {
        Instant lands = NOW.plusSeconds(3600);
        assertThat(NpcHelpRules.reachable(NOW, 3599, lands)).isTrue();
        assertThat(NpcHelpRules.reachable(NOW, 3600, lands)).isTrue(); // with the attack
        assertThat(NpcHelpRules.reachable(NOW, 3601, lands)).isFalse();
    }

    @Test
    void theDayTheShortageWaitAndTheWindowShrinkWithTheSpeed() {
        assertThat(NpcHelpRules.day(1)).isEqualTo(Duration.ofDays(1));
        assertThat(NpcHelpRules.day(3)).isEqualTo(Duration.ofHours(8));
        assertThat(NpcHelpRules.shortageWait(1, SETTINGS)).isEqualTo(Duration.ofHours(12));
        assertThat(NpcHelpRules.shortageWait(4, SETTINGS)).isEqualTo(Duration.ofHours(3));
        assertThat(NpcHelpRules.resourceWindow(2, SETTINGS)).isEqualTo(Duration.ofHours(6));
        assertThat(NpcHelpRules.thanksWindow(3, SETTINGS)).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void aShortageCountsOnceItHeldTwelveHours() {
        assertThat(NpcHelpRules.shortageHeld(NOW.minus(Duration.ofHours(12)), NOW, 1, SETTINGS))
                .isTrue();
        assertThat(
                        NpcHelpRules.shortageHeld(
                                NOW.minus(Duration.ofHours(12)).plusSeconds(1), NOW, 1, SETTINGS))
                .isFalse();
        assertThat(NpcHelpRules.shortageHeld(NOW.minus(Duration.ofHours(4)), NOW, 3, SETTINGS))
                .isTrue();
        assertThat(NpcHelpRules.shortageHeld(null, NOW, 1, SETTINGS)).isFalse();
    }

    @Test
    void theShortfallIsWhatIsMissingRoundedUpToAHundred() {
        assertThat(
                        NpcHelpRules.shortfall(
                                new ResourceAmounts(5000, 6000, 1000),
                                new ResourceAmounts(5200, 2001, 999)))
                .isEqualTo(new ResourceAmounts(0, 4000, 100));
        assertThat(
                        NpcHelpRules.shortfall(
                                new ResourceAmounts(10, 10, 10), new ResourceAmounts(10, 10, 10)))
                .isEqualTo(ResourceAmounts.ZERO);
    }

    @Test
    void aLordCannotHoldWhatItsEstimateLosesAndAskForTheDefendersThatTurnIt() {
        Map<UnitType, Integer> axes = Map.of(UnitType.AXEMAN, 2000);
        Map<UnitType, Integer> few = Map.of(UnitType.SPEARMAN, 10);
        assertThat(NpcHelpRules.cannotHold(DATA, axes, few, 0, 1.6)).isTrue();
        assertThat(
                        NpcHelpRules.cannotHold(
                                DATA,
                                axes,
                                Map.of(UnitType.SPEARMAN, 3000, UnitType.SWORDSMAN, 3000),
                                0,
                                1.6))
                .isFalse();

        int wanted = NpcHelpRules.defendersNeeded(DATA, axes, few, 0, 1.6, SETTINGS);
        assertThat(wanted % NpcHelpRules.STEP).isZero();
        assertThat(wanted).isBetween(SETTINGS.minDefenders(), SETTINGS.maxDefenders());
        Map<UnitType, Integer> with =
                Map.of(UnitType.SPEARMAN, 10 + wanted / 2, UnitType.SWORDSMAN, wanted - wanted / 2);
        assertThat(NpcHelpRules.cannotHold(DATA, axes, with, 0, 1.6)).isFalse();
        Map<UnitType, Integer> less =
                Map.of(
                        UnitType.SPEARMAN,
                        10 + (wanted - NpcHelpRules.STEP) / 2,
                        UnitType.SWORDSMAN,
                        wanted - NpcHelpRules.STEP - (wanted - NpcHelpRules.STEP) / 2);
        assertThat(NpcHelpRules.cannotHold(DATA, axes, less, 0, 1.6)).isTrue();

        // a careful lord (margin 2) asks for more than a bold one (1.3)
        assertThat(NpcHelpRules.defendersNeeded(DATA, axes, few, 0, 2.0, SETTINGS))
                .isGreaterThan(NpcHelpRules.defendersNeeded(DATA, axes, few, 0, 1.3, SETTINGS));
        // a wall helps
        assertThat(NpcHelpRules.defendersNeeded(DATA, axes, few, 10, 1.6, SETTINGS))
                .isLessThan(wanted);
        // never more than the most
        assertThat(
                        NpcHelpRules.defendersNeeded(
                                DATA, Map.of(UnitType.AXEMAN, 1_000_000), few, 0, 1.6, SETTINGS))
                .isEqualTo(SETTINGS.maxDefenders());
    }

    @Test
    void anArmyWithCatapultsIsEstimatedWithThemAimedAtTheWall() {
        Map<UnitType, Integer> siege =
                Map.of(UnitType.AXEMAN, 2000, UnitType.RAM, 20, UnitType.CATAPULT, 10);
        Map<UnitType, Integer> few = Map.of(UnitType.SPEARMAN, 10);
        // the simulation found this: an attacker with catapults made the estimate throw
        assertThat(NpcHelpRules.cannotHold(DATA, siege, few, 5, 1.6)).isTrue();
        assertThat(NpcHelpRules.defendersNeeded(DATA, siege, few, 5, 1.6, SETTINGS))
                .isBetween(SETTINGS.minDefenders(), SETTINGS.maxDefenders());
    }

    @Test
    void anUnseenAttackerIsFearedWithMorePointsAndAsksForAsManyAgain() {
        assertThat(NpcHelpRules.fearsUnseen(501, 500)).isTrue();
        assertThat(NpcHelpRules.fearsUnseen(500, 500)).isFalse();
        assertThat(NpcHelpRules.unseenDefenders(0, SETTINGS)).isEqualTo(100);
        assertThat(NpcHelpRules.unseenDefenders(301, SETTINGS)).isEqualTo(350);
        assertThat(NpcHelpRules.unseenDefenders(99_999, SETTINGS)).isEqualTo(5000);
    }

    @Test
    void deliveriesCoverARequestOnceEveryResourceHasArrived() {
        ResourceAmounts asked = new ResourceAmounts(0, 4000, 100);
        assertThat(NpcHelpRules.covered(asked, new ResourceAmounts(0, 4000, 100))).isTrue();
        assertThat(NpcHelpRules.covered(asked, new ResourceAmounts(9000, 4000, 99))).isFalse();
        assertThat(NpcHelpRules.covered(asked, new ResourceAmounts(0, 5000, 200))).isTrue();
    }

    @Test
    void anAnswerInTimeIsWorthFifteenNoMinusFiveIgnoredMinusTenLateNothing() {
        assertThat(NpcHelpRules.trustFor(NpcHelpState.ANSWERED, SETTINGS, false)).isEqualTo(15);
        assertThat(NpcHelpRules.trustFor(NpcHelpState.DECLINED, SETTINGS, false)).isEqualTo(-5);
        assertThat(NpcHelpRules.trustFor(NpcHelpState.IGNORED, SETTINGS, false)).isEqualTo(-10);
        assertThat(NpcHelpRules.trustFor(NpcHelpState.LATE, SETTINGS, false)).isZero();
        assertThat(NpcHelpRules.trustFor(NpcHelpState.CANCELLED, SETTINGS, false)).isZero();
        // out of the human's reach: a no or no answer costs nothing, an answer still counts
        assertThat(NpcHelpRules.trustFor(NpcHelpState.DECLINED, SETTINGS, true)).isZero();
        assertThat(NpcHelpRules.trustFor(NpcHelpState.IGNORED, SETTINGS, true)).isZero();
        assertThat(NpcHelpRules.trustFor(NpcHelpState.ANSWERED, SETTINGS, true)).isEqualTo(15);
        assertThat(NpcHelpRules.atDeadline(true)).isEqualTo(NpcHelpState.LATE);
        assertThat(NpcHelpRules.atDeadline(false)).isEqualTo(NpcHelpState.IGNORED);
    }

    @Test
    void anAllyWarnsOnceBelowTwentyFiveAndAgainOnlyAfterRisingAboveThirtyFive() {
        assertThat(NpcHelpRules.warning(24, false, SETTINGS)).isEqualTo(NpcHelpRules.Warning.WRITE);
        assertThat(NpcHelpRules.warning(25, false, SETTINGS)).isEqualTo(NpcHelpRules.Warning.NONE);
        assertThat(NpcHelpRules.warning(-1, false, SETTINGS))
                .isEqualTo(NpcHelpRules.Warning.NONE); // it leaves instead
        assertThat(NpcHelpRules.warning(5, true, SETTINGS)).isEqualTo(NpcHelpRules.Warning.NONE);
        assertThat(NpcHelpRules.warning(35, true, SETTINGS)).isEqualTo(NpcHelpRules.Warning.NONE);
        assertThat(NpcHelpRules.warning(36, true, SETTINGS)).isEqualTo(NpcHelpRules.Warning.REARM);
    }

    @Test
    void aThankYouShipmentGoesOutAtMostOnceADay() {
        assertThat(NpcHelpRules.shipsThanks(null, NOW, 1, SETTINGS)).isTrue();
        assertThat(NpcHelpRules.shipsThanks(NOW.minus(Duration.ofHours(23)), NOW, 1, SETTINGS))
                .isFalse();
        assertThat(NpcHelpRules.shipsThanks(NOW.minus(Duration.ofHours(24)), NOW, 1, SETTINGS))
                .isTrue();
        assertThat(NpcHelpRules.shipsThanks(NOW.minus(Duration.ofHours(8)), NOW, 3, SETTINGS))
                .isTrue();
    }

    @Test
    void theSheetFillsTheFastestDefendersFirstAndLeavesTooSlowOnesHome() {
        List<UnitType> types = List.copyOf(NpcHelp.DEFENDERS);
        // one unit's travel time: its minutes per field over 10 fields
        java.util.function.ToLongFunction<UnitType> seconds =
                type -> (long) (DATA.unit(type).minutesPerField() * 10 * 60);
        long spear = seconds.applyAsLong(UnitType.SPEARMAN);
        long sword = seconds.applyAsLong(UnitType.SWORDSMAN);
        Map<UnitType, Integer> home =
                Map.of(UnitType.SPEARMAN, 200, UnitType.SWORDSMAN, 100, UnitType.AXEMAN, 500);
        // time enough for both: spearmen first, swordsmen for the rest
        assertThat(NpcHelpRules.fill(home, types, 150, seconds, sword))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 150));
        assertThat(NpcHelpRules.fill(home, types, 250, seconds, sword))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 200, UnitType.SWORDSMAN, 50));
        assertThat(NpcHelpRules.fill(home, types, 1000, seconds, sword))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 200, UnitType.SWORDSMAN, 100));
        // swordsmen would land too late: only the spearmen go
        assertThat(NpcHelpRules.fill(home, types, 250, seconds, spear))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 200));
        // nothing lands in time: still the fastest first, the sheet says it is too late
        assertThat(NpcHelpRules.fill(home, types, 250, seconds, 0))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 200, UnitType.SWORDSMAN, 50));
        // heavy cavalry is faster than spearmen
        assertThat(
                        NpcHelpRules.fill(
                                Map.of(UnitType.SPEARMAN, 100, UnitType.HEAVY_CAVALRY, 30),
                                types,
                                50,
                                seconds,
                                spear))
                .isEqualTo(Map.of(UnitType.HEAVY_CAVALRY, 30, UnitType.SPEARMAN, 20));
        assertThat(NpcHelpRules.fill(Map.of(UnitType.AXEMAN, 50), types, 300, seconds, spear))
                .isEmpty();
    }

    @Test
    void invalidNumbersStopTheStart() {
        assertThatThrownBy(
                        () ->
                                new NpcHelpSettings(
                                        0, 12, 12, 15, -5, -10, 24, 0.5, 100, 5000, 10, 25))
                .hasMessageContaining("per-day");
        assertThatThrownBy(
                        () ->
                                new NpcHelpSettings(
                                        3, 12, 12, 15, 5, -10, 24, 0.5, 100, 5000, 10, 25))
                .hasMessageContaining("declined");
        assertThatThrownBy(
                        () -> new NpcHelpSettings(3, 12, 12, 15, -5, -10, 24, 0.5, 100, 50, 10, 25))
                .hasMessageContaining("min-defenders");
        assertThatThrownBy(
                        () ->
                                new NpcHelpSettings(
                                        3, 12, 12, 15, -5, -10, 24, 0.5, 100, 5000, 30, 25))
                .hasMessageContaining("warn-below");
    }
}
