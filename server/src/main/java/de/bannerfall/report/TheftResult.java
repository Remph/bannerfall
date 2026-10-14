package de.bannerfall.report;

/**
 * What the banner thieves of an attack did (02-game-rules §21.6.2), in the attacker's report. Only
 * {@code STOLEN} and {@code FAILED} come from a roll.
 */
public enum TheftResult {
    /** The roll succeeded: the banner is in the attacker's stash now. */
    STOLEN,
    /** The roll failed: the banner still flies in the village. */
    FAILED,
    /** The battle was won, but no banner flies in the village (barbarian villages carry none). */
    NO_BANNER,
    /** The village was conquered: its banner goes with the conquest, no roll. */
    CONQUERED,
    /** The battle was won, but no thief survived it. */
    NO_SURVIVORS,
    /** The battle was lost (or there was none): no roll. */
    BATTLE_LOST
}
