package de.bannerfall.npc;

/** What moved a lord's trust (12 §5). */
public enum TrustReason {
    /** A support of the human's stood in the lord's village when a foreign attack arrived. */
    SUPPORT,
    /** The human's merchants delivered resources to the lord. */
    SHIPPED,
    /** The human beat somebody the lord holds a grudge against. */
    GRUDGE_WIN,
    /** The human attacked the lord. */
    ATTACKED,
    /** The human scouted the lord. */
    SCOUTED,
    /** The human attacked one of the lord's guild mates. */
    MATE_ATTACKED,
    /** The human conquered one of the lord's villages: straight to {@link NpcTrustRules#MIN}. */
    CONQUERED,
    /** The human answered the lord's help request in time (2.1 S4). */
    HELP_ANSWERED,
    /** The human said "Not this time" to the lord's help request (S4). */
    HELP_DECLINED,
    /** The human left the lord's help request unanswered until its deadline (S4). */
    HELP_IGNORED
}
