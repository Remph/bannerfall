package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    /**
     * The integration tests hash with one round ({@code TestPasswordEncoderConfiguration}), so this
     * is the only test of the server's own encoder: the stored format and the 128 characters a
     * password may have (bcrypt would stop at 72 bytes).
     */
    @Test
    void theServerHashesLongPasswordsWithPbkdf2() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
        String password = "ä".repeat(128);

        String hash = encoder.encode(password);

        assertThat(hash).startsWith("{pbkdf2@SpringSecurity_v5_8}");
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches("ä".repeat(127) + "a", hash)).isFalse();
    }
}
