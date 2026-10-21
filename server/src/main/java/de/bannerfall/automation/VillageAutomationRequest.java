package de.bannerfall.automation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/villages/{id}/automation (1.6).
 *
 * @param plan the plan the village works through; null drops the automation
 * @param enabled switched on (needs a plan)
 */
public record VillageAutomationRequest(
        @Schema(nullable = true) BuildPlanLetter plan, @NotNull Boolean enabled) {}
