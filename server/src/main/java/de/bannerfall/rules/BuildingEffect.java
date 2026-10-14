package de.bannerfall.rules;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one level of a building changes, as a before/after pair for the Town Hall preview (M24). The
 * unit of the numbers depends on the {@link Kind}.
 *
 * @param from the value at the level the upgrade starts from; null if the building has no such
 *     effect yet (a new building)
 * @param to the value at the level the upgrade leads to
 */
public record BuildingEffect(Kind kind, @Schema(nullable = true) Double from, double to) {

    /** Which number changes; the web names and formats each one. */
    public enum Kind {
        /** Per hour and resource, with the world speed. */
        PRODUCTION_PER_HOUR,
        /** Per resource. */
        WAREHOUSE_CAPACITY,
        /** Population the farm feeds. */
        FARM_CAPACITY,
        /** Per resource protected from loot. */
        HIDING_CAPACITY,
        /** Merchants of the market. */
        MERCHANTS,
        /** Build time of new orders as a percentage of the base time (the Town Hall's factor). */
        BUILD_TIME_PERCENT,
        /** Recruit time per unit as a percentage of the time at level 1. */
        RECRUIT_TIME_PERCENT,
        /** Defence bonus of the wall in percent. */
        WALL_DEFENSE_PERCENT,
        /** Flat defence points the wall adds. */
        WALL_DEFENSE_FLAT
    }
}
