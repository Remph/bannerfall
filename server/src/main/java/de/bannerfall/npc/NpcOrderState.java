package de.bannerfall.npc;

/** Where an order to an allied lord stands (2.1 S3, 12-solo-and-allies §8). */
public enum NpcOrderState {
    /** Given: the lord answers in its next round. */
    OPEN,
    /** The lord scouts the target first and answers once it has seen it. */
    SCOUTING,
    /** A "land together" march: it leaves at the planned time. */
    PLANNED,
    /** Carried out: on the way, standing as a support, or holding off. */
    ACTIVE,
    /** The lord said no; the answer says why. */
    REFUSED
}
