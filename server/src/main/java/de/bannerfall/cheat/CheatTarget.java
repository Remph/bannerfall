package de.bannerfall.cheat;

/**
 * The village a cheat works on, and whose it must be.
 *
 * @param playerId the owner the village must have (dev cheats: the caller); null for any village
 *     (host cheats)
 */
public record CheatTarget(long villageId, Long playerId) {

    /** A village of this player; someone else's village is {@code 404 VILLAGE_NOT_FOUND}. */
    public static CheatTarget own(long villageId, long playerId) {
        return new CheatTarget(villageId, playerId);
    }

    /** Any village, whoever owns it (barbarian villages too). */
    public static CheatTarget any(long villageId) {
        return new CheatTarget(villageId, null);
    }
}
