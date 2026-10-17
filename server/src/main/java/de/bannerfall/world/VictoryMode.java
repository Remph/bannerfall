package de.bannerfall.world;

/**
 * How a world is won (v1.3, 02-game-rules §22). Whatever the mode, the host can set an end date:
 * then the side with the most points wins. Stored as text, so a new mode needs no migration.
 */
public enum VictoryMode {
    /** A side holds {@code dominationPercent} of all villages for {@code victoryHoldHours}. */
    DOMINATION,
    /**
     * A side holds {@code keepPercent} of the banner keeps (rounded up) for {@code
     * victoryHoldHours}.
     */
    KEEPS,
    /** No victory condition: only the end date (or the host) ends the world. */
    NONE
}
