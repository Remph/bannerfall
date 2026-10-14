package de.bannerfall.npc;

/** How an NPC lord plays (09-npc-lords §2). Only the admin sees it. */
public enum NpcPersonality {
    DEFENSIVE,
    OFFENSIVE,
    BALANCED;

    /** The personality of the n-th lord ever spawned, counting from 0 (09 §2). */
    public static NpcPersonality ofNth(long n) {
        NpcPersonality[] order = {BALANCED, OFFENSIVE, DEFENSIVE};
        return order[(int) (n % order.length)];
    }
}
