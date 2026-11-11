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

    @Test
    void forgetsTheAddressOnceTheBlockHasRunOut() {
        fail("ben", 5);
        fail("ada", "203.0.113.4", 1);

        clock.advance(Duration.ofSeconds(59));
        limiter.forgetExpired();
        assertThat(limiter.entries()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(1));
        limiter.forgetExpired();
        assertThat(limiter.entries()).isZero();
    }

    @Test
    void anIpv6NetworkIsOneClient() {
        // five addresses of one /64 are one address ...
        for (int i = 0; i < 5; i++) {
            limiter.tryAttempt("ben", "2001:db8:1:2::" + Integer.toHexString(i + 1));
        }

        assertThat(limiter.isBlocked("ben", "2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isTrue();
        assertThat(limiter.tryAttempt("ben", "2001:db8:1:2:ffff::1")).isFalse();
        // ... another network, and an IPv4 client, are not blocked with it
        assertThat(limiter.isBlocked("ben", "2001:db8:1:3::1")).isFalse();
        assertThat(limiter.isBlocked("ben", HOME)).isFalse();
    }

    /** 2.0, F4: guesses from many networks used up one budget per name. */
    @Test
    void aNameIsLockedAfterTooManyFailuresFromAnyAddresses() {
        // five networks with four failures each: no address is blocked ...
        for (int network = 0; network < 5; network++) {
            fail("ben", "203.0.113." + network, 4);
        }
        assertThat(limiter.isBlocked("ben", "203.0.113.0")).isFalse();
        assertThat(limiter.isLocked("ben")).isTrue();

        // ... but the name takes no further attempt, from a new address either
        assertThat(limiter.tryAttempt("ben", "198.51.100.77")).isFalse();
        assertThat(limiter.tryAttempt("ben", "203.0.113.0")).isFalse();
        assertThat(limiter.isLocked("anna")).isFalse();
        assertThat(limiter.tryAttempt("anna", "198.51.100.77")).isTrue();
    }

    @Test
    void justUnderTheNamesBudgetStillAllowsALogin() {
        for (int network = 0; network < 5; network++) {
            fail("ben", "203.0.113." + network, 3);
        }
        fail("ben", "203.0.113.9", 4);

        assertThat(limiter.isLocked("ben")).isFalse();
        assertThat(limiter.tryAttempt("ben", HOME)).isTrue();
    }

    @Test
    void theNamesLockEndsWithItsWindow() {
        for (int network = 0; network < 5; network++) {
            fail("ben", "203.0.113." + network, 4);
        }

        clock.advance(LoginRateLimiter.ACCOUNT_WINDOW.minusSeconds(1));
        assertThat(limiter.isLocked("ben")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.isLocked("ben")).isFalse();
        assertThat(limiter.tryAttempt("ben", HOME)).isTrue();
    }

    @Test
    void failuresInSeparateWindowsDoNotAddUp() {
        for (int round = 0; round < 3; round++) {
            for (int network = 0; network < 3; network++) {
                fail("ben", "203.0.113." + network, 3);
            }
            clock.advance(LoginRateLimiter.ACCOUNT_WINDOW);
        }

        assertThat(limiter.isLocked("ben")).isFalse();
    }

    @Test
    void aBlockedAddressDoesNotUseUpTheNamesBudget() {
        fail("ben", "203.0.113.9", 5);
        // a script hammers on from the blocked address: refused, and not counted for the name
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAttempt("ben", "203.0.113.9")).isFalse();
        }

        assertThat(limiter.isLocked("ben")).isFalse();
        assertThat(limiter.tryAttempt("ben", HOME)).isTrue();
    }

    @Test
    void aRightPasswordClearsTheNamesFailures() {
        fail("ben", "203.0.113.1", 4);
        fail("ben", "203.0.113.2", 4);
        fail("ben", "203.0.113.3", 4);
        fail("ben", "203.0.113.4", 4);
        limiter.tryAttempt("ben", HOME);
        limiter.recordSuccess("ben", HOME);
        fail("ben", "203.0.113.5", 4);

        assertThat(limiter.isLocked("ben")).isFalse();
    }

    @Test
    void forgettingANameLiftsItsLockAndExpiredNamesAreDropped() {
        for (int network = 0; network < 5; network++) {
            fail("ben", "203.0.113." + network, 4);
        }
        fail("ada", "198.51.100.1", 1);

        limiter.forget("ben");
        assertThat(limiter.isLocked("ben")).isFalse();
        assertThat(limiter.accountsHeld()).isEqualTo(1);

        clock.advance(LoginRateLimiter.ACCOUNT_WINDOW);
        limiter.forgetExpired();
        assertThat(limiter.accountsHeld()).isZero();
    }
}
