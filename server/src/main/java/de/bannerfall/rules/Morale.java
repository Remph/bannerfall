package de.bannerfall.rules;

/**
 * Morale (1.6, 02-game-rules §8.2): an attack on a much smaller player hits with less strength. The
 * attacker's {@code A} is multiplied by {@code 0.3 + 3 × defender points / attacker points} (player
 * points), never above 1 and never below the world's floor, rounded to a whole percent. The defence
 * never changes, so a smaller player gains nothing by attacking a bigger one. Pure value, built
 * from the world settings.
 *
 * @param enabled the world's switch; off means 1 for every battle
 * @param floor the lowest morale, from {@link #MIN_FLOOR} to 1
 */
public record Morale(boolean enabled, double floor) {

    /** The lowest floor a host can set, the Tribal Wars one. */
    public static final double MIN_FLOOR = 0.3;

    /** Morale at a ratio of 0 (before the floor). */
    static final double BASE = 0.3;

    /** How fast morale climbs with the ratio: full morale from a ratio of 0.7 / 3 ≈ 0.233. */
    static final double SLOPE = 3;

    /** A world without morale. */
    public static final Morale OFF = new Morale(false, MIN_FLOOR);

    public Morale {
        if (!(floor >= MIN_FLOOR && floor <= 1)) {
            throw new IllegalArgumentException("floor must be in [0.3, 1]: " + floor);
        }
    }

    /**
     * The attacker's morale against a village of another player, from both players' points at the
     * arrival; 1 with morale off.
     *
     * @param attackerPoints the attacking player's points, at least 0
     * @param defenderPoints the points of the target village's owner, at least 0
     */
    public double factor(int attackerPoints, int defenderPoints) {
        if (attackerPoints < 0 || defenderPoints < 0) {
            throw new IllegalArgumentException(
                    "points must not be negative: " + attackerPoints + ", " + defenderPoints);
        }
        if (!enabled || defenderPoints >= attackerPoints) {
            return 1;
        }
        double raw = BASE + SLOPE * defenderPoints / attackerPoints;
        // a whole percent, as reports and the simulator show it
        double percent = Math.round(Math.min(1, raw) * 100) / 100.0;
        return Math.max(floor, percent);
    }

    /**
     * Like {@link #factor(int, int)}, 1 for a barbarian village (no owner, so no points to
     * compare).
     */
    public double factor(int attackerPoints, int defenderPoints, boolean owned) {
        return owned ? factor(attackerPoints, defenderPoints) : 1;
    }
}
