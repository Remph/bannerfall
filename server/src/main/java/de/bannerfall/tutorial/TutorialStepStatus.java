package de.bannerfall.tutorial;

/** Where a step stands for one player. */
public enum TutorialStepStatus {
    DONE,
    SKIPPED,
    /** Its turn can come: the step before it is over and the game has reached it. */
    OPEN,
    /** Not yet: the step before it is not over, or the game has not reached it. */
    UPCOMING
}
