package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    /** Finer steps would not survive the round trip through PostgreSQL (microseconds). */
    @Test
    void theClockTicksInWholeMilliseconds() {
        Instant now = new ClockConfig().clock().instant();

        assertThat(now.getNano() % 1_000_000).isZero();
    }
}
