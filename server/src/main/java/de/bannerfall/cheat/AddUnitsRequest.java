package de.bannerfall.cheat;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/dev/villages/{id}/units: how many units of each type to add to the home stack, e.g.
 * {@code {"units": {"SPEARMAN": 100}}}. Missing types add nothing.
 */
public record AddUnitsRequest(
        @NotNull Map<UnitType, @NotNull @Min(0) @Max(AddUnitsRequest.MAX) Integer> units) {

    /** Per type and request; keeps the counts and the population sums from overflowing. */
    static final int MAX = 100_000;
}
