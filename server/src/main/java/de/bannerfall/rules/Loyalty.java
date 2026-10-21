package de.bannerfall.rules;

import de.bannerfall.common.RandomSource;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;

/** Loyalty: regeneration and noble hits (02-game-rules §10). Pure functions. */
public final class Loyalty {

    public static final double MAX = 100;

    /** A conquered village starts with this much. */
    public static final double AFTER_CONQUEST = 25;

    /** One surviving noble lowers the loyalty by a random amount in this range (inclusive). */
    public static final int MIN_HIT = 20;

    public static final int MAX_HIT = 35;

    private Loyalty() {}

    /** Regeneration per hour: {@code loyaltyRegenPerHour × speed}. */
    public static double regenPerHour(WorldSettings world) {
        return world.loyaltyRegenPerHour() * world.speed();
    }

    /**
     * Lazy regeneration from {@code from} (loyaltyUpdatedAt) to {@code to}: {@code min(100, loyalty
     * + perHour × hours)}. A {@code to} before {@code from} changes nothing.
     */
    public static double regen(double loyalty, double perHour, Instant from, Instant to) {
        double hours = Math.max(0, Duration.between(from, to).toNanos() / 3_600_000_000_000.0);
        return Math.min(MAX, loyalty + perHour * hours);
    }

    /** The new loyaltyUpdatedAt after a catch-up to {@code to}: never moves backwards. */
    public static Instant newAnchor(Instant from, Instant to) {
        return to.isAfter(from) ? to : from;
    }

    /**
     * The loyalty after {@code nobles} surviving nobles hit it: each one subtracts {@code
     * randomInt(20, 35)}, one draw per noble. The result can be 0 or below: then the village is
     * conquered ({@link #conquers}).
     */
    public static double afterNobles(double loyalty, int nobles, RandomSource random) {
        return afterNobles(loyalty, nobles, random, 0);
    }

    /**
     * Like {@link #afterNobles(double, int, RandomSource)} for a village with an Oath banner
     * (02-game-rules §21.2): each noble subtracts {@code round(randomInt(20, 35) × (1 − oath))}.
     *
     * @param oath the Oath banner of the village at battle time, as a fraction; 0 without
     */
    public static double afterNobles(double loyalty, int nobles, RandomSource random, double oath) {
        double result = loyalty;
        for (int i = 0; i < nobles; i++) {
            result -= hit(random.nextInt(MIN_HIT, MAX_HIT), oath);
        }
        return result;
    }

    /**
     * One noble's hit after the Oath banner (02-game-rules §21.2): {@code round(hit × (1 − oath))},
     * the plain hit without one. The simulator's loyalty outlook uses it for the weakest and the
     * strongest hit.
     */
    public static long hit(int hit, double oath) {
        return oath == 0 ? hit : Math.round(hit * (1 - oath));
    }

    /** At 0 or below the village changes owner. */
    public static boolean conquers(double loyalty) {
        return loyalty <= 0;
    }

    /** What the API and the reports show: rounded down, never below 0. */
    public static int shown(double loyalty) {
        return (int) Math.max(0, Math.floor(loyalty));
    }
}
