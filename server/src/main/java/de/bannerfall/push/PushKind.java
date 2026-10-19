package de.bannerfall.push;

/**
 * What a player can get pushed (1.5), each with its own switch; decided with the design: war, lost
 * villages and messages start on, finished builds start off.
 */
public enum PushKind {
    /** An attack on one of the player's villages was sent. */
    ATTACK(true),
    /** One of the player's villages was conquered. */
    VILLAGE_LOST(true),
    /** A private message from another player. */
    MESSAGE(true),
    /** A guild invited the player. */
    GUILD_INVITE(true),
    /** The world ended, or its victory clock started, changed hands or stopped. */
    WORLD_END(true),
    /** A village's build queue ran empty. */
    BUILDS_DONE(false);

    private final boolean defaultEnabled;

    PushKind(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}
