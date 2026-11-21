package de.bannerfall.chronicle;

/** What a moment of the chronicle is (2.1 I1, 02-game-rules §32). */
public enum ChronicleKind {
    /**
     * A village changed hands ({@code village}, {@code from} or null for barbarians, {@code to}).
     */
    CONQUEST,
    /**
     * The conquests of a day that are no turning point of their own, in one line ({@code count},
     * the day's busiest conqueror {@code to} with {@code topCount}); worked out on read.
     */
    CONQUESTS,
    /** A guild with a human in it was founded ({@code side}, by {@code player}). */
    GUILD_FOUNDED,
    /** A guild with a human in it ended ({@code side}). */
    GUILD_DISBANDED,
    /** A guild marked another guild as its enemy ({@code side} marked {@code other}). */
    WAR,
    /** An NPC lord joined a human's guild as an ally ({@code lord}, {@code side}). */
    ALLY_JOINED,
    /** An ally left ({@code lord}, {@code side}; {@code attacked} when the human attacked it). */
    ALLY_LEFT,
    /** An ally marched on an order ({@code lord} for {@code player} on {@code village}). */
    ORDER_ATTACK,
    /** A help request answered in time ({@code lord}, {@code player}, {@code village}). */
    HELP_ANSWERED,
    /** A help request left without an answer ({@code lord}, {@code player}, {@code village}). */
    HELP_IGNORED,
    /** A lord guild became the court of a solo world ({@code side}, before it {@code other}). */
    COURT,
    /** Another side took the lead in points ({@code side}); worked out on read from the frames. */
    LEAD,
    /** A side started the victory clock ({@code side}, wins at {@code winsAt}). */
    VICTORY_CLOCK,
    /** The world ended ({@code reason}, the winner {@code side} or null). */
    WORLD_ENDED
}
