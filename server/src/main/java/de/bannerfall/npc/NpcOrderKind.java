package de.bannerfall.npc;

/** What a human orders an allied lord to do (2.1 S3, 12-solo-and-allies §8). */
public enum NpcOrderKind {
    /** Attack a village with up to its personality's share of attack units. */
    ATTACK,
    /** Support a village of the human or of an ally. */
    SUPPORT,
    /** Send resources to a village of the human. */
    SEND,
    /** Leave a village alone: no farming, no attack, no scouting. */
    HOLD
}
