package de.bannerfall.movement;

/** What a movement does when it arrives (02-game-rules §6). Stored as text. */
public enum MovementKind {
    /** Fights on arrival (a battle, or a conquest with surviving nobles). */
    ATTACK,
    /** Joins (or creates) the stack of its home village in the target village. */
    SUPPORT,
    /** Units walking home, maybe with loot: after a battle, a cancel, a withdraw or a send back. */
    RETURN
}
