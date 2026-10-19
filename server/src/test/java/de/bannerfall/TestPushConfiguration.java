package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * The test world sends push notifications (1.5), with a fixed key pair, only to the {@link
 * FakePushService}, on one thread so pushes leave in the order they were caused.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPushConfiguration {

    public static final String PUBLIC_KEY =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

    static final String PRIVATE_KEY = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    @Bean
    DynamicPropertyRegistrar pushProperties() {
        return registry -> {
            registry.add("bannerfall.push.public-key", () -> PUBLIC_KEY);
            registry.add("bannerfall.push.private-key", () -> PRIVATE_KEY);
            registry.add("bannerfall.push.services", () -> FakePushService.get().origin());
            registry.add("bannerfall.push.threads", () -> "1");
        };
    }
}
