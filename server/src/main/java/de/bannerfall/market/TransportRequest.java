package de.bannerfall.market;

import de.bannerfall.common.Coordinates;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/villages/{id}/transports: e.g. {@code {"target": {"x": 48, "y": 52}, "wood": 1000,
 * "clay": 0, "iron": 500}}. At least one resource must be more than 0 ({@code EMPTY_TRANSPORT}).
 */
public record TransportRequest(
        @NotNull Coordinates target,
        @Min(0) @Max(MarketService.MAX_AMOUNT) int wood,
        @Min(0) @Max(MarketService.MAX_AMOUNT) int clay,
        @Min(0) @Max(MarketService.MAX_AMOUNT) int iron) {}
