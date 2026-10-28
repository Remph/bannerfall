package de.bannerfall.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonPasswordsTest {

    @Test
    void refusesTheMostUsedPasswordsIgnoringCase() {
        assertThat(CommonPasswords.refuses("password123", "ben")).isTrue();
        assertThat(CommonPasswords.refuses("PassWord123", "ben")).isTrue();
        assertThat(CommonPasswords.refuses("12345678", "ben")).isTrue();
        assertThat(CommonPasswords.refuses("Qwertz123", "ben")).isTrue();
    }

    @Test
    void refusesTheAccountsOwnNameAndOneRepeatedCharacter() {
        assertThat(CommonPasswords.refuses("Anna_Maria", "anna_maria")).isTrue();
        assertThat(CommonPasswords.refuses("xxxxxxxxxx", "ben")).isTrue();
    }

    @Test
    void acceptsAnythingElse() {
        assertThat(CommonPasswords.refuses("correct horse", "ben")).isFalse();
        assertThat(CommonPasswords.refuses("a new secret", "ben")).isFalse();
        assertThat(CommonPasswords.refuses("Tr0ub4dor&3", "ben")).isFalse();
    }

    @Test
    void theListHoldsOnlyLowerCasePasswordsThatPassTheLengthRule() throws Exception {
        try (var in = CommonPasswordsTest.class.getResourceAsStream("/common-passwords.txt")) {
            List<String> entries =
                    new String(in.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .filter(line -> !line.isBlank() && !line.startsWith("#"))
                            .toList();

            assertThat(entries).hasSizeGreaterThan(200);
            assertThat(entries)
                    .allSatisfy(
                            entry -> {
                                assertThat(entry).isEqualTo(entry.toLowerCase());
                                assertThat(entry.length()).isGreaterThanOrEqualTo(8);
                            });
        }
    }
}
