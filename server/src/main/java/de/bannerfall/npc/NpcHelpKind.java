package de.bannerfall.npc;

/** What an allied lord's help request asks for (2.1 S4, 12-solo-and-allies §9). */
public enum NpcHelpKind {
    /** Defenders before an attack the lord can't hold lands. */
    SUPPORT,
    /** Resources a shortage has held its next step (a noble, the wall) for. */
    RESOURCES
}
