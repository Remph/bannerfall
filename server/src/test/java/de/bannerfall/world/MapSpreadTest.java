package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MapSpreadTest {

    @Test
    void normalIsTheSpreadOfEveryWorldBefore() {
        assertThat(MapSpread.NORMAL.factor()).isEqualTo(1.0);
        assertThat(MapSpread.NORMAL.defaultMapSize()).isEqualTo(100);
        assertThat(MapSpread.NORMAL.defaultNobleMaxDistance()).isEqualTo(70);
        assertThat(MapSpread.NORMAL.scale(15)).isEqualTo(15);
        assertThat(WorldSettings.builder().build().mapSpread()).isEqualTo(MapSpread.NORMAL);
    }

    @Test
    void thePresetsScaleTheMapAndTheNobleRange() {
        assertThat(MapSpread.CLOSE.defaultMapSize()).isEqualTo(80);
        assertThat(MapSpread.WIDE.defaultMapSize()).isEqualTo(130);
        assertThat(MapSpread.VAST.defaultMapSize()).isEqualTo(160);
        assertThat(MapSpread.CLOSE.defaultNobleMaxDistance()).isEqualTo(56);
        assertThat(MapSpread.WIDE.defaultNobleMaxDistance()).isEqualTo(91);
        assertThat(MapSpread.VAST.defaultNobleMaxDistance()).isEqualTo(112);
    }

    @Test
    void radiiAreRoundedToWholeFields() {
        // the NPC templates' radii: DEFENSIVE farm 12, BALANCED 15, OFFENSIVE conquest 25
        assertThat(MapSpread.CLOSE.scale(12)).isEqualTo(10); // 9.6
        assertThat(MapSpread.WIDE.scale(15)).isEqualTo(20); // 19.5 rounds up
        assertThat(MapSpread.VAST.scale(25)).isEqualTo(40);
    }
}
