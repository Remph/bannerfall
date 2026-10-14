package de.bannerfall.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.common.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatTextTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a\u0001b", // C0
                "a\u0085b", // C1: next line
                "a b", // line separator
                "a‮evil", // right-to-left override
                "a⁧b" // right-to-left isolate
            })
    void controlAndBidiCharactersAreRefused(String text) {
        assertThatThrownBy(() -> ChatText.checked(text)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "line\nbreak\tand tab",
                "Grüße, ¡hola!",
                "family 👨‍👩‍👧" // emoji joined by ZWJ
            })
    void ordinaryTextPasses(String text) {
        assertThat(ChatText.checked(text)).isEqualTo(text);
    }
}
