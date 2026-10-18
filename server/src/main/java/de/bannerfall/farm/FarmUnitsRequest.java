package de.bannerfall.farm;

import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * PUT /api/villages/{id}/farm-targets/{targetId}: what the entry sends from now on, one of the
 * player's templates or its own units (exactly one of the two, 1.4).
 */
public record FarmUnitsRequest(
        @Schema(nullable = true) Map<UnitType, @NotNull Integer> units,
        @Schema(nullable = true) FarmTemplateLetter template) {}
