package de.bannerfall.rules;

import de.bannerfall.world.WorldSettings;

/** Merchant count, capacity and travel (02-game-rules §12). Pure functions. */
public final class Merchants {

    /** Resources one merchant carries (sum of all three). */
    public static final int CAPACITY = 1000;

    /** Merchant speed. */
    public static final double MINUTES_PER_FIELD = 6;

    private Merchants() {}

    /**
     * Merchants at market level {@code L}: {@code L ≤ 10 ? L : 10 + (L − 10)²} (0 without market).
     */
    public static int total(int marketLevel) {
        if (marketLevel < 0) {
            throw new IllegalArgumentException("level must not be negative: " + marketLevel);
        }
        return marketLevel <= 10 ? marketLevel : 10 + (marketLevel - 10) * (marketLevel - 10);
    }

    /** Merchants needed for {@code totalResources ≥ 0}: {@code ceil(total / 1000)}. */
    public static int needed(int totalResources) {
        if (totalResources < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + totalResources);
        }
        return (int) ((totalResources + (long) CAPACITY - 1) / CAPACITY);
    }

    /**
     * Merchants an offer reserves, or a transport of accepted offers needs: {@code count ×
     * ceil(offerAmount / 1000)} (every unit of the offer travels with its own merchants).
     */
    public static int forOffer(int offerAmount, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        return Math.multiplyExact(needed(offerAmount), count);
    }

    /**
     * Free merchants: {@code total − busy}, never below 0 (catapults can lower the market while
     * merchants are on the way).
     */
    public static int free(int total, int busy) {
        return Math.max(0, total - busy);
    }

    /**
     * An offer's ratio is allowed if {@code 1/maxRatio ≤ want/offer ≤ maxRatio} (both amounts &gt;
     * 0).
     */
    public static boolean ratioAllowed(int offerAmount, int wantAmount, double maxRatio) {
        if (offerAmount <= 0 || wantAmount <= 0) {
            throw new IllegalArgumentException(
                    "amounts must be positive: " + offerAmount + ", " + wantAmount);
        }
        return wantAmount <= maxRatio * offerAmount && offerAmount <= maxRatio * wantAmount;
    }

    /** One-way travel time of a transport over {@code distance} fields (travel formula of §6). */
    public static long travelSeconds(double distance, WorldSettings world) {
        return Travel.travelSeconds(distance, MINUTES_PER_FIELD, world);
    }
}
