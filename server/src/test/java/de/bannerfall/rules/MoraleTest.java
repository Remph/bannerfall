package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Morale (1.6, 02-game-rules §8.2), with the test vectors of the rulebook. */
class MoraleTest {

    private static final Morale CLASSIC = new Morale(true, Morale.MIN_FLOOR);

    @Test
    void theClassicCurveWithRealmOnePoints() {
        // 6 449 points against 1 339: 0.3 + 3 × 0.2076 = 0.9229 → 92 %
        assertThat(CLASSIC.factor(6449, 1339)).isEqualTo(0.92);
        // against 377: 0.4754 → 48 %
        assertThat(CLASSIC.factor(6449, 377)).isEqualTo(0.48);
        // against 88: 0.3409 → 34 %
        assertThat(CLASSIC.factor(6449, 88)).isEqualTo(0.34);
        // against 4 346: above 1, so full strength
        assertThat(CLASSIC.factor(6449, 4346)).isEqualTo(1);
    }

    @Test
    void fullMoraleFromAboutAQuarterOfThePoints() {
        // 0.3 + 3 × 0.2333… = 1
        assertThat(CLASSIC.factor(3000, 700)).isEqualTo(1);
        assertThat(CLASSIC.factor(3000, 690)).isEqualTo(0.99);
    }

    @Test
    void neverAboveOneAgainstABiggerPlayer() {
        assertThat(CLASSIC.factor(100, 10_000)).isEqualTo(1);
        assertThat(CLASSIC.factor(500, 500)).isEqualTo(1);
    }

    @Test
    void theFloorHoldsAgainstTheSmallest() {
        assertThat(CLASSIC.factor(10_000, 0)).isEqualTo(0.3);
        assertThat(new Morale(true, 0.5).factor(6449, 88)).isEqualTo(0.5);
        assertThat(new Morale(true, 0.5).factor(6449, 377)).isEqualTo(0.5);
        assertThat(new Morale(true, 0.5).factor(6449, 1339)).isEqualTo(0.92);
    }

    @Test
    void offAndBarbariansAreAlwaysFullStrength() {
        assertThat(Morale.OFF.factor(6449, 88)).isEqualTo(1);
        assertThat(CLASSIC.factor(6449, 0, false)).isEqualTo(1);
        assertThat(CLASSIC.factor(6449, 377, true)).isEqualTo(0.48);
    }

    @Test
    void rejectsAFloorOutsideTheRangeAndNegativePoints() {
        assertThatThrownBy(() -> new Morale(true, 0.29))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Morale(true, 1.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CLASSIC.factor(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
