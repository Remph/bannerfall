package de.bannerfall.movement;

/** What a player can send (04-api {@code CommandRequest.kind}); a RETURN is never sent directly. */
public enum CommandKind {
    ATTACK,
    SUPPORT;

    MovementKind toMovementKind() {
        return switch (this) {
            case ATTACK -> MovementKind.ATTACK;
            case SUPPORT -> MovementKind.SUPPORT;
        };
    }
}
