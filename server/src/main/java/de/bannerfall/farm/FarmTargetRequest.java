package de.bannerfall.farm;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/villages/{id}/farm-targets: the target and the template; the counts are checked by
 * {@link FarmService} (0 … 30 000 each, at least one unit, no nobles).
 */
public record FarmTargetRequest(
        @NotNull Coordinates target, @NotNull Map<UnitType, @NotNull Integer> units) {}
