package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the {@link TestEventHandler} (event kind TEST) in the shared test context. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEventHandlerConfiguration {

    @Bean
    TestEventHandler testEventHandler() {
        return new TestEventHandler();
    }
}
