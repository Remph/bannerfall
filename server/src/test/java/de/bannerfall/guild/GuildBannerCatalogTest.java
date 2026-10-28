package de.bannerfall.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.rules.GuildEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

/** guild-banner.yml (02-game-rules §16.4): the prices, the effect of each level, the guards. */
class GuildBannerCatalogTest {

    private static final GuildBannerCatalog CATALOG =
            new GuildBannerCatalog(List.of(30_000L, 90_000L, 250_000L), 0.05, 0.10, 0.05, 24);

    @Test
    void aLevelCostsItsPricePerMemberNow() {
        assertThat(CATALOG.price(1, 10)).isEqualTo(300_000);
        assertThat(CATALOG.price(2, 2)).isEqualTo(180_000);
        assertThat(CATALOG.price(3, 1)).isEqualTo(250_000);
        // a guild is never empty, but the price never drops to nothing
        assertThat(CATALOG.price(1, 0)).isEqualTo(30_000);
    }

    @Test
    void thereAreNoLevelsBeyondTheThreeOfTheLadder() {
        assertThatThrownBy(() -> CATALOG.price(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CATALOG.price(4, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachLevelSwitchesOnOneMoreEffect() {
        assertThat(CATALOG.effect(0)).isEqualTo(GuildEffect.NONE);
        assertThat(CATALOG.effect(1)).isEqualTo(new GuildEffect(0.05, 0, 0));
        assertThat(CATALOG.effect(2)).isEqualTo(new GuildEffect(0.05, 0.10, 0));
        assertThat(CATALOG.effect(3)).isEqualTo(new GuildEffect(0.05, 0.10, 0.05));
    }

    @Test
    void aBrokenFileStopsTheApplication() {
        assertThatThrownBy(() -> new GuildBannerCatalog(List.of(1L, 2L), 0.05, 0.10, 0.05, 24))
                .hasMessageContaining("3 entries");
        assertThatThrownBy(() -> new GuildBannerCatalog(List.of(1L, 0L, 3L), 0.05, 0.10, 0.05, 24))
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new GuildBannerCatalog(List.of(1L, 2L, 3L), 1.0, 0.10, 0.05, 24))
                .hasMessageContaining("below 1");
        assertThatThrownBy(() -> new GuildBannerCatalog(List.of(1L, 2L, 3L), 0.05, 0.10, 0.05, -1))
                .hasMessageContaining("lock-hours");
    }
}
