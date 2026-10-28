package de.bannerfall.tutorial;

import static de.bannerfall.tutorial.TutorialStep.Detection.ACK;
import static de.bannerfall.tutorial.TutorialStep.Detection.STATE;
import static de.bannerfall.tutorial.TutorialStep.Kind.LESSON;
import static de.bannerfall.tutorial.TutorialStep.Kind.STEP;

/**
 * The tutorial's registry (02-game-rules §27), in the order the card works through them. A new
 * lesson or step is one more constant plus its rules in {@link TutorialRules} and its texts in the
 * web client. The key sent to the client is the constant's name.
 */
public enum TutorialStep {
    WOODCUTTER(STEP, STATE, null),
    HEADQUARTERS(STEP, STATE, WOODCUTTER),
    BARRACKS(STEP, STATE, HEADQUARTERS),
    SPEARMEN(STEP, STATE, BARRACKS),
    MAP_BARBARIAN(STEP, ACK, SPEARMEN),
    ATTACK(STEP, STATE, MAP_BARBARIAN),
    REPORT(STEP, STATE, ATTACK),
    SIMULATOR(STEP, ACK, REPORT),
    FARM_LIST(STEP, STATE, SIMULATOR),
    MARKET(STEP, STATE, null),
    GUILD(STEP, STATE, null),
    LESSON_UNIT_LEVELS(LESSON, ACK, null),
    LESSON_CONQUEST(LESSON, ACK, null),
    LESSON_AUTOMATION(LESSON, ACK, null),
    LESSON_BANNER_HALL(LESSON, ACK, null);

    /** A step is ticked off by the player; a lesson is only read. */
    public enum Kind {
        STEP,
        LESSON
    }

    /**
     * How the server learns that it is done: from the game state, or because the client says so.
     */
    public enum Detection {
        STATE,
        ACK
    }

    private final Kind kind;
    private final Detection detection;
    private final TutorialStep prerequisite;

    TutorialStep(Kind kind, Detection detection, TutorialStep prerequisite) {
        this.kind = kind;
        this.detection = detection;
        this.prerequisite = prerequisite;
    }

    public Kind kind() {
        return kind;
    }

    public Detection detection() {
        return detection;
    }

    /** The step that must be done or skipped before this one comes up; null for none. */
    public TutorialStep prerequisite() {
        return prerequisite;
    }
}
