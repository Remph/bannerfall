package de.bannerfall.unit;

import de.bannerfall.gamedata.UnitType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ObjIntConsumer;

/**
 * A number of units per {@link UnitType}: one {@code int} column per type ({@code u_spearman} ...
 * {@code u_banner_thief}). Counts are never negative.
 */
@Embeddable
public class UnitCounts {

    @Column(name = "u_spearman")
    private int spearman;

    @Column(name = "u_swordsman")
    private int swordsman;

    @Column(name = "u_axeman")
    private int axeman;

    @Column(name = "u_scout")
    private int scout;

    @Column(name = "u_light_cavalry")
    private int lightCavalry;

    @Column(name = "u_heavy_cavalry")
    private int heavyCavalry;

    @Column(name = "u_ram")
    private int ram;

    @Column(name = "u_catapult")
    private int catapult;

    @Column(name = "u_noble")
    private int noble;

    @Column(name = "u_paladin")
    private int paladin;

    @Column(name = "u_banner_thief")
    private int bannerThief;

    /** No units. */
    public UnitCounts() {}

    /** Units from a map like {@code {SPEARMAN: 10}}; missing types count 0. */
    public static UnitCounts of(Map<UnitType, Integer> counts) {
        var result = new UnitCounts();
        counts.forEach(result::set);
        return result;
    }

    public int get(UnitType type) {
        return switch (type) {
            case SPEARMAN -> spearman;
            case SWORDSMAN -> swordsman;
            case AXEMAN -> axeman;
            case SCOUT -> scout;
            case LIGHT_CAVALRY -> lightCavalry;
            case HEAVY_CAVALRY -> heavyCavalry;
            case RAM -> ram;
            case CATAPULT -> catapult;
            case NOBLE -> noble;
            case PALADIN -> paladin;
            case BANNER_THIEF -> bannerThief;
        };
    }

    public void set(UnitType type, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("negative count for " + type + ": " + count);
        }
        switch (type) {
            case SPEARMAN -> spearman = count;
            case SWORDSMAN -> swordsman = count;
            case AXEMAN -> axeman = count;
            case SCOUT -> scout = count;
            case LIGHT_CAVALRY -> lightCavalry = count;
            case HEAVY_CAVALRY -> heavyCavalry = count;
            case RAM -> ram = count;
            case CATAPULT -> catapult = count;
            case NOBLE -> noble = count;
            case PALADIN -> paladin = count;
            case BANNER_THIEF -> bannerThief = count;
        }
    }

    /** Adds (or with a negative {@code delta} removes) units; the result must not be negative. */
    public void add(UnitType type, int delta) {
        set(type, Math.addExact(get(type), delta));
    }

    public boolean isEmpty() {
        for (UnitType type : UnitType.values()) {
            if (get(type) != 0) {
                return false;
            }
        }
        return true;
    }

    /** True if there are at least as many units of every type in {@code available}. */
    public boolean fitsInto(UnitCounts available) {
        for (UnitType type : UnitType.values()) {
            if (get(type) > available.get(type)) {
                return false;
            }
        }
        return true;
    }

    /** A new instance with the same counts, e.g. to hand units from one entity to another. */
    public UnitCounts copy() {
        return plus(new UnitCounts());
    }

    /** A new instance with the sum; this one is unchanged. */
    public UnitCounts plus(UnitCounts other) {
        var result = new UnitCounts();
        for (UnitType type : UnitType.values()) {
            result.set(type, Math.addExact(get(type), other.get(type)));
        }
        return result;
    }

    /**
     * A new instance with the difference; this one is unchanged. Throws if any count would go
     * negative, so check first (e.g. {@code NOT_ENOUGH_UNITS}).
     */
    public UnitCounts minus(UnitCounts other) {
        var result = new UnitCounts();
        for (UnitType type : UnitType.values()) {
            result.set(type, get(type) - other.get(type));
        }
        return result;
    }

    /** Calls {@code action} for every type with a count &gt; 0, in canonical order. */
    public void forEach(ObjIntConsumer<UnitType> action) {
        for (UnitType type : UnitType.values()) {
            int count = get(type);
            if (count > 0) {
                action.accept(type, count);
            }
        }
    }

    /** Only the types with a count &gt; 0 (the API's "missing keys mean 0"). */
    public Map<UnitType, Integer> toMap() {
        var map = new EnumMap<UnitType, Integer>(UnitType.class);
        forEach(map::put);
        return map;
    }
}
