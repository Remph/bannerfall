package de.bannerfall.world;

/** Why a world ended (v1.3, 02-game-rules §22). */
public enum EndReason {
    /** A side held the domination share for the hold time. */
    DOMINATION,
    /** A side held enough banner keeps for the hold time. */
    KEEPS,
    /** The end date came (set by the host, also "end now"): the side with the most points wins. */
    END_DATE
}
