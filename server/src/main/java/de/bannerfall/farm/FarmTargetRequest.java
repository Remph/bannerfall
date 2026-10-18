package de.bannerfall.farm;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/villages/{id}/farm-targets: the target and what it is sent, either one of the player's
 * templates or its own units (exactly one of the two, 1.4); the counts are checked by {@link
 * FarmService} (0 … 30 000 each, at least one unit, no nobles).
 */
public record FarmTargetRequest(
        @NotNull Coordinates target,
        @Schema(nullable = true) Map<UnitType, @NotNull Integer> units,
        @Schema(nullable = true) FarmTemplateLetter template) {}
