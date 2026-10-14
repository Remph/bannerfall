package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 02-game-rules §10: {@code min(100, loyalty + loyaltyRegenPerHour × speed × hours)}; every
 * surviving noble subtracts {@code randomInt(20, 35)}.
 */
class LoyaltyTest {

    private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void regenPerHourIsScaledBySpeed() {
        assertThat(Loyalty.regenPerHour(WorldSettings.defaults())).isEqualTo(1.0);
        assertThat(Loyalty.regenPerHour(WorldSettings.defaults().withSpeed(3))).isEqualTo(3.0);
    }

    @Test
    void regeneratesLinearly() {
        assertThat(Loyalty.regen(25, 1.0, T0, T0.plus(Duration.ofHours(10))))
                .isCloseTo(35, within(1e-9));
        assertThat(Loyalty.regen(25, 3.0, T0, T0.plus(Duration.ofMinutes(30))))
                .isCloseTo(26.5, within(1e-9));
    }

    @Test
    void stopsAtOneHundred() {
        assertThat(Loyalty.regen(95, 1.0, T0, T0.plus(Duration.ofHours(10)))).isEqualTo(100);
        assertThat(Loyalty.regen(100, 1.0, T0, T0.plus(Duration.ofHours(1)))).isEqualTo(100);
    }

    @Test
    void timeBeforeTheAnchorChangesNothing() {
        Instant earlier = T0.minus(Duration.ofHours(5));

        assertThat(Loyalty.regen(25, 1.0, T0, earlier)).isEqualTo(25);
        assertThat(Loyalty.newAnchor(T0, earlier)).isEqualTo(T0);
        assertThat(Loyalty.newAnchor(T0, T0.plusSeconds(1))).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void eachNobleHitsWithItsOwnDraw() {
        // nextInt(20, 35) over 16 values: 0.0 → 20, 0.5 → 28, 0.99 → 35
        assertThat(Loyalty.afterNobles(100, 1, FixedRandomSource.constant(0.0))).isEqualTo(80);
        assertThat(Loyalty.afterNobles(100, 1, FixedRandomSource.constant(0.99))).isEqualTo(65);
        assertThat(Loyalty.afterNobles(100, 3, FixedRandomSource.sequence(0.0, 0.5, 0.99)))
                .isEqualTo(100 - 20 - 28 - 35);
        assertThat(Loyalty.afterNobles(57.5, 0, FixedRandomSource.constant(0.5))).isEqualTo(57.5);
    }

    @Test
    void anOathBannerSoftensEachHit() {
        // 02 §21.2: round(randomInt(20, 35) × (1 − b)) per noble
        assertThat(Loyalty.afterNobles(100, 1, FixedRandomSource.constant(0.99), 0.12))
                .isEqualTo(100 - 31); // 35 × 0.88 = 30.8
        assertThat(Loyalty.afterNobles(100, 1, FixedRandomSource.constant(0.0), 0.04))
                .isEqualTo(100 - 19); // 20 × 0.96 = 19.2
        assertThat(Loyalty.afterNobles(100, 2, FixedRandomSource.sequence(0.0, 0.99), 0.08))
                .isEqualTo(100 - 18 - 32); // 18.4 and 32.2
        assertThat(Loyalty.afterNobles(100, 1, FixedRandomSource.constant(0.99), 0)).isEqualTo(65);
    }

    @Test
    void aFullVillageFallsWhenTheHitsAddUpToOneHundred() {
        // three high hits are enough (100 − 105), four low ones are not (100 − 80)
        assertThat(Loyalty.conquers(Loyalty.afterNobles(100, 3, FixedRandomSource.constant(0.99))))
                .isTrue();
        assertThat(Loyalty.afterNobles(100, 4, FixedRandomSource.constant(0.0))).isEqualTo(20);
        assertThat(Loyalty.conquers(Loyalty.afterNobles(100, 4, FixedRandomSource.constant(0.0))))
                .isFalse();
    }

    @Test
    void zeroOrBelowConquers() {
        assertThat(Loyalty.conquers(0.0)).isTrue();
        assertThat(Loyalty.conquers(-40)).isTrue();
        assertThat(Loyalty.conquers(0.5)).isFalse();
    }

    @Test
    void shownIsRoundedDownAndNeverNegative() {
        assertThat(Loyalty.shown(57.9)).isEqualTo(57);
        assertThat(Loyalty.shown(100)).isEqualTo(100);
        assertThat(Loyalty.shown(-15)).isZero();
    }
}
