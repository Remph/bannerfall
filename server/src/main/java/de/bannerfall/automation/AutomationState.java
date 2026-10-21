package de.bannerfall.automation;

/** What a village's automation does right now (02-game-rules §25), for its status. */
public enum AutomationState {
    /** The player has too few villages, or the world has no automation. */
    LOCKED,
    /** No plan picked, or switched off. */
    OFF,
    /** Stopped because the owner cancelled one of its orders. */
    PAUSED,
    /** The picked plan was deleted. */
    NO_PLAN,
    /** The next level can be queued now (it is, at the next look). */
    READY,
    /** The next level waits for resources until {@code readyAt}. */
    WAITING_RESOURCES,
    /** The build queue has no normal place free, or holds two of the automation's orders. */
    WAITING_QUEUE,
    /** A warehouse or farm level the next line needs is queued. */
    WAITING_UPGRADE,
    /** The next level can't be queued by waiting: a resource isn't produced or the farm is full. */
    BLOCKED,
    /** Every line of the plan is reached (the recruit part may still run). */
    DONE
}
