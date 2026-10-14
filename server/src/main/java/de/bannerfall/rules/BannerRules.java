package de.bannerfall.rules;

import java.util.List;

/**
 * The rounding of the banner effects (02-game-rules §21.2, "Rounding"). They sit on top of the
 * existing formulas, which round already. Pure functions.
 */
public final class BannerRules {

    private BannerRules() {}

    /** The tier a banner works at: its own, but at most the Banner Hall's level (0 = dormant). */
    public static int effectiveTier(int tier, int hallLevel) {
        return Math.max(0, Math.min(tier, hallLevel));
    }

    /** A capacity with a bonus: {@code round(base × (1 + bonus))}. */
    public static int capacity(int base, double bonus) {
        return bonus == 0 ? base : (int) Math.round(base * (1 + bonus));
    }

    /** A production per hour with a bonus: {@code perHour × (1 + bonus)}, not rounded. */
    public static double production(double perHour, double bonus) {
        return perHour * (1 + bonus);
    }

    /** A time in seconds with a reduction: {@code max(1, round(seconds × (1 − reduction)))}. */
    public static long seconds(long seconds, double reduction) {
        return reduction == 0 ? seconds : Math.max(1, Math.round(seconds * (1 - reduction)));
    }

    /**
     * The chance that banner thieves take a village's banner (02-game-rules §21.6.2): {@code
     * min(maxChance, (1 − (1 − perThief)^k) × (1 − hallReduction × hallLevel))}. 0 without a
     * surviving thief.
     *
     * @param survivors thieves that survived the battle
     * @param hallLevel the target's Banner Hall level after the battle
     */
    public static double theftChance(
            int survivors, int hallLevel, double perThief, double maxChance, double hallReduction) {
        if (survivors <= 0) {
            return 0;
        }
        double fromThieves = 1 - Math.pow(1 - perThief, survivors);
        double hall = Math.max(0, 1 - hallReduction * Math.max(0, hallLevel));
        return Math.min(maxChance, fromThieves * hall);
    }

    /**
     * The tier of a dropped banner (02-game-rules §21.7) from one draw in [0, 1): the percentages
     * of {@code tierOdds} (tier I, II, III, adding up to 100) one after another.
     */
    public static int dropTier(double draw, List<Integer> tierOdds) {
        double point = draw * 100;
        int sum = 0;
        for (int i = 0; i < tierOdds.size(); i++) {
            sum += tierOdds.get(i);
            if (point < sum) {
                return i + 1;
            }
        }
        return tierOdds.size(); // not reached: the odds add up to 100 and draw × 100 < 100
    }
}
