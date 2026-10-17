package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarketExchangeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @ParameterizedTest(name = "market {0} at speed {1} → {2} per day")
    @CsvSource({
        "0, 1, 0",
        "1, 1, 1000",
        "10, 1, 10000",
        "25, 1, 25000",
        "10, 6, 60000",
        "3, 0.5, 1500",
        "1, 0.3333, 333"
    })
    void theDailyCapGrowsWithTheMarketLevelAndTheSpeed(int level, double speed, int expected) {
        assertThat(MarketExchange.dailyCap(level, speed)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} at {1} costs {2}")
    @CsvSource({
        "1000, 1.5, 1500",
        "1, 1.5, 2",
        "999, 1.5, 1499",
        "1000, 1.1, 1100",
        "1000, 1, 1000",
        "7, 3.3, 24",
        "0, 1.5, 0"
    })
    void theCostRoundsUpInDecimals(int amount, double ratio, int expected) {
        assertThat(MarketExchange.cost(amount, ratio)).isEqualTo(expected);
    }

    @Test
    void negativeInputIsRejected() {
        assertThatThrownBy(() -> MarketExchange.dailyCap(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketExchange.cost(-1, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDayRunsFromMidnightToMidnightInTheZone() {
        // 23:30 in Berlin (summer time, UTC+2) is 21:30 UTC
        Instant lateEvening = Instant.parse("2026-10-15T21:30:00Z");
        assertThat(MarketExchange.dayStart(lateEvening, BERLIN))
                .isEqualTo(Instant.parse("2026-10-14T22:00:00Z"));
        assertThat(MarketExchange.nextReset(lateEvening, BERLIN))
                .isEqualTo(Instant.parse("2026-10-15T22:00:00Z"));
        // at midnight sharp a new day has begun
        Instant midnight = Instant.parse("2026-10-15T22:00:00Z");
        assertThat(MarketExchange.dayStart(midnight, BERLIN)).isEqualTo(midnight);
    }

    @Test
    void theDayOfTheClockChangeHasItsRealLength() {
        // 2026-10-25: Berlin goes back from UTC+2 to UTC+1, the day lasts 25 hours
        Instant noon = Instant.parse("2026-10-25T11:00:00Z");
        assertThat(MarketExchange.dayStart(noon, BERLIN))
                .isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
        assertThat(MarketExchange.nextReset(noon, BERLIN))
                .isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    }
}
