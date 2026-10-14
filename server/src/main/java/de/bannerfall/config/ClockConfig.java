package de.bannerfall.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only source of "now". Never call Instant.now() directly; inject this Clock instead. */
@Configuration
class ClockConfig {

    /**
     * Ticks in whole milliseconds. The system clock can be finer than PostgreSQL's microseconds;
     * then a time saved and loaded again would differ from the one in memory. With millisecond
     * steps every instant survives the round trip unchanged (and matches the X-Server-Time header).
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
