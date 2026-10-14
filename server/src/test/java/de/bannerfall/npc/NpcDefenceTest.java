package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.UnitType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The support budget of 09 §6.5: a village never has more than its share out in support. */
class NpcDefenceTest {

    @Test
    void theFirstSupportGetsTheWholeShare() {
        assertThat(
                        NpcDefence.withinBudget(
                                Map.of(UnitType.SPEARMAN, 50),
                                Map.of(UnitType.SPEARMAN, 100),
                                Map.of(),
                                0.5))
                .containsExactly(Map.entry(UnitType.SPEARMAN, 50));
    }

    @Test
    void unitsAlreadyOutCountAgainstTheShareOfWhatTheVillageHasInAll() {
        // 50 home, 50 already out: half of all 100 is out, nothing more may go
        assertThat(
                        NpcDefence.withinBudget(
                                Map.of(UnitType.SPEARMAN, 25),
                                Map.of(UnitType.SPEARMAN, 50),
                                Map.of(UnitType.SPEARMAN, 50),
                                0.5))
                .isEmpty();
    }

    @Test
    void aLittleBudgetLeftIsUsedAndEveryTypeCountsForItself() {
        // 60 swordsmen home, 20 out: budget 0.5 × 80 − 20 = 20, the share would be 30
        assertThat(
                        NpcDefence.withinBudget(
                                Map.of(UnitType.SWORDSMAN, 30, UnitType.SPEARMAN, 10),
                                Map.of(UnitType.SWORDSMAN, 60, UnitType.SPEARMAN, 20),
                                Map.of(UnitType.SWORDSMAN, 20),
                                0.5))
                .containsOnly(Map.entry(UnitType.SWORDSMAN, 20), Map.entry(UnitType.SPEARMAN, 10));
    }
}
