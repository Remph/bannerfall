package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LootTest {

    private final GameData data = TestGameData.load();

    @Test
    void aPlunderBannerRaisesTheCarryCapacity() {
        GameData data = TestGameData.load();
        var spearmen = Map.of(UnitType.SPEARMAN, 40);
        assertThat(Loot.capacity(data, spearmen)).isEqualTo(1000);
        assertThat(Loot.capacity(data, spearmen, 0)).isEqualTo(1000);
        assertThat(Loot.capacity(data, spearmen, 0.30)).isEqualTo(1300);
        // 7 × 25 = 175, +10 % = 192.5 rounds up
        assertThat(Loot.capacity(data, Map.of(UnitType.SPEARMAN, 7), 0.10)).isEqualTo(193);
    }

    @Test
    void theRulebookExample() {
        // Stock 300/2000/2000, hiding place 1 protects 150 of each.
        ResourceAmounts available = Loot.available(new ResourceAmounts(300, 2000, 2000), 1);

        assertThat(available).isEqualTo(new ResourceAmounts(150, 1850, 1850));
        assertThat(Loot.distribute(1000, available)).isEqualTo(new ResourceAmounts(150, 425, 425));
    }

    @ParameterizedTest(name = "capacity {0}, available {1}/{2}/{3} → {4}/{5}/{6}")
    @CsvSource({
        "1000, 150, 1850, 1850, 150, 425, 425",
        "900, 300, 300, 300, 300, 300, 300", // evenly, no rest
        "10, 100, 100, 100, 4, 3, 3", // the rest goes to wood first
        "11, 100, 100, 100, 4, 4, 3",
        "2, 100, 100, 100, 1, 1, 0", // share at least 1
        "1, 100, 100, 100, 1, 0, 0",
        "900, 0, 1000, 100, 0, 800, 100", // iron runs out, clay takes the rest
        "10000, 100, 200, 300, 100, 200, 300", // capacity left over: everything is taken
        "0, 100, 200, 300, 0, 0, 0", // nothing to carry
        "500, 0, 0, 0, 0, 0, 0" // nothing to take
    })
    void distributeFillsEvenly(
            int capacity, int wood, int clay, int iron, int lootWood, int lootClay, int lootIron) {
        ResourceAmounts loot = Loot.distribute(capacity, new ResourceAmounts(wood, clay, iron));

        assertThat(loot).isEqualTo(new ResourceAmounts(lootWood, lootClay, lootIron));
    }

    @Test
    void lootIsTheSmallerOfCapacityAndStockAndNeverMoreThanAvailable() {
        int[] amounts = {0, 1, 2, 7, 50, 333, 1000};
        for (int capacity : new int[] {0, 1, 2, 3, 10, 100, 999, 5000}) {
            for (int wood : amounts) {
                for (int clay : amounts) {
                    for (int iron : amounts) {
                        var available = new ResourceAmounts(wood, clay, iron);

                        ResourceAmounts loot = Loot.distribute(capacity, available);

                        String what = "capacity " + capacity + ", " + available;
                        assertThat(loot.total())
                                .as(what)
                                .isEqualTo(Math.min(capacity, available.total()));
                        assertThat(loot.fitsInto(available)).as(what).isTrue();
                        for (ResourceType type : ResourceType.values()) {
                            assertThat(loot.get(type)).as(what).isNotNegative();
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "stock {0}, hiding place {1} → {2}")
    @CsvSource({
        "1000, 0, 1000", // no hiding place
        "1000, 1, 850",
        "150, 1, 0", // exactly hidden
        "100, 1, 0", // never negative
        "3000, 10, 993" // hiding place 10 protects 2007
    })
    void theHidingPlaceProtectsEachResource(int stock, int hidingLevel, int expected) {
        ResourceAmounts available =
                Loot.available(new ResourceAmounts(stock, stock, stock), hidingLevel);

        assertThat(available).isEqualTo(new ResourceAmounts(expected, expected, expected));
    }

    @Test
    void capacityIsWhatTheSurvivorsCarry() {
        // 10 × 25 + 2 × 80 + 5 × 0
        assertThat(
                        Loot.capacity(
                                data,
                                Map.of(
                                        UnitType.SPEARMAN, 10,
                                        UnitType.LIGHT_CAVALRY, 2,
                                        UnitType.RAM, 5)))
                .isEqualTo(410);
        assertThat(Loot.capacity(data, Map.of(UnitType.SCOUT, 100))).isZero();
        assertThat(Loot.capacity(data, Map.of())).isZero();
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> Loot.distribute(-1, ResourceAmounts.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Loot.distribute(10, new ResourceAmounts(5, -1, 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Loot.capacity(data, Map.of(UnitType.AXEMAN, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBiggerHidingCapacityLeavesLessToTake() {
        var stock = new ResourceAmounts(500, 150, 80);

        // hiding place level 1 hides 150; with a Treasury banner (+20 %) 180
        assertThat(Loot.availableAbove(stock, 150)).isEqualTo(Loot.available(stock, 1));
        assertThat(Loot.availableAbove(stock, 180)).isEqualTo(new ResourceAmounts(320, 0, 0));
    }
}
