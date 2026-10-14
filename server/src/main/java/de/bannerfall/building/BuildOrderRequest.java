package de.bannerfall.building;

import de.bannerfall.gamedata.BuildingType;
import jakarta.validation.constraints.NotNull;

/** POST /api/villages/{id}/build-orders: which building to upgrade by one level. */
public record BuildOrderRequest(@NotNull BuildingType building) {}
