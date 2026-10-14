package de.bannerfall.movement;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/stacks/{id}/return: which units of the support stack walk home, e.g. {@code {"units":
 * {"SPEARMAN": 50}}}. Missing types stay.
 */
public record StackReturnRequest(@NotNull Map<UnitType, @NotNull @Min(0) Integer> units) {}
