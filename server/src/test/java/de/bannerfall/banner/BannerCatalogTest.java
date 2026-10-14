package de.bannerfall.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BannerType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** banners.yml (02-game-rules §21.2): the numbers of the file and the rules that guard it. */
class BannerCatalogTest {

    private static final BannerCatalog.Drops DROPS =
            new BannerCatalog.Drops(0.10, 0.015, List.of(70, 25, 5), 30, 24, 8);
    private static final BannerCatalog.Theft THEFT = new BannerCatalog.Theft(0.03, 0.85, 0.10);
    private static final BannerCatalog.Fusing FUSING = new BannerCatalog.Fusing(3);

    private static Map<BannerType, List<Double>> effects() {
        Map<BannerType, List<Double>> effects = new EnumMap<>(BannerType.class);
        for (BannerType type : BannerType.values()) {
            effects.put(type, List.of(0.05, 0.10, 0.15));
        }
        return effects;
    }

    @Test
    void theNineTypesInTheOrderOfTheRulebook() {
        assertThat(BannerType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "HARVEST",
                        "BUILDERS",
                        "DRILL",
                        "WAR",
                        "BULWARK",
                        "HASTE",
                        "TREASURY",
                        "PLUNDER",
                        "OATH");
    }

    @Test
    void anEffectIsLookedUpByTypeAndTier() {
        BannerCatalog catalog = new BannerCatalog(effects(), DROPS, THEFT, FUSING);

        assertThat(catalog.effect(BannerType.HARVEST, 1)).isEqualTo(0.05);
        assertThat(catalog.effect(BannerType.OATH, 3)).isEqualTo(0.15);
        assertThatThrownBy(() -> catalog.effect(BannerType.HARVEST, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.effect(BannerType.HARVEST, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTypeWithoutThreeTiersIsRejected() {
        Map<BannerType, List<Double>> broken = effects();
        broken.put(BannerType.WAR, List.of(0.03, 0.06));

        assertThatThrownBy(() -> new BannerCatalog(broken, DROPS, THEFT, FUSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("effects.WAR");
    }

    @Test
    void aMissingTypeIsRejected() {
        Map<BannerType, List<Double>> broken = effects();
        broken.remove(BannerType.OATH);

        assertThatThrownBy(() -> new BannerCatalog(broken, DROPS, THEFT, FUSING))
                .hasMessageContaining("effects.OATH");
    }

    @Test
    void tiersMustAscendAndStayBelowOneHundredPercent() {
        Map<BannerType, List<Double>> flat = effects();
        flat.put(BannerType.HASTE, List.of(0.10, 0.10, 0.15));
        Map<BannerType, List<Double>> whole = effects();
        whole.put(BannerType.DRILL, List.of(0.05, 0.10, 1.0));

        assertThatThrownBy(() -> new BannerCatalog(flat, DROPS, THEFT, FUSING))
                .hasMessageContaining("effects.HASTE");
        assertThatThrownBy(() -> new BannerCatalog(whole, DROPS, THEFT, FUSING))
                .hasMessageContaining("effects.DRILL");
    }

    @Test
    void theTierOddsMustAddUpToOneHundred() {
        var odds = new BannerCatalog.Drops(0.10, 0.015, List.of(70, 25, 6), 30, 24, 8);

        assertThatThrownBy(() -> new BannerCatalog(effects(), odds, THEFT, FUSING))
                .hasMessageContaining("tier-odds");
    }

    @Test
    void theHallMustLeaveSomeChanceOfTheft() {
        var theft = new BannerCatalog.Theft(0.03, 0.85, 0.40);

        assertThatThrownBy(() -> new BannerCatalog(effects(), DROPS, theft, FUSING))
                .hasMessageContaining("hall-reduction");
    }
}
