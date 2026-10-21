package de.bannerfall.rules;

import de.bannerfall.gamedata.ResourceAmounts;

/**
 * The rising noble price (1.6, 02-game-rules §5.7). Pure functions.
 *
 * <p>A player's <b>rank</b> is the villages he owns plus the nobles he owns, minus {@link
 * #FREE_RANKS}, never below 0. The next noble costs the base price × {@code min(MAX_FACTOR, 1 +
 * step × rank)}, each resource rounded. So the first four nobles of a one-village player cost the
 * base price, and every village beyond the first and every noble beyond four makes the next one
 * dearer. The cap keeps the price inside a warehouse at level 30 (350,000 clay of 395,000). A step
 * of 0 is the flat price of before 1.6.
 */
public final class NobleRules {

    /** One village and three nobles in hand are rank 0: the first conquest costs what it did. */
    public static final int FREE_RANKS = 4;

    /** The price never goes above this many times the base price. */
    public static final double MAX_FACTOR = 7.0;

    private NobleRules() {}

    /** {@code max(0, villages + nobles − 4)}. */
    public static int rank(int villages, int nobles) {
        return rank(villages + nobles);
    }

    /** The rank of a player who holds {@code held} villages and nobles together. */
    public static int rank(int held) {
        return Math.max(0, held - FREE_RANKS);
    }

    /** {@code min(7, 1 + step × rank)}; 1 for a step of 0 or below. */
    public static double factor(double step, int rank) {
        if (step <= 0 || rank <= 0) {
            return 1.0;
        }
        return Math.min(MAX_FACTOR, 1 + step * rank);
    }

    /** The price of one noble at {@code rank}: every resource of {@code base} × the factor. */
    public static ResourceAmounts price(ResourceAmounts base, double step, int rank) {
        double factor = factor(step, rank);
        return new ResourceAmounts(
                (int) Math.round(base.wood() * factor),
                (int) Math.round(base.clay() * factor),
                (int) Math.round(base.iron() * factor));
    }

    /**
     * What one noble of an order of {@code count ≥ 1} costs for a player who holds {@code held}
     * villages and nobles: noble {@code i} (from 0) of the order is at rank {@code rank(held + i)},
     * and each pays the average of their prices, every resource rounded up. The order costs {@code
     * count} times that, so a cancel refunds exactly what each noble still in training paid, and a
     * player never pays less by ordering several at once.
     *
     * @param held villages plus nobles the player owns before this order
     */
    public static ResourceAmounts unitPrice(
            ResourceAmounts base, double step, int held, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1: " + count);
        }
        long wood = 0;
        long clay = 0;
        long iron = 0;
        for (int i = 0; i < count; i++) {
            ResourceAmounts one = price(base, step, rank(held + i));
            wood += one.wood();
            clay += one.clay();
            iron += one.iron();
        }
        return new ResourceAmounts(
                ceilDiv(wood, count), ceilDiv(clay, count), ceilDiv(iron, count));
    }

    /**
     * The most nobles the stock pays for (and the population, at {@code pop} each) for a player who
     * holds {@code held} villages and nobles, at most {@code limit}. Never negative.
     */
    public static int maxAffordable(
            ResourceAmounts base,
            double step,
            int held,
            ResourceAmounts stock,
            int freePop,
            int pop,
            int limit) {
        int max = Math.min(limit, Math.max(0, freePop) / pop);
        int count = 0;
        while (count < max
                && unitPrice(base, step, held, count + 1).times(count + 1).fitsInto(stock)) {
            count++;
        }
        return count;
    }

    private static int ceilDiv(long sum, int count) {
        return Math.toIntExact(Math.ceilDiv(sum, (long) count));
    }
}
