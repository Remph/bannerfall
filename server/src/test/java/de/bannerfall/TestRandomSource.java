package de.bannerfall;

import de.bannerfall.common.DefaultRandomSource;
import de.bannerfall.common.RandomSource;

/**
 * The integration tests' dice ({@code @Primary}, {@link TestRandomSourceConfiguration}): the real
 * ones unless a test fixes them with {@link #use}, e.g. {@code FixedRandomSource.constant(0.99)}
 * for the highest loyalty hit. Everything draws from it while fixed, spawns too, so fix the dice
 * after registering the players and call {@link #reset} afterwards (the context is shared).
 */
public final class TestRandomSource implements RandomSource {

    private final RandomSource real = new DefaultRandomSource();
    private volatile RandomSource fixed;

    /** From now on every draw comes from {@code dice}. */
    public void use(RandomSource dice) {
        this.fixed = dice;
    }

    /** Back to the real dice. */
    public void reset() {
        this.fixed = null;
    }

    private RandomSource current() {
        RandomSource dice = fixed;
        return dice != null ? dice : real;
    }

    @Override
    public double nextDouble() {
        return current().nextDouble();
    }

    @Override
    public int nextInt(int minIncl, int maxIncl) {
        return current().nextInt(minIncl, maxIncl);
    }
}
