package de.bannerfall.unit;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.common.Coordinates;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnitStackTest {

    private static Village village(int x) {
        return new Village(
                null, "V" + x, new Coordinates(x, 0), 0, new BuildingLevels(), 0, Instant.EPOCH);
    }

    @Test
    void theHomeStackIsAtHome() {
        UnitStack stack = UnitStack.homeOf(village(1));

        assertThat(stack.isHome()).isTrue();
        assertThat(stack.getUnits().isEmpty()).isTrue();
    }

    @Test
    void aSupportStackIsNotAtHome() {
        assertThat(new UnitStack(village(1), village(5)).isHome()).isFalse();
    }
}
