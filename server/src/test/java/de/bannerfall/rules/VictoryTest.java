package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VictoryTest {

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");

    @ParameterizedTest(name = "{0} of {1} villages at {2} % → {3}")
    @CsvSource({
        "60, 100, 60, true",
        "59, 100, 60, false",
        "3, 5, 60, true",
        "2, 5, 60, false",
        "100, 100, 100, true",
        "0, 0, 60, false",
        "0, 10, 10, false",
        "1, 10, 10, true"
    })
    void aSideDominatesWithItsShareOfAllVillages(int side, int all, int percent, boolean expected) {
        assertThat(Victory.dominates(side, all, percent)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{1} % of {0} keeps → {2}")
    @CsvSource({
        "5, 51, 3",
        "4, 51, 3",
        "4, 99, 4",
        "4, 100, 4",
        "4, 50, 2",
        "4, 75, 3",
        "4, 76, 4",
        "2, 51, 2",
        "1, 1, 1",
        "5, 1, 1",
        "12, 51, 7",
        "5, 60, 3",
        "3, 100, 3"
    })
    void theKeepsNeededAreThePercentRoundedUp(int keeps, int percent, int needed) {
        assertThat(Victory.keepsNeeded(keeps, percent)).isEqualTo(needed);
    }

    @Test
    void theSameSideKeepsItsClock() {
        Instant since = T0.minus(Duration.ofHours(3));
        Victory.Clock clock = Victory.next("GUILD:1", since, "GUILD:1", T0);
        assertThat(clock).isEqualTo(new Victory.Clock("GUILD:1", since));
    }

    @Test
    void anotherSideStartsANewClock() {
        Victory.Clock clock =
                Victory.next("GUILD:1", T0.minus(Duration.ofHours(3)), "PLAYER:7", T0);
        assertThat(clock).isEqualTo(new Victory.Clock("PLAYER:7", T0));
        assertThat(Victory.next(null, null, "PLAYER:7", T0))
                .isEqualTo(new Victory.Clock("PLAYER:7", T0));
    }

    @Test
    void nobodyMeetingTheConditionStopsTheClock() {
        assertThat(Victory.next("GUILD:1", T0.minusSeconds(60), null, T0))
                .isEqualTo(new Victory.Clock(null, null));
    }

    @Test
    void theLeaderWinsAfterTheHoldTime() {
        var clock = new Victory.Clock("GUILD:1", T0);
        assertThat(clock.won(T0.plus(Duration.ofHours(48)).minusSeconds(1), 48)).isFalse();
        assertThat(clock.won(T0.plus(Duration.ofHours(48)), 48)).isTrue();
        assertThat(new Victory.Clock(null, null).won(T0.plus(Duration.ofDays(9)), 48)).isFalse();
        assertThat(Victory.winsAt(T0, 48)).isEqualTo(T0.plus(Duration.ofHours(48)));
    }
}
