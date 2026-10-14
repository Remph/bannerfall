package de.bannerfall;

import de.bannerfall.common.RandomSource;

/**
 * Dice that return the given doubles (each in [0, 1)) one after another and start over at the end,
 * so a test decides every "random" number. {@code nextInt(min, max)} maps the same values onto the
 * range.
 */
public final class FixedRandomSource implements RandomSource {

    private final double[] values;
    private int next = 0;

    private FixedRandomSource(double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("at least one value needed");
        }
        for (double value : values) {
            if (value < 0 || value >= 1) {
                throw new IllegalArgumentException("values must be in [0, 1): " + value);
            }
        }
        this.values = values;
    }

    /** Always returns {@code value}. */
    public static FixedRandomSource constant(double value) {
        return new FixedRandomSource(value);
    }

    /** Returns the values in turn, then starts again from the first. */
    public static FixedRandomSource sequence(double... values) {
        return new FixedRandomSource(values);
    }

    @Override
    public double nextDouble() {
        double value = values[next];
        next = (next + 1) % values.length;
        return value;
    }

    @Override
    public int nextInt(int minIncl, int maxIncl) {
        return minIncl + (int) Math.floor(nextDouble() * (maxIncl - minIncl + 1));
    }
}
