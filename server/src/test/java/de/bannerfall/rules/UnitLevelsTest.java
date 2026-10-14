package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.UnitType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitLevelsTest {

    @Test
    void aMissingTypeIsLevelOne() {
        var levels = new UnitLevels(Map.of(UnitType.AXEMAN, 3));

        assertThat(levels.get(UnitType.AXEMAN)).isEqualTo(3);
        assertThat(levels.get(UnitType.SPEARMAN)).isEqualTo(1);
        assertThat(UnitLevels.NONE.get(UnitType.AXEMAN)).isEqualTo(1);
    }

    @Test
    void levelOneEntriesAreDropped() {
        var levels = new UnitLevels(Map.of(UnitType.AXEMAN, 1));

        assertThat(levels.isNone()).isTrue();
        assertThat(levels).isEqualTo(UnitLevels.NONE);
    }

    @Test
    void aLevelBelowOneIsRejected() {
        assertThatThrownBy(() -> new UnitLevels(Map.of(UnitType.AXEMAN, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
