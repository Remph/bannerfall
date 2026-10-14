package de.bannerfall.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The host token's start-up check and the wrong-token limit (08-host-console §4). */
class AdminTokenTest {

    private static final String GOOD = "a".repeat(AdminToken.MIN_LENGTH);

    @Test
    void aShortTokenStopsTheServer() {
        assertThatThrownBy(() -> new AdminToken("a".repeat(AdminToken.MIN_LENGTH - 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void noTokenClosesTheDoor() {
        AdminToken none = new AdminToken("");
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.matches("")).isFalse();
        assertThat(new AdminToken("   ").isConfigured()).isFalse();
    }

    @Test
    void onlyTheExactTokenMatches() {
        AdminToken token = new AdminToken(" " + GOOD + "\n");
        assertThat(token.isConfigured()).isTrue();
        assertThat(token.matches(GOOD)).isTrue();
        assertThat(token.matches(GOOD + "a")).isFalse();
        assertThat(token.matches(GOOD.substring(1))).isFalse();
        assertThat(token.matches(null)).isFalse();
    }

    @Test
    void fiveWrongTokensInARowBlockThatAddressForAMinute() {
        MutableClock clock = MutableClock.startingAt("2026-10-08T10:00:00Z");
        AdminTokenLimiter limiter = new AdminTokenLimiter(clock);
        String outside = "172.18.0.5";
        for (int i = 0; i < AdminTokenLimiter.MAX_FAILURES; i++) {
            assertThat(limiter.isBlocked(outside)).isFalse();
            limiter.recordFailure(outside);
            clock.advance(Duration.ofSeconds(50));
        }
        assertThat(limiter.isBlocked(outside)).isTrue();
        // the CLI inside the container isn't locked out by somebody guessing from outside
        assertThat(limiter.isBlocked("127.0.0.1")).isFalse();
        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.isBlocked(outside)).isFalse();

        // failures more than a minute apart don't add up
        limiter.recordFailure(outside);
        clock.advance(Duration.ofMinutes(2));
        limiter.recordFailure(outside);
        assertThat(limiter.isBlocked(outside)).isFalse();
    }
}
