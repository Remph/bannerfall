package de.bannerfall.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The passwords a new password may not be (security review of 1.7): usernames are public (the
 * ranking), so a guessed common password is the way into an account. The list is short on purpose
 * (the most used passwords of 8 or more characters, {@code common-passwords.txt}); the length rule
 * and the login limits do the rest. A password is also refused if it is the account's own name, or
 * one character repeated.
 */
final class CommonPasswords {

    private static final Set<String> COMMON = load();

    private CommonPasswords() {}

    /**
     * Whether {@code password} is too easy to guess for the account called {@code username}.
     * Compared without regard to case.
     */
    static boolean refuses(String password, String username) {
        String lower = password.toLowerCase(Locale.ROOT);
        return COMMON.contains(lower)
                || lower.equals(username.toLowerCase(Locale.ROOT))
                || lower.chars().distinct().count() == 1;
    }

    private static Set<String> load() {
        try (InputStream in = CommonPasswords.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt is missing");
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
