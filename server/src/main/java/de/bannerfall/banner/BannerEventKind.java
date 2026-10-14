package de.bannerfall.banner;

/**
 * What happened to a banner (02-game-rules §21.9, {@code banner_event.kind}). The log serves the
 * drop limits and the achievements of the battle milestones and tells the host what became of a
 * banner. M32a writes the kinds that need no battle.
 */
public enum BannerEventKind {
    FOUND,
    GRANTED,
    EQUIPPED,
    UNEQUIPPED,
    /** Taken by a banner thief (M32b). */
    STOLEN,
    /** Went with a conquered village to the conqueror's stash. */
    CONQUERED,
    FUSED,
    /** Gone for good: the village turned barbarian, or the host took it away. */
    DESTROYED
}
