package de.bannerfall.common;

/**
 * Every random number in game logic comes from here (never {@code Math.random()} or {@code new
 * Random()}), so tests can fix the dice with {@code FixedRandomSource}.
 */
public interface RandomSource {

    /** Uniform in [0, 1). */
    double nextDouble();

    /** Uniform integer in [minIncl, maxIncl]. */
    int nextInt(int minIncl, int maxIncl);

    /** Uniform in [minIncl, maxExcl): {@code min + (max − min) × nextDouble()}. One draw. */
    default double nextDouble(double minIncl, double maxExcl) {
        return minIncl + (maxExcl - minIncl) * nextDouble();
    }
}
