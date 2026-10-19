package de.bannerfall.account;

/**
 * Why a login or link through the account server didn't work (1.5): the page the player lands on
 * after it reads this with {@code GET /api/auth/external}.
 */
public enum ExternalLoginProblem {
    /** The account server said no, the player cancelled, or the answer couldn't be checked. */
    FAILED,
    /** The account server can't be reached (or the world doesn't use one). */
    UNAVAILABLE,
    /** The player is banned on this world ({@code bannedUntil}, {@code banReason}). */
    BANNED,
    /** The host deleted this player. */
    DELETED,
    /** The account server's account already belongs to another player of this world. */
    ALREADY_LINKED,
    /** Linking isn't allowed now (the mode changed, or the account is linked already). */
    LINK_NOT_ALLOWED
}
