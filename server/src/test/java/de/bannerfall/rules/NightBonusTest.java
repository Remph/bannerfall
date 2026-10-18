package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** The night bonus window (1.4, 02-game-rules §8.1). */
class NightBonusTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** The defaults: 23:00 to 07:00 German time, defence × 2. */
    private final NightBonus night = new NightBonus(true, 23, 7, 2.0, BERLIN);

    @Test
    void theWindowRunsAcrossMidnightWithTheStartInAndTheEndOut() {
        // October: Berlin is UTC+2
        assertThat(night.isNight(Instant.parse("2026-10-18T20:59:59Z"))).isFalse(); // 22:59:59
        assertThat(night.isNight(Instant.parse("2026-10-18T21:00:00Z"))).isTrue(); // 23:00
        assertThat(night.isNight(Instant.parse("2026-10-18T22:30:00Z"))).isTrue(); // 00:30
        assertThat(night.isNight(Instant.parse("2026-10-19T04:59:59Z"))).isTrue(); // 06:59:59
        assertThat(night.isNight(Instant.parse("2026-10-19T05:00:00Z"))).isFalse(); // 07:00
        assertThat(night.isNight(Instant.parse("2026-10-19T10:00:00Z"))).isFalse(); // 12:00
    }

    @Test
    void theHoursAreReadInTheZoneAlsoInWinterTime() {
        // December: Berlin is UTC+1, so 22:00 UTC is 23:00 there
        assertThat(night.isNight(Instant.parse("2026-12-01T21:30:00Z"))).isFalse(); // 22:30
        assertThat(night.isNight(Instant.parse("2026-12-01T22:00:00Z"))).isTrue(); // 23:00
        assertThat(night.isNight(Instant.parse("2026-12-02T06:00:00Z"))).isFalse(); // 07:00
    }

    @Test
    void aWindowWithinOneDay() {
        var early = new NightBonus(true, 1, 6, 1.5, BERLIN);
        assertThat(early.isNight(Instant.parse("2026-10-18T22:59:00Z"))).isFalse(); // 00:59
        assertThat(early.isNight(Instant.parse("2026-10-18T23:00:00Z"))).isTrue(); // 01:00
        assertThat(early.isNight(Instant.parse("2026-10-19T03:59:00Z"))).isTrue(); // 05:59
        assertThat(early.isNight(Instant.parse("2026-10-19T04:00:00Z"))).isFalse(); // 06:00
    }

    @Test
    void theFactorCountsOnlyAtNightWithTheSwitchOnAndForOwnedVillages() {
        Instant midnight = Instant.parse("2026-10-18T22:00:00Z");
        Instant noon = Instant.parse("2026-10-19T10:00:00Z");
        assertThat(night.factorAt(midnight, true)).isEqualTo(2.0);
        assertThat(night.factorAt(noon, true)).isEqualTo(1.0);
        // barbarians never get it
        assertThat(night.factorAt(midnight, false)).isEqualTo(1.0);
        var off = new NightBonus(false, 23, 7, 2.0, BERLIN);
        assertThat(off.factorAt(midnight, true)).isEqualTo(1.0);
    }

    @Test
    void anEmptyWindowHoursOutOfRangeAndAFactorBelowOneAreRejected() {
        assertThatThrownBy(() -> new NightBonus(true, 7, 7, 2.0, BERLIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NightBonus(true, 24, 7, 2.0, BERLIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NightBonus(true, 23, -1, 2.0, BERLIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NightBonus(true, 23, 7, 0.9, BERLIN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
