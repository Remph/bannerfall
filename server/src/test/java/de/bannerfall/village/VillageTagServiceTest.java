package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.common.ApiException;
import org.junit.jupiter.api.Test;

/** The tag rules of 02-game-rules §26, pure. */
class VillageTagServiceTest {

    @Test
    void aTagIsTrimmedAndWrittenInCapitals() {
        assertThat(VillageTagService.normalise("  off ")).isEqualTo("OFF");
        assertThat(VillageTagService.normalise("Def2")).isEqualTo("DEF2");
        assertThat(VillageTagService.normalise("a")).isEqualTo("A");
        assertThat(VillageTagService.normalise("12345")).isEqualTo("12345");
    }

    @Test
    void aBlankTagMeansNoTag() {
        assertThat(VillageTagService.normalise("")).isNull();
        assertThat(VillageTagService.normalise("  \t ")).isNull();
        assertThat(VillageTagService.normalise(null)).isNull();
    }

    @Test
    void moreThanFiveCharactersOrAnythingButLettersAndDigitsIsRejected() {
        for (String bad : new String[] {"ABCDEF", "O FF", "OFF!", "ÄÖÜ", "off-1", "🙂", "a\nb"}) {
            assertThatThrownBy(() -> VillageTagService.normalise(bad))
                    .as(bad)
                    .isInstanceOf(ApiException.class);
        }
    }
}
