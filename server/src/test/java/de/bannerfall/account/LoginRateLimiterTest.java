package de.bannerfall.account;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final MutableClock clock = MutableClock.startingAt("2026-10-02T20:00:00Z");
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    private static final String HOME = "198.51.100.7";

    private void fail(String key, int times) {
        fail(key, HOME, times);
    }

    private void fail(String key, String address, int times) {
        for (int i = 0; i < times; i++) {
            limiter.tryAttempt(key, address);
        }
    }

    @Test
    void blocksAfterFiveFailures() {
        fail("ben", 4);
        assertThat(limiter.isBlocked("ben", HOME)).isFalse();

        fail("ben", 1);
        assertThat(limiter.isBlocked("ben", HOME)).isTrue();
    }

    @Test
    void blockEndsOneMinuteAfterTheLastFailure() {
        fail("ben", 5);

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.isBlocked("ben", HOME)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
    }

    @Test
    void successResetsTheCounter() {
        fail("ben", 4);
        limiter.recordSuccess("ben", HOME);
        fail("ben", 4);

        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
    }

    @Test
    void failuresFarApartDoNotAddUp() {
        for (int i = 0; i < 10; i++) {
            fail("ben", 1);
            clock.advance(Duration.ofMinutes(2));
        }

        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
    }

    @Test
    void anAttemptCountsBeforeItsAnswerAndABlockedOneIsRefused() {
        // five requests at once all count, even before any password was checked
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAttempt("ben", HOME)).isTrue();
        }
        assertThat(limiter.tryAttempt("ben", HOME)).isFalse();
        assertThat(limiter.isBlocked("ben", HOME)).isTrue();
    }

    @Test
    void usernamesAreCountedSeparately() {
        fail("ben", 5);

        assertThat(limiter.isBlocked("anna", HOME)).isFalse();
    }

    /** Code review 1.2, D1: a stranger's wrong guesses don't lock the owner out. */
    @Test
    void aBlockHoldsOnlyForTheAddressThatFailed() {
        fail("ben", "203.0.113.9", 5);

        assertThat(limiter.isBlocked("ben", "203.0.113.9")).isTrue();
        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
        assertThat(limiter.tryAttempt("ben", HOME)).isTrue();
    }

    @Test
    void forgettingANameLiftsItsBlocksEverywhere() {
        fail("ben", "203.0.113.9", 5);
        fail("ben", 5);
        fail("anna", 5);

        limiter.forget("ben");

        assertThat(limiter.isBlocked("ben", "203.0.113.9")).isFalse();
        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
        assertThat(limiter.isBlocked("anna", HOME)).isTrue();
    }
}
