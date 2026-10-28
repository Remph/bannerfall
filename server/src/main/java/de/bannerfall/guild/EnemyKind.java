package de.bannerfall.guild;

/** What an enemy mark points at (1.7, 02-game-rules §16.3). */
public enum EnemyKind {
    /** A whole guild: all its members and their villages. */
    GUILD,
    /** A player, also an NPC lord: all his villages. */
    PLAYER,
    /** One village, whoever owns it. */
    VILLAGE
}
