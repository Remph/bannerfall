package de.bannerfall.rules;

import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import java.util.Map;

/** How much the attackers carry home and from which resource (02-game-rules §9.3). Pure. */
public final class Loot {

    private static final ResourceType[] RESOURCES = ResourceType.values();

    private Loot() {}

    /**
     * What the surviving attackers of an attack with a Plunder banner can carry (02-game-rules
     * §21.2): {@code round(Σ count × carry × (1 + bonus))}.
     *
     * @param bonus the Plunder banner the attack took along, as a fraction; 0 without
     */
    public static int capacity(GameData data, Map<UnitType, Integer> survivors, double bonus) {
        return BannerRules.capacity(capacity(data, survivors), bonus);
    }

    /** What the surviving attackers can carry: {@code Σ count × carry}. */
    public static int capacity(GameData data, Map<UnitType, Integer> survivors) {
        int capacity = 0;
        for (var entry : survivors.entrySet()) {
            int count = entry.getValue();
            if (count < 0) {
                throw new IllegalArgumentException(
                        "negative count for " + entry.getKey() + ": " + count);
            }
            int carry = data.unit(entry.getKey()).carry();
            capacity = Math.addExact(capacity, Math.multiplyExact(count, carry));
        }
        return capacity;
    }

    /**
     * What can be taken from a village: per resource {@code max(0, stock − hidingCapacity)}.
     *
     * @param stock the village's stock rounded down ({@code Village.floorResources()}) after its
     *     catch-up to the battle time
     * @param hidingLevel the village's hiding place level
     */
    public static ResourceAmounts available(ResourceAmounts stock, int hidingLevel) {
        return availableAbove(stock, Storage.hidingCapacity(hidingLevel));
    }

    /**
     * Like {@link #available(ResourceAmounts, int)} with the hiding capacity itself, for a village
     * whose capacity a Treasury banner raises (02-game-rules §21.2).
     */
    public static ResourceAmounts availableAbove(ResourceAmounts stock, int hidden) {
        return new ResourceAmounts(
                Math.max(0, stock.wood() - hidden),
                Math.max(0, stock.clay() - hidden),
                Math.max(0, stock.iron() - hidden));
    }

    /**
     * Spreads the capacity over the resources like water filling up three glasses: every round, the
     * capacity left is shared equally (rounded down, at least 1) among the resources that still
     * have something, in the order wood, clay, iron; a resource that runs out leaves its part of
     * the share to the next round. Ends when the capacity is used up or nothing is left. So the
     * loot is {@code min(capacity, Σ available)} in total and as even as the stock allows, and a
     * remainder that can't be shared equally goes to wood first, then clay.
     *
     * <p>Example (02-game-rules §9.3): capacity 1000, available 150/1850/1850 → round 1 shares 333
     * (wood can only give 150), round 2 shares the 184 left between clay and iron → 150/425/425.
     *
     * @param capacity {@link #capacity} of the surviving attackers
     * @param available {@link #available} in the target village
     */
    public static ResourceAmounts distribute(int capacity, ResourceAmounts available) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative: " + capacity);
        }
        int[] left = new int[RESOURCES.length];
        for (int i = 0; i < RESOURCES.length; i++) {
            left[i] = available.get(RESOURCES[i]);
            if (left[i] < 0) {
                throw new IllegalArgumentException("available must not be negative: " + available);
            }
        }

        int[] loot = new int[RESOURCES.length];
        int remaining = capacity;
        while (remaining > 0) {
            int open = 0;
            for (int amount : left) {
                if (amount > 0) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            int share = Math.max(1, remaining / open);
            for (int i = 0; i < RESOURCES.length; i++) {
                if (left[i] > 0) {
                    int take = Math.min(share, Math.min(left[i], remaining));
                    loot[i] += take;
                    left[i] -= take;
                    remaining -= take;
                }
            }
        }
        return new ResourceAmounts(loot[0], loot[1], loot[2]);
    }
}
