package de.bannerfall.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.StandardEffect;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** banners.yml (02-game-rules §21.2): the numbers of the file and the rules that guard it. */
class BannerCatalogTest {

    private static final BannerCatalog.Drops DROPS =
            new BannerCatalog.Drops(0.10, 0.015, List.of(70, 25, 5), 30, 24, 8);
    private static final BannerCatalog.Theft THEFT =
            new BannerCatalog.Theft(List.of(0.03, 0.04, 0.05), 0.85, 0.10);
    private static final BannerCatalog.Fusing FUSING = new BannerCatalog.Fusing(3);

    private static Map<BannerType, List<Double>> effects() {
        Map<BannerType, List<Double>> effects = new EnumMap<>(BannerType.class);
        for (BannerType type : BannerType.values()) {
            effects.put(type, List.of(0.05, 0.10, 0.15));
        }
        return effects;
    }

    @Test
    void theNineTypesAndTheSevenStandardsInTheOrderOfTheRulebook() {
        assertThat(BannerType.villageBanners())
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
        // the paladin standards (1.4) come after them, so no stored type changes its place
        assertThat(BannerType.standards())
                .extracting(Enum::name)
                .containsExactly(
                        "SPEAR_STANDARD",
                        "SWORD_STANDARD",
                        "AXE_STANDARD",
                        "LANCE_STANDARD",
                        "KNIGHT_STANDARD",
                        "RAM_STANDARD",
                        "SIEGE_STANDARD");
        assertThat(BannerType.values())
                .containsExactlyElementsOf(
                        Stream.concat(
                                        BannerType.villageBanners().stream(),
                                        BannerType.standards().stream())
                                .toList());
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
    void aFlyingBannerWorksAtMostAtTheHallsLevelAndSleepsWithoutOne() {
        // 02 §21.3: the battle, the simulator and the NPC estimate all take it from here
        BannerCatalog catalog = new BannerCatalog(effects(), DROPS, THEFT, FUSING);

        assertThat(catalog.flying(BannerType.BULWARK, 3, 5))
                .isEqualTo(new BannerEffect(BannerType.BULWARK, 3, 3, 0.15));
        assertThat(catalog.flying(BannerType.BULWARK, 3, 2))
                .isEqualTo(new BannerEffect(BannerType.BULWARK, 3, 2, 0.10));
        assertThat(catalog.flying(BannerType.OATH, 2, 0))
                .isEqualTo(new BannerEffect(BannerType.OATH, 2, 0, 0));
        // a standard works at the tier it is given (BannerRules.standardTier), 0 while it sleeps
        assertThat(catalog.standard(BannerType.SIEGE_STANDARD, 3, 1))
                .isEqualTo(new StandardEffect(BannerType.SIEGE_STANDARD, 3, 1, 0.05));
        assertThat(catalog.standard(BannerType.SIEGE_STANDARD, 3, 0))
                .isEqualTo(new StandardEffect(BannerType.SIEGE_STANDARD, 3, 0, 0));
    }

    @Test
    void theTheftChanceOfABannerShrinksWithTheHallAndThatOfAStandardDoesNot() {
        // 02 §21.6.2 and §21.18: 1 − (1 − perThief)^survivors, the hall takes 10 % per level
        double tenThieves = 1 - Math.pow(1 - 0.04, 10);

        assertThat(THEFT.chance(10, 0, 2)).isCloseTo(tenThieves, within(1e-12));
        assertThat(THEFT.chance(10, 3, 2)).isCloseTo(tenThieves * 0.7, within(1e-12));
        assertThat(THEFT.standardChance(10, 2)).isCloseTo(tenThieves, within(1e-12));
        assertThat(THEFT.chance(200, 0, 3)).isEqualTo(0.85);
        assertThat(THEFT.standardChance(0, 1)).isZero();
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
        var theft = new BannerCatalog.Theft(List.of(0.03), 0.85, 0.40);

        assertThatThrownBy(() -> new BannerCatalog(effects(), DROPS, theft, FUSING))
                .hasMessageContaining("hall-reduction");
    }

    @Test
    void theChancePerThiefRisesWithTheThiefLevel() {
        assertThat(THEFT.perThief(1)).isEqualTo(0.03);
        assertThat(THEFT.perThief(3)).isEqualTo(0.05);
        // a level past the list counts as its last entry
        assertThat(THEFT.perThief(4)).isEqualTo(0.05);
    }

    @Test
    void theChancesPerThiefMustAscend() {
        var theft = new BannerCatalog.Theft(List.of(0.03, 0.03), 0.85, 0.10);

        assertThatThrownBy(() -> new BannerCatalog(effects(), DROPS, theft, FUSING))
                .hasMessageContaining("chance-per-thief");
    }
}
