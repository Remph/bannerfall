package de.bannerfall.account;

/**
 * How players log in to this world (1.5, 01-architecture §4.9a): env {@code
 * BANNERFALL_ACCOUNT_SERVER_MODE}.
 */
public enum AccountLoginMode {
    /** Only the world's own accounts (name and password), as before 1.5. The default. */
    OFF,
    /**
     * Both: the world's own accounts as before, plus the account server's button; a player with a
     * local account may link it to his account there (for a world that has players already).
     */
    LINKING,
    /** Only the account server: no local login or registration (a new world). */
    REQUIRED
}
