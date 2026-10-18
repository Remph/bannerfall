package de.bannerfall.farm;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** PUT /api/farm-templates/{letter}: the template's units (1.4). */
public record FarmTemplateRequest(@NotNull Map<UnitType, @NotNull Integer> units) {}
