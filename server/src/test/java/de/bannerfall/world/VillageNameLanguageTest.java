package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The names the game gives villages (v1.3, 02-game-rules §2); in use: VillageNamesFlowTest. */
class VillageNameLanguageTest {

    @Test
    void defaultNameFitsTheColumn() {
        assertThat(VillageNameLanguage.EN.defaultName("Ben")).isEqualTo("Ben's village");

        String longName = "A".repeat(24);
        assertThat(VillageNameLanguage.EN.defaultName(longName)).isEqualTo(longName);
        assertThat(VillageNameLanguage.EN.defaultName("A".repeat(22))).hasSize(32);
    }

    @Test
    void germanDefaultNameFitsTheColumn() {
        assertThat(VillageNameLanguage.DE.defaultName("Graf Ottmar"))
                .isEqualTo("Dorf von Graf Ottmar");

        assertThat(VillageNameLanguage.DE.defaultName("A".repeat(23))).hasSize(32);
        String longName = "A".repeat(24);
        assertThat(VillageNameLanguage.DE.defaultName(longName)).isEqualTo(longName);
    }

    @Test
    void likePatternMatchesOnlyDefaultNames() {
        assertThat(VillageNameLanguage.EN.likePattern()).isEqualTo("_%'s village");
        assertThat(VillageNameLanguage.DE.likePattern()).isEqualTo("Dorf von _%");
    }
}
