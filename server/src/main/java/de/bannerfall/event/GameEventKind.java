package de.bannerfall.event;

/**
 * What an event does when it is due. Every kind in use has exactly one {@link GameEventHandler}
 * (01-architecture §4.6); stored as text, so adding a kind needs no migration.
 */
public enum GameEventKind {
    /** {@code refId} = build_order (M4b). */
    BUILD_COMPLETE,
    /** {@code refId} = movement (M7a). */
    MOVEMENT_ARRIVE,
    /** {@code refId} = trade_transport (M13a). */
    TRADE_ARRIVE,
    /** No {@code refId}: grows all barbarian villages and schedules the next round (M10). */
    BARBARIAN_GROWTH,
    /** {@code refId} = player (NPC lord): one decision round (M12). */
    NPC_THINK,
    /**
     * {@code refId} = 1 (the world): the pre-game ends, the world's content is created and every
     * registered player gets a village (M14g).
     */
    WORLD_OPEN,
    /**
     * No {@code refId}: checks the victory condition every few minutes and schedules the next check
     * (v1.3, 02-game-rules §22).
     */
    VICTORY_CHECK,
    /** {@code refId} = 1 (the world): the end date the host set has come (v1.3, §22). */
    WORLD_END,
    /**
     * {@code refId} = village: the village automation looks whether it can queue the next line of
     * its build plan or recruit, and schedules its next look (1.6, 02-game-rules §25).
     */
    AUTOMATION_CHECK,
    /**
     * No {@code refId}: pushes "storage full" for the villages of players who want it, and
     * schedules the next look (1.6, 01-architecture §4.8b).
     */
    STORAGE_CHECK,
    /**
     * Only for the event engine's own tests, which register a handler for it. The game has none: a
     * pending TEST event in a real database stops the server at startup.
     */
    TEST,
    /**
     * Only for the event engine's own tests: a kind that never gets a handler, for "no handler"
     * cases (before M7a they used a real kind whose handler didn't exist yet).
     */
    TEST_UNHANDLED
}
