package de.bannerfall.gamedata;

/**
 * Whole amounts of the three resources: costs, loot capacity, trade volumes. (Village stock is
 * fractional and lives in doubles, see 02-game-rules §3.) Arithmetic fails loudly on int overflow.
 */
public record ResourceAmounts(int wood, int clay, int iron) {

    public static final ResourceAmounts ZERO = new ResourceAmounts(0, 0, 0);

    public int get(ResourceType type) {
        return switch (type) {
            case WOOD -> wood;
            case CLAY -> clay;
            case IRON -> iron;
        };
    }

    /** Sum of all three resources. */
    public int total() {
        return Math.addExact(Math.addExact(wood, clay), iron);
    }

    public ResourceAmounts plus(ResourceAmounts other) {
        return new ResourceAmounts(
                Math.addExact(wood, other.wood),
                Math.addExact(clay, other.clay),
                Math.addExact(iron, other.iron));
    }

    /** May go negative; check {@link #fitsInto} first when paying. */
    public ResourceAmounts minus(ResourceAmounts other) {
        return new ResourceAmounts(
                Math.subtractExact(wood, other.wood),
                Math.subtractExact(clay, other.clay),
                Math.subtractExact(iron, other.iron));
    }

    public ResourceAmounts times(int factor) {
        return new ResourceAmounts(
                Math.multiplyExact(wood, factor),
                Math.multiplyExact(clay, factor),
                Math.multiplyExact(iron, factor));
    }

    /** True if every resource of this is at most the one of {@code available} (affordable). */
    public boolean fitsInto(ResourceAmounts available) {
        return wood <= available.wood && clay <= available.clay && iron <= available.iron;
    }
}
