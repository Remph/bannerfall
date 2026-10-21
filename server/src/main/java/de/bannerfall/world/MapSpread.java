package de.bannerfall.world;

/**
 * How far apart the sides start (1.6, 02-game-rules §2): one factor for every distance on the map
 * that new villages and NPC lords use (the spawn radii, the barbarian and keep rings, the lords'
 * farm, conquest and guild radii). {@link #NORMAL} is the spread of every world before 1.6. Stored
 * as text; fixed once the world is open, like {@code mapSize}.
 */
public enum MapSpread {
    /** Neighbours about 3.5 fields apart. */
    CLOSE(0.8),
    /** About 4.4 fields: the spread before 1.6. */
    NORMAL(1.0),
    /** About 5.8 fields. */
    WIDE(1.3),
    /** About 7.1 fields. */
    VAST(1.6);

    /** The map size and noble range of a {@link #NORMAL} world, the defaults before 1.6. */
    static final int NORMAL_MAP_SIZE = 100;

    static final int NORMAL_NOBLE_MAX_DISTANCE = 70;

    private final double factor;

    MapSpread(double factor) {
        this.factor = factor;
    }

    /** What every distance is multiplied by. */
    public double factor() {
        return factor;
    }

    /** {@code fields × factor}, rounded: a whole-field radius for this spread. */
    public int scale(int fields) {
        return (int) Math.round(fields * factor);
    }

    /** The map size that goes with this spread (80, 100, 130, 160). */
    public int defaultMapSize() {
        return scale(NORMAL_MAP_SIZE);
    }

    /** The noble range that goes with this spread (56, 70, 91, 112). */
    public int defaultNobleMaxDistance() {
        return scale(NORMAL_NOBLE_MAX_DISTANCE);
    }
}
