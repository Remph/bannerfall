package de.bannerfall.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The host token (08-host-console §4): {@code bannerfall.admin.token}, in production the env
 * variable {@code BANNERFALL_ADMIN_TOKEN} ({@code openssl rand -base64 32}). Unset or blank = the
 * token door is closed. A token shorter than {@value #MIN_LENGTH} characters stops the server at
 * start: it would be as guessable as a short password with admin rights.
 */
@Component
public class AdminToken {

    static final int MIN_LENGTH = 32;

    private final byte[] token;

    AdminToken(@Value("${bannerfall.admin.token:}") String token) {
        String trimmed = token == null ? "" : token.strip();
        if (!trimmed.isEmpty() && trimmed.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "bannerfall.admin.token (BANNERFALL_ADMIN_TOKEN) must have at least "
                            + MIN_LENGTH
                            + " characters, e.g. from `openssl rand -base64 32`; leave it unset to"
                            + " close the host token door");
        }
        this.token = trimmed.isEmpty() ? null : trimmed.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isConfigured() {
        return token != null;
    }

    /** Constant time: the answer time doesn't tell how much of the token was right. */
    boolean matches(String given) {
        return token != null
                && given != null
                && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8));
    }
}
