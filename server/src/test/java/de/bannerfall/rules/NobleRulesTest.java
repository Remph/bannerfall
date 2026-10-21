package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.ResourceAmounts;
import org.junit.jupiter.api.Test;

/** The rising noble price (02-game-rules §5.7). */
class NobleRulesTest {

    private static final ResourceAmounts BASE = new ResourceAmounts(40_000, 50_000, 50_000);

    @Test
    void theFirstFourNoblesOfAOneVillagePlayerAreRankZero() {
        // one village, nobles 0 … 3 in hand: the next is the 1st … 4th
        for (int nobles = 0; nobles <= 3; nobles++) {
            assertThat(NobleRules.rank(1, nobles)).isZero();
        }
        // a fifth noble, or a second village with three nobles left after the conquest
        assertThat(NobleRules.rank(1, 4)).isEqualTo(1);
        assertThat(NobleRules.rank(2, 3)).isEqualTo(1);
        assertThat(NobleRules.rank(10, 3)).isEqualTo(9);
        // a player without a village and nobles is never below 0
        assertThat(NobleRules.rank(0, 0)).isZero();
    }

    @Test
    void everyRankAddsTheStepUpToSevenTimes() {
        assertThat(NobleRules.factor(0.25, 0)).isEqualTo(1.0);
        assertThat(NobleRules.factor(0.25, 1)).isEqualTo(1.25);
        assertThat(NobleRules.factor(0.25, 4)).isEqualTo(2.0);
        assertThat(NobleRules.factor(0.25, 24)).isEqualTo(7.0);
        assertThat(NobleRules.factor(0.25, 100)).isEqualTo(7.0);
        assertThat(NobleRules.factor(0.5, 13)).isEqualTo(7.0);
        // step 0 is the flat price of before 1.6
        assertThat(NobleRules.factor(0, 50)).isEqualTo(1.0);
    }

    @Test
    void thePriceIsTheBaseTimesTheFactorRounded() {
        assertThat(NobleRules.price(BASE, 0.25, 0)).isEqualTo(BASE);
        assertThat(NobleRules.price(BASE, 0.25, 1))
                .isEqualTo(new ResourceAmounts(50_000, 62_500, 62_500));
        assertThat(NobleRules.price(BASE, 0.25, 100))
                .isEqualTo(new ResourceAmounts(280_000, 350_000, 350_000));
        assertThat(NobleRules.price(new ResourceAmounts(3, 5, 7), 0.25, 1))
                .isEqualTo(new ResourceAmounts(4, 6, 9));
        // the highest price still fits a warehouse at level 30
        assertThat(NobleRules.price(BASE, 1, 100).clay())
                .isLessThanOrEqualTo(Storage.warehouseCapacity(30));
    }

    @Test
    void anOrderOfSeveralPaysTheAverageOfTheirPricesRoundedUp() {
        // a fresh player (one village, no noble): his first four are all at rank 0
        assertThat(NobleRules.unitPrice(BASE, 0.25, 1, 4)).isEqualTo(BASE);
        // a fifth in the same order is rank 1: 4 × 50,000 + 62,500 clay over five
        assertThat(NobleRules.unitPrice(BASE, 0.25, 1, 5).clay()).isEqualTo(52_500);
        // ranks 1 and 2 (five held): 1.25 and 1.5, average 1.375
        assertThat(NobleRules.unitPrice(BASE, 0.25, 5, 2))
                .isEqualTo(new ResourceAmounts(55_000, 68_750, 68_750));
        // never less than one by one
        ResourceAmounts oneByOne =
                NobleRules.price(BASE, 0.25, 3)
                        .plus(NobleRules.price(BASE, 0.25, 4))
                        .plus(NobleRules.price(BASE, 0.25, 5));
        ResourceAmounts together = NobleRules.unitPrice(BASE, 0.25, 7, 3).times(3);
        assertThat(oneByOne.fitsInto(together)).isTrue();
        // an odd sum is rounded up per noble: ranks 0 and 1 at step 0.5 are 1 and 2 (1.5 rounded)
        assertThat(NobleRules.unitPrice(new ResourceAmounts(1, 1, 1), 0.5, 4, 2))
                .isEqualTo(new ResourceAmounts(2, 2, 2));
        assertThatThrownBy(() -> NobleRules.unitPrice(BASE, 0.25, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMostAffordableCountsTheRisingPrice() {
        ResourceAmounts stock = new ResourceAmounts(200_000, 200_000, 200_000);
        // a fresh player: four at the base price take 200,000 clay and iron each
        assertThat(NobleRules.maxAffordable(BASE, 0.25, 1, stock, 10_000, 100, 30_000))
                .isEqualTo(4);
        // rank 1 (five held): 62,500 + 75,000 = 137,500 clay for two, a third costs 87,500 more
        assertThat(NobleRules.maxAffordable(BASE, 0.25, 5, stock, 10_000, 100, 30_000))
                .isEqualTo(2);
        // the population and the limit cap it too
        assertThat(NobleRules.maxAffordable(BASE, 0.25, 1, stock, 250, 100, 30_000)).isEqualTo(2);
        assertThat(NobleRules.maxAffordable(BASE, 0.25, 1, stock, 10_000, 100, 1)).isEqualTo(1);
        assertThat(NobleRules.maxAffordable(BASE, 0.25, 1, stock, -5, 100, 30_000)).isZero();
    }
}
