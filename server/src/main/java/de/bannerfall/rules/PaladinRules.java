package de.bannerfall.rules;

import de.bannerfall.world.WorldSettings;

/**
 * The paladin slots of a player (02-game-rules §5.5). Pure functions.
 *
 * <p>A player has one slot from the start, one more for every {@code paladinVillagesPerSlot}
 * villages he owns beyond the first (with 4: a second slot at 5 villages, a third at 9, a fourth at
 * 13, …), at most {@code paladinMaxSlots}. A world with {@code paladinMaxSlots = 0} has no paladin.
 */
public final class PaladinRules {

    private PaladinRules() {}

    /**
     * The slots for {@code villages} owned villages: {@code min(maxSlots, 1 + (villages − 1) /
     * villagesPerSlot)}, 0 if the world has no paladin or the player has no village.
     */
    public static int slots(int villages, int villagesPerSlot, int maxSlots) {
        if (villages < 1 || maxSlots < 1) {
            return 0;
        }
        return Math.min(maxSlots, 1 + (villages - 1) / Math.max(1, villagesPerSlot));
    }

    public static int slots(int villages, WorldSettings world) {
        return slots(villages, world.paladinVillagesPerSlot(), world.paladinMaxSlots());
    }

    /**
     * The number of villages at which the next slot opens, or {@code -1} if the player is at the
     * maximum (or the world has no paladin).
     */
    public static int nextSlotAtVillages(int villages, WorldSettings world) {
        int current = slots(villages, world);
        if (world.paladinMaxSlots() < 1 || current >= world.paladinMaxSlots()) {
            return -1;
        }
        return 1 + current * Math.max(1, world.paladinVillagesPerSlot());
    }
}
