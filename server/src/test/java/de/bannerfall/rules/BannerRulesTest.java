package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The rounding of the banner effects (02-game-rules §21.2). */
class BannerRulesTest {

    @Test
    void theHallCapsTheTierAndNoHallMeansDormant() {
        assertThat(BannerRules.effectiveTier(3, 1)).isEqualTo(1);
        assertThat(BannerRules.effectiveTier(2, 3)).isEqualTo(2);
        assertThat(BannerRules.effectiveTier(1, 3)).isEqualTo(1);
        assertThat(BannerRules.effectiveTier(3, 0)).isZero();
    }

    @Test
    void aStandardWorksUpToThePaladinLevelAndSleepsWithoutAHall() {
        assertThat(BannerRules.standardTier(3, 1, 1)).isEqualTo(1);
        assertThat(BannerRules.standardTier(2, 3, 1)).isEqualTo(2);
        assertThat(BannerRules.standardTier(3, 3, 0)).isZero();
        assertThat(BannerRules.standardTier(1, 0, 3)).isZero();
    }

    @Test
    void boostedRamsAndCatapultsAreRoundedDown() {
        assertThat(BannerRules.boosted(10, 0)).isEqualTo(10);
        assertThat(BannerRules.boosted(10, 0.10)).isEqualTo(11);
        assertThat(BannerRules.boosted(9, 0.10)).isEqualTo(9);
        assertThat(BannerRules.boosted(20, 0.30)).isEqualTo(26);
        assertThat(BannerRules.boosted(0, 0.30)).isZero();
    }

    @Test
    void aCapacityIsRoundedAfterTheBonus() {
        assertThat(BannerRules.capacity(1000, 0)).isEqualTo(1000);
        assertThat(BannerRules.capacity(1000, 0.10)).isEqualTo(1100);
        // 1229 × 1.2 = 1474.8
        assertThat(BannerRules.capacity(1229, 0.20)).isEqualTo(1475);
        // 45 × 1.1 = 49.5 rounds up
        assertThat(BannerRules.capacity(45, 0.10)).isEqualTo(50);
    }

    @Test
    void aProductionIsScaledNotRounded() {
        assertThat(BannerRules.production(30, 0)).isEqualTo(30);
        assertThat(BannerRules.production(30, 0.15))
                .isEqualTo(34.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void aTimeIsRoundedAndNeverBelowOneSecond() {
        assertThat(BannerRules.seconds(1000, 0)).isEqualTo(1000);
        assertThat(BannerRules.seconds(1000, 0.10)).isEqualTo(900);
        // 7 × 0.95 = 6.65
        assertThat(BannerRules.seconds(7, 0.05)).isEqualTo(7);
        assertThat(BannerRules.seconds(1, 0.15)).isEqualTo(1);
        assertThat(BannerRules.seconds(0, 0.15)).isEqualTo(1);
    }

    @Test
    void theTheftChanceFollowsTheRulebookTable() {
        // 02 §21.6.2, rounded to whole percent: rows 1 / 5 / 10 / 20 / 30 / 50 / 80 thieves,
        // columns hall 0 / 1 / 2 / 3
        int[] thieves = {1, 5, 10, 20, 30, 50, 80};
        int[][] percent = {
            {3, 3, 2, 2},
            {14, 13, 11, 10},
            {26, 24, 21, 18},
            {46, 41, 36, 32},
            {60, 54, 48, 42},
            {78, 70, 63, 55},
            {85, 82, 73, 64}
        };
        for (int row = 0; row < thieves.length; row++) {
            for (int hall = 0; hall <= 3; hall++) {
                assertThat(Math.round(100 * theft(thieves[row], hall)))
                        .as("%d thieves, hall %d", thieves[row], hall)
                        .isEqualTo(percent[row][hall]);
            }
        }
    }

    @Test
    void noSurvivingThiefNoChanceAndTheCapHolds() {
        assertThat(theft(0, 0)).isZero();
        assertThat(theft(500, 0)).isEqualTo(0.85);
    }

    @Test
    void theDropTierFollowsTheOdds() {
        var odds = java.util.List.of(70, 25, 5);
        assertThat(BannerRules.dropTier(0.0, odds)).isEqualTo(1);
        assertThat(BannerRules.dropTier(0.6999, odds)).isEqualTo(1);
        assertThat(BannerRules.dropTier(0.70, odds)).isEqualTo(2);
        assertThat(BannerRules.dropTier(0.9499, odds)).isEqualTo(2);
        assertThat(BannerRules.dropTier(0.95, odds)).isEqualTo(3);
        assertThat(BannerRules.dropTier(0.9999, odds)).isEqualTo(3);
        // a tier with 0 % never drops
        assertThat(BannerRules.dropTier(0.99, java.util.List.of(80, 20, 0))).isEqualTo(2);
    }

    private static double theft(int survivors, int hall) {
        return BannerRules.theftChance(survivors, hall, 0.03, 0.85, 0.10);
    }
}
