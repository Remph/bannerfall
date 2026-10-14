package de.bannerfall.farm;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** PUT /api/villages/{id}/farm-targets/{targetId}: the new template. */
public record FarmUnitsRequest(@NotNull Map<UnitType, @NotNull Integer> units) {}
