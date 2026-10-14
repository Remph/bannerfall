package de.bannerfall.rules;

import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;

/** Resource production and lazy catch-up (02-game-rules §3). Pure functions. */
public final class Production {

    private Production() {}

    /**
     * Production per hour of one resource, not rounded: {@code L == 0 ? 5 : 30 × 1.165^(L−1)},
     * times world speed and the owner's production factor (1.0 for humans and barbarians).
     */
    public static double perHour(int level, double speed, double ownerFactor) {
        requireNotNegative(level);
        double base = level == 0 ? 5 : 30 * Math.pow(1.165, level - 1);
        return base * speed * ownerFactor;
    }

    /** {@link #perHour(int, double, double)} for a human or barbarian owner. */
    public static double perHour(int level, WorldSettings world) {
        return perHour(level, world.speed(), 1.0);
    }

    /**
     * Lazy production of one resource from {@code from} (resourcesUpdatedAt) to {@code to}. Stock
     * at or above the capacity is never reduced; otherwise production stops at the capacity. A
     * {@code to} before {@code from} produces nothing.
     */
    public static double advance(
            double stored, double perHour, double capacity, Instant from, Instant to) {
        if (stored >= capacity) {
            return stored;
        }
        double hours = Math.max(0, Duration.between(from, to).toNanos() / 3_600_000_000_000.0);
        return Math.min(capacity, stored + perHour * hours);
    }

    /** The new resourcesUpdatedAt after a catch-up to {@code to}: never moves backwards. */
    public static Instant newAnchor(Instant from, Instant to) {
        return to.isAfter(from) ? to : from;
    }

    /**
     * Incoming resources (returning loot, trade arrivals): fills up to the capacity, the excess is
     * lost, and stock above the capacity is kept as it is.
     */
    public static double addIncoming(double stored, double amount, double capacity) {
        return Math.max(stored, Math.min(capacity, stored + amount));
    }

    private static void requireNotNegative(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative: " + level);
        }
    }
}
