package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * The test world has an account server ({@link FakeAccountServer}), in mode OFF: the tests of the
 * login through it switch the mode on ({@code AccountServerSettings#setMode}) and back.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAccountServerConfiguration {

    @Bean
    DynamicPropertyRegistrar accountServerProperties() {
        return registry -> {
            registry.add(
                    "bannerfall.account-server.issuer", () -> FakeAccountServer.get().issuer());
            registry.add("bannerfall.account-server.client-id", () -> FakeAccountServer.CLIENT_ID);
            registry.add(
                    "bannerfall.account-server.client-secret",
                    () -> FakeAccountServer.CLIENT_SECRET);
            registry.add("bannerfall.account-server.name", () -> "accounts.example.com");
            registry.add("bannerfall.account-server.mode", () -> "OFF");
        };
    }
}
