package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.UnitType;
import org.junit.jupiter.api.Test;

class StandardEffectTest {

    @Test
    void eachStandardCoversItsOwnUnits() {
        var axe = new StandardEffect(BannerType.AXE_STANDARD, 2, 2, 0.10);
        assertThat(axe.attack(UnitType.AXEMAN)).isEqualTo(0.10);
        assertThat(axe.attack(UnitType.LIGHT_CAVALRY)).isZero();
        assertThat(axe.defence(UnitType.AXEMAN)).isZero();

        var knight = new StandardEffect(BannerType.KNIGHT_STANDARD, 1, 1, 0.04);
        assertThat(knight.attack(UnitType.HEAVY_CAVALRY)).isEqualTo(0.04);
        assertThat(knight.defence(UnitType.HEAVY_CAVALRY)).isEqualTo(0.04);
        assertThat(knight.defence(UnitType.SPEARMAN)).isZero();

        assertThat(
                        new StandardEffect(BannerType.LANCE_STANDARD, 1, 1, 0.05)
                                .attack(UnitType.LIGHT_CAVALRY))
                .isEqualTo(0.05);
        assertThat(
                        new StandardEffect(BannerType.SPEAR_STANDARD, 1, 1, 0.05)
                                .defence(UnitType.SPEARMAN))
                .isEqualTo(0.05);
        assertThat(
                        new StandardEffect(BannerType.SWORD_STANDARD, 1, 1, 0.05)
                                .defence(UnitType.SWORDSMAN))
                .isEqualTo(0.05);
        var ram = new StandardEffect(BannerType.RAM_STANDARD, 3, 3, 0.30);
        assertThat(ram.rams()).isEqualTo(0.30);
        assertThat(ram.catapults()).isZero();
        assertThat(new StandardEffect(BannerType.SIEGE_STANDARD, 1, 1, 0.10).catapults())
                .isEqualTo(0.10);
    }

    @Test
    void noneDoesNothing() {
        assertThat(StandardEffect.NONE.works()).isFalse();
        for (UnitType unit : UnitType.values()) {
            assertThat(StandardEffect.NONE.attack(unit)).isZero();
            assertThat(StandardEffect.NONE.defence(unit)).isZero();
        }
        assertThat(new StandardEffect(BannerType.AXE_STANDARD, 2, 0, 0).works()).isFalse();
    }

    @Test
    void aVillageBannerIsNoStandard() {
        assertThatThrownBy(() -> new StandardEffect(BannerType.WAR, 1, 1, 0.02))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StandardEffect(BannerType.AXE_STANDARD, 1, 1, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
