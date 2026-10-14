package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.world.WorldSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The paladin slots (02-game-rules §5.5). */
class PaladinRulesTest {

    @ParameterizedTest(name = "{0} villages, {1} per slot, max {2} → {3} slots")
    @CsvSource({
        // The example from the M31 request: one at the start, the second at 5 villages, then 9, 13,
        // …
        "1, 4, 5, 1",
        "4, 4, 5, 1",
        "5, 4, 5, 2",
        "8, 4, 5, 2",
        "9, 4, 5, 3",
        "13, 4, 5, 4",
        "17, 4, 5, 5",
        "100, 4, 5, 5", // the maximum
        "50, 1, 5, 5", // one slot per village
        "2, 1, 5, 2",
        "3, 10, 5, 1",
        "11, 10, 5, 2",
        "9, 4, 2, 2", // a lower maximum wins
        "9, 4, 1, 1",
        "9, 4, 0, 0", // 0 = no paladin in this world
        "0, 4, 5, 0" // no village, no slot
    })
    void slotsFollowTheVillageCount(int villages, int perSlot, int max, int expected) {
        assertThat(PaladinRules.slots(villages, perSlot, max)).isEqualTo(expected);
    }

    @Test
    void theWorldSettingsAreUsed() {
        var world = WorldSettings.defaults();

        // defaults: every 4 villages, at most 5
        assertThat(PaladinRules.slots(1, world)).isEqualTo(1);
        assertThat(PaladinRules.slots(5, world)).isEqualTo(2);
        assertThat(PaladinRules.slots(17, world)).isEqualTo(5);
        assertThat(PaladinRules.slots(5, world.withPaladin(10, 3))).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} villages → next slot at {1}")
    @CsvSource({"1, 5", "4, 5", "5, 9", "8, 9", "9, 13", "13, 17", "17, -1", "40, -1"})
    void theNextSlotOpensAt(int villages, int expected) {
        assertThat(PaladinRules.nextSlotAtVillages(villages, WorldSettings.defaults()))
                .isEqualTo(expected);
    }

    @Test
    void thereIsNoNextSlotWithoutPaladins() {
        assertThat(PaladinRules.nextSlotAtVillages(3, WorldSettings.defaults().withPaladin(4, 0)))
                .isEqualTo(-1);
    }
}
