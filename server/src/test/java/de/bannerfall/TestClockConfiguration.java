package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Integration tests run on a {@link MutableClock}: it stands still unless a test moves it ({@code
 * clock.advance(...)}). {@code @Primary} makes it win over the system clock of ClockConfig.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return MutableClock.startingAt("2026-01-01T00:00:00Z");
    }
}
