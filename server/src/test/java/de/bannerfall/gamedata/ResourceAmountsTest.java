package de.bannerfall.gamedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResourceAmountsTest {

    private final ResourceAmounts a = new ResourceAmounts(100, 200, 300);
    private final ResourceAmounts b = new ResourceAmounts(10, 20, 30);

    @Test
    void plusMinusTimes() {
        assertThat(a.plus(b)).isEqualTo(new ResourceAmounts(110, 220, 330));
        assertThat(a.minus(b)).isEqualTo(new ResourceAmounts(90, 180, 270));
        assertThat(b.times(3)).isEqualTo(new ResourceAmounts(30, 60, 90));
        assertThat(b.times(0)).isEqualTo(ResourceAmounts.ZERO);
    }

    @Test
    void minusMayGoNegative() {
        assertThat(b.minus(a)).isEqualTo(new ResourceAmounts(-90, -180, -270));
    }

    @Test
    void fitsIntoNeedsEveryResource() {
        assertThat(b.fitsInto(a)).isTrue();
        assertThat(a.fitsInto(a)).isTrue();
        assertThat(a.fitsInto(b)).isFalse();
        assertThat(new ResourceAmounts(10, 20, 301).fitsInto(a)).isFalse(); // only iron too much
    }

    @Test
    void getAndTotal() {
        assertThat(a.get(ResourceType.WOOD)).isEqualTo(100);
        assertThat(a.get(ResourceType.CLAY)).isEqualTo(200);
        assertThat(a.get(ResourceType.IRON)).isEqualTo(300);
        assertThat(a.total()).isEqualTo(600);
    }

    @Test
    void overflowFailsLoudly() {
        assertThatThrownBy(() -> new ResourceAmounts(50_000, 0, 0).times(100_000))
                .isInstanceOf(ArithmeticException.class);
    }
}
