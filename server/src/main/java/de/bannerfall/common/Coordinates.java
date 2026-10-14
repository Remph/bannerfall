package de.bannerfall.common;

/** A field on the map, {@code (x|y)}. */
public record Coordinates(int x, int y) {

    /** Euclidean distance in fields (02-game-rules §2). */
    public double distanceTo(Coordinates other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    @Override
    public String toString() {
        return "(" + x + "|" + y + ")";
    }
}
