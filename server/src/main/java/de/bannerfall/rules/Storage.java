package de.bannerfall.rules;

/** Warehouse and hiding place capacities (02-game-rules §3). Pure functions. */
public final class Storage {

    private Storage() {}

    /**
     * Capacity per resource of a warehouse at {@code level ≥ 1}: {@code round(1000 × 1.229^(L−1))}.
     */
    public static int warehouseCapacity(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("warehouse level must be at least 1: " + level);
        }
        return (int) Math.round(1000 * Math.pow(1.229, level - 1));
    }

    /** Warehouse capacity with a Treasury banner's bonus (02-game-rules §21.2). */
    public static int warehouseCapacity(int level, double bannerBonus) {
        return BannerRules.capacity(warehouseCapacity(level), bannerBonus);
    }

    /** Hiding place capacity with a Treasury banner's bonus (02-game-rules §21.2). */
    public static int hidingCapacity(int level, double bannerBonus) {
        return BannerRules.capacity(hidingCapacity(level), bannerBonus);
    }

    /** Per resource protected from loot: {@code L == 0 ? 0 : round(150 × 1.334^(L−1))}. */
    public static int hidingCapacity(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative: " + level);
        }
        return level == 0 ? 0 : (int) Math.round(150 * Math.pow(1.334, level - 1));
    }
}
