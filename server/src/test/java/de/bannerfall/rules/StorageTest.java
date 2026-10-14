package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StorageTest {

    @ParameterizedTest(name = "warehouse {0} → {1}")
    @CsvSource({"1, 1000", "5, 2281", "10, 6397", "20, 50290", "30, 395371"})
    void warehouseCapacityMatchesTheRulebook(int level, int expected) {
        assertThat(Storage.warehouseCapacity(level)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "hiding place {0} → {1}")
    @CsvSource({"0, 0", "1, 150", "5, 475", "10, 2007"})
    void hidingCapacityMatchesTheRulebook(int level, int expected) {
        assertThat(Storage.hidingCapacity(level)).isEqualTo(expected);
    }

    @Test
    void warehouseNeedsLevelOne() {
        assertThatThrownBy(() -> Storage.warehouseCapacity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hidingPlaceRejectsNegativeLevels() {
        assertThatThrownBy(() -> Storage.hidingCapacity(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTreasuryBannerRaisesBothCapacitiesAndRoundsOnce() {
        // 1000 × 1.3 and 150 × 1.2 (02-game-rules §21.2)
        assertThat(Storage.warehouseCapacity(1, 0.30)).isEqualTo(1300);
        assertThat(Storage.hidingCapacity(1, 0.20)).isEqualTo(180);
        // no banner: the plain numbers, also for a hiding place that is not built
        assertThat(Storage.warehouseCapacity(5, 0)).isEqualTo(Storage.warehouseCapacity(5));
        assertThat(Storage.hidingCapacity(0, 0.30)).isZero();
    }
}
