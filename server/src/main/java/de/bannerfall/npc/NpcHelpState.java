package de.bannerfall.npc;

/** Where an allied lord's help request stands (2.1 S4, 12-solo-and-allies §9). */
public enum NpcHelpState {
    /** Waiting for the human's answer. */
    OPEN,
    /** In time: the support stood there at the arrival, the shipment came before the deadline. */
    ANSWERED,
    /** "Not this time". */
    DECLINED,
    /** No answer until the deadline. */
    IGNORED,
    /** The human answered, but too late: nothing gained, nothing lost. */
    LATE,
    /** The attack turned back, the lord left the guild or lost the village. */
    CANCELLED
}
