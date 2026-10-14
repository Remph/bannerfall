package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * {@code @Scheduled} (event worker, cleanup) runs on Boot's scheduler, not on the STOMP broker's
 * pool, although the broker brings its own {@code TaskScheduler} bean (M9a). The integration tests
 * switch the scheduler off, so this small context checks it with the scheduler on.
 */
class EventSchedulingConfigTest {

    @Test
    void scheduledRunsOnBootsSchedulerNotOnTheBrokersPool() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(Broker.class, EventSchedulingConfig.class, Probe.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            String thread =
                                    context.getBean(Probe.class).thread.get(5, TimeUnit.SECONDS);
                            assertThat(thread).startsWith("scheduling-");
                        });
    }

    /** A broker like {@code WebSocketConfig}'s, which brings the "MessageBroker-N" scheduler. */
    @Configuration
    @EnableWebSocketMessageBroker
    static class Broker implements WebSocketMessageBrokerConfigurer {

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws");
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableSimpleBroker("/topic", "/queue");
        }
    }

    static class Probe {

        final CompletableFuture<String> thread = new CompletableFuture<>();

        @Scheduled(fixedDelay = 10)
        void tick() {
            thread.complete(Thread.currentThread().getName());
        }
    }
}
