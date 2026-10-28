package de.bannerfall.admin;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.MutableClock;
import org.junit.jupiter.api.Test;

class AdminTokenLimiterTest {

    private final AdminTokenLimiter limiter =
            new AdminTokenLimiter(MutableClock.startingAt("2026-10-27T10:00:00Z"));

    @Test
    void anIpv6NetworkIsBlockedAsOneClient() {
        for (int i = 0; i < AdminTokenLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("2001:db8:1:2::" + Integer.toHexString(i + 1));
        }

        assertThat(limiter.isBlocked("2001:db8:1:2:aaaa::1")).isTrue();
        assertThat(limiter.isBlocked("2001:db8:1:3::1")).isFalse();
        assertThat(limiter.isBlocked("198.51.100.7")).isFalse();
    }

    @Test
    void aRightTokenFreesTheWholeNetwork() {
        for (int i = 0; i < AdminTokenLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("2001:db8:1:2::1");
        }

        limiter.recordSuccess("2001:db8:1:2::9");

        assertThat(limiter.isBlocked("2001:db8:1:2::1")).isFalse();
    }
}
