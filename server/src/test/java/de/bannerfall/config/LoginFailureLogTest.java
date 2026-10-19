package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** A failed login through the account server is logged without what the caller wrote. */
class LoginFailureLogTest {

    @Test
    void onlyTheErrorCodeAndNoLineBreaks() {
        OAuth2AuthenticationException forged =
                new OAuth2AuthenticationException(
                        new OAuth2Error("x\r\n2026-10-19 INFO forged", "a description", null),
                        "[x] a description");

        assertThat(SecurityConfig.loginFailure(forged))
                .isEqualTo("x??2026-10-19?INFO?forged")
                .doesNotContain("description");
        assertThat(SecurityConfig.loginFailure(new BadCredentialsException("anything")))
                .isEqualTo("BadCredentialsException");
    }
}
