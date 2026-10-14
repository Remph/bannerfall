package de.bannerfall.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Switches on {@code @Scheduled}: the event worker's tick and the nightly cleanup. Tests set {@code
 * bannerfall.events.scheduler-enabled=false}; nothing then runs by itself and they call {@link
 * EventProcessor#processDue} by hand with a clock they control.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "bannerfall.events.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
class EventSchedulingConfig {

    /**
     * Spring Boot's default scheduler (one thread, "scheduling-1"), declared by hand since M9a: the
     * STOMP broker brings its own {@code TaskScheduler} bean, Boot then skips its default, and
     * {@code @Scheduled} would run on the broker's pool ("MessageBroker-N" threads, the tick and
     * the cleanup in parallel). With two schedulers {@code @Scheduled} takes the one named {@code
     * taskScheduler}.
     */
    @Bean
    ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
