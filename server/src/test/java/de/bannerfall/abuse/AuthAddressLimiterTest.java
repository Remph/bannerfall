package de.bannerfall.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The client addresses of the login limits stay in memory only for a few minutes (/privacy). */
class AuthAddressLimiterTest {

    private final MutableClock clock = MutableClock.startingAt("2026-10-17T12:00:00Z");
    private final AuthAddressLimiter limiter = new AuthAddressLimiter(clock, 10, 3);

    @Test
    void forgetsAnAddressOnceItsBucketIsFullAgain() {
        limiter.tryLogin("198.51.100.7");
        limiter.tryRegister("198.51.100.8");
        assertThat(limiter.addressesHeld()).isEqualTo(2);

        // a login token comes back every 6 s, a registration token every 20 s
        clock.advance(Duration.ofSeconds(10));
        limiter.forgetOldAddresses();
        assertThat(limiter.addressesHeld()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(10));
        limiter.forgetOldAddresses();
        assertThat(limiter.addressesHeld()).isZero();
    }

    @Test
    void keepsTheLimitOfAnAddressStillInUse() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryRegister("198.51.100.7")).isTrue();
        }
        clock.advance(Duration.ofSeconds(5));
        limiter.forgetOldAddresses();

        assertThat(limiter.tryRegister("198.51.100.7")).isFalse();
    }
}
