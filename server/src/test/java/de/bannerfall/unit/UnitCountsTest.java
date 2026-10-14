package de.bannerfall.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitCountsTest {

    @Test
    void everyTypeHasItsOwnField() {
        var counts = new UnitCounts();
        UnitType[] types = UnitType.values();
        for (int i = 0; i < types.length; i++) {
            counts.set(types[i], (i + 1) * 10);
        }

        for (int i = 0; i < types.length; i++) {
            assertThat(counts.get(types[i])).as(types[i].name()).isEqualTo((i + 1) * 10);
        }
    }

    @Test
    void newCountsAreEmpty() {
        var counts = new UnitCounts();

        assertThat(counts.isEmpty()).isTrue();
        assertThat(counts.toMap()).isEmpty();
    }

    @Test
    void addChangesOneTypeAndNeverGoesNegative() {
        var counts = UnitCounts.of(Map.of(UnitType.SPEARMAN, 10));

        counts.add(UnitType.SPEARMAN, -4);
        assertThat(counts.get(UnitType.SPEARMAN)).isEqualTo(6);
        assertThat(counts.isEmpty()).isFalse();

        assertThatThrownBy(() -> counts.add(UnitType.SPEARMAN, -7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(counts.get(UnitType.SPEARMAN)).isEqualTo(6);
    }

    @Test
    void plusAndMinusReturnNewCounts() {
        var a = UnitCounts.of(Map.of(UnitType.SPEARMAN, 10, UnitType.RAM, 2));
        var b = UnitCounts.of(Map.of(UnitType.SPEARMAN, 3, UnitType.NOBLE, 1));

        assertThat(a.plus(b).toMap())
                .isEqualTo(Map.of(UnitType.SPEARMAN, 13, UnitType.RAM, 2, UnitType.NOBLE, 1));
        assertThat(a.minus(UnitCounts.of(Map.of(UnitType.SPEARMAN, 3))).toMap())
                .isEqualTo(Map.of(UnitType.SPEARMAN, 7, UnitType.RAM, 2));
        // unchanged
        assertThat(a.toMap()).isEqualTo(Map.of(UnitType.SPEARMAN, 10, UnitType.RAM, 2));
    }

    @Test
    void minusBelowZeroIsRejected() {
        var a = UnitCounts.of(Map.of(UnitType.SPEARMAN, 1));
        var b = UnitCounts.of(Map.of(UnitType.SPEARMAN, 1, UnitType.AXEMAN, 1));

        assertThatThrownBy(() -> a.minus(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fitsIntoComparesEveryType() {
        var home = UnitCounts.of(Map.of(UnitType.SPEARMAN, 10, UnitType.AXEMAN, 5));

        assertThat(UnitCounts.of(Map.of(UnitType.SPEARMAN, 10)).fitsInto(home)).isTrue();
        assertThat(new UnitCounts().fitsInto(home)).isTrue();
        assertThat(UnitCounts.of(Map.of(UnitType.AXEMAN, 6)).fitsInto(home)).isFalse();
        assertThat(UnitCounts.of(Map.of(UnitType.NOBLE, 1)).fitsInto(home)).isFalse();
    }

    @Test
    void aCopyIsIndependent() {
        var original = UnitCounts.of(Map.of(UnitType.SPEARMAN, 10));
        var copy = original.copy();

        copy.add(UnitType.SPEARMAN, 5);

        assertThat(original.get(UnitType.SPEARMAN)).isEqualTo(10);
        assertThat(copy.get(UnitType.SPEARMAN)).isEqualTo(15);
    }

    @Test
    void forEachAndToMapSkipZeroCountsInCanonicalOrder() {
        var counts = UnitCounts.of(Map.of(UnitType.NOBLE, 1, UnitType.SPEARMAN, 5));
        List<String> seen = new ArrayList<>();

        counts.forEach((type, count) -> seen.add(type + "=" + count));

        assertThat(seen).containsExactly("SPEARMAN=5", "NOBLE=1");
        assertThat(counts.toMap().keySet()).containsExactly(UnitType.SPEARMAN, UnitType.NOBLE);
    }
}
