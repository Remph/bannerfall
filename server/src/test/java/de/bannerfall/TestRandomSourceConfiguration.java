package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Integration tests can fix the dice: {@link TestRandomSource}. */
@TestConfiguration(proxyBeanMethods = false)
public class TestRandomSourceConfiguration {

    @Bean
    @Primary
    TestRandomSource testRandomSource() {
        return new TestRandomSource();
    }
}
