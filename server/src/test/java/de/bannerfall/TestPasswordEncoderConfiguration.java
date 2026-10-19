package de.bannerfall;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

/**
 * The server's password hashes ({@code SecurityConfig}), but with one PBKDF2 round instead of
 * 310,000. A full run registers over 1,000 players and logs many of them in; at a fifth of a second
 * per hash that was more than half of the test time. The stored format ({@code
 * {pbkdf2@SpringSecurity_v5_8}...}) stays the same.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPasswordEncoderConfiguration {

    @Bean
    @Primary
    PasswordEncoder testPasswordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        Pbkdf2PasswordEncoder oneRound =
                new Pbkdf2PasswordEncoder(
                        "", 16, 1, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder(id, Map.of(id, oneRound));
    }
}
