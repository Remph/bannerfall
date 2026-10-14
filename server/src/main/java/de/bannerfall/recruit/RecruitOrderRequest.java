package de.bannerfall.recruit;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.RecruitRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/villages/{id}/recruit-orders: which unit and how many. The building follows from the
 * unit.
 *
 * @param count 1 … {@link RecruitRules#MAX_COUNT}
 */
public record RecruitOrderRequest(
        @NotNull UnitType unit, @NotNull @Min(1) @Max(RecruitRules.MAX_COUNT) Integer count) {}
