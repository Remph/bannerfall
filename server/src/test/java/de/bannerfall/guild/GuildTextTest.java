package de.bannerfall.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The text rules of guilds (02-game-rules §16), without Spring. */
class GuildTextTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Wolves",
                "abc",
                "Die Wölfe",
                "Iron-Wolves_2",
                "St. Mary's Guard",
                "Ünïcödé 日本",
                "123",
                "  Padded name \t"
            })
    void goodNamesAreAcceptedAndTrimmed(String name) {
        assertThat(GuildText.name(name)).isEqualTo(name.strip());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "  ",
                "ab",
                "-Wolves",
                "Wolves-",
                "Two  Spaces",
                "Tab\tinside",
                "Line\nbreak",
                "Wolves!",
                "<b>Wolves</b>",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" // 31
            })
    void badNamesAreRefused(String name) {
        assertThatThrownBy(() -> GuildText.name(name))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void aNameOfThirtyCharactersIsFine() {
        assertThat(GuildText.name("a".repeat(30))).hasSize(30);
        // counted as characters, not UTF-16 units: ten emoji-free letters outside the BMP
        assertThat(GuildText.name("𝐀".repeat(5) + "abc")).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "iw", "W0LF", "12345", " Tag "})
    void goodTagsAreAcceptedAndTrimmed(String tag) {
        assertThat(GuildText.tag(tag)).isEqualTo(tag.strip());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "ABCDEF", "A B", "A-B", "Ä1", "日本", "A\nB"})
    void badTagsAreRefused(String tag) {
        assertThatThrownBy(() -> GuildText.tag(tag))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void descriptionsMayBeEmptyAndKeepLineBreaks() {
        assertThat(GuildText.description(null)).isEmpty();
        assertThat(GuildText.description("  ")).isEmpty();
        assertThat(GuildText.description(" Line one\nLine two \n")).isEqualTo("Line one\nLine two");
        assertThat(GuildText.description("x".repeat(500))).hasSize(500);
    }

    @Test
    void descriptionsAreCappedAndHaveNoControlCharacters() {
        assertThatThrownBy(() -> GuildText.description("x".repeat(501)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GuildText.description("bell\u0007"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GuildText.description("a\u0000b"))
                .isInstanceOf(ApiException.class);
    }
}
