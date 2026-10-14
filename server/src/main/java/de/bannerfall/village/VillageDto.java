package de.bannerfall.village;

import de.bannerfall.building.BuildOrderDto;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * GET/PATCH /api/villages/{id} (04-api).
 *
 * @param loyalty rounded down
 * @param resources the raw stored amounts at {@code resources.at}; the client ticks them forward
 *     with {@code production} and caps them at {@code storageCapacity}
 * @param storageCapacity warehouse capacity per resource
 * @param hidingCapacity per resource, protected from loot
 * @param population {@code used} counts the buildings with their queued levels and all the
 *     village's units, those in training included
 * @param buildings every building with its (current) level
 * @param buildQueue the queued upgrades in running order, the first one is running
 * @param unitsHome the village's own units at home; missing types mean 0
 * @param incomingAttacks attacks on the way to this village (M7)
 * @param banner the banner flying here, with the tier it works at; null = none (M32, only the owner
 *     sees it)
 */
public record VillageDto(
        long id,
        String name,
        int x,
        int y,
        int points,
        int loyalty,
        VillageResourcesDto resources,
        ResourceRatesDto production,
        int storageCapacity,
        int hidingCapacity,
        PopulationDto population,
        Map<BuildingType, Integer> buildings,
        List<BuildOrderDto> buildQueue,
        Map<UnitType, Integer> unitsHome,
        int incomingAttacks,
        @Schema(nullable = true) VillageBannerDto banner) {

    /** Stored amounts (fractional) at the time {@code at}. */
    public record VillageResourcesDto(double wood, double clay, double iron, Instant at) {}

    /** Per hour, not rounded. */
    public record ResourceRatesDto(double wood, double clay, double iron) {}

    /** {@code used}: see {@link VillageDto#population()}. */
    public record PopulationDto(int used, int capacity) {}
}
