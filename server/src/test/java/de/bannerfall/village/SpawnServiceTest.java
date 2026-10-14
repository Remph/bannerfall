package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The spawn itself is tested through registration (RegistrationTest). */
class SpawnServiceTest {

    @Test
    void defaultNameFitsTheColumn() {
        assertThat(SpawnService.defaultName("Ben")).isEqualTo("Ben's village");

        String longName = "A".repeat(24);
        assertThat(SpawnService.defaultName(longName)).isEqualTo(longName);
        assertThat(SpawnService.defaultName("A".repeat(22))).hasSize(32);
    }
}
