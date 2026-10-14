package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/villages/{id}/offers: "give {@code offerAmount} {@code offerResource} for {@code
 * wantAmount} {@code wantResource}, {@code count} times", e.g. {@code {"offerResource": "WOOD",
 * "offerAmount": 1000, "wantResource": "IRON", "wantAmount": 800, "count": 3}}.
 */
public record OfferRequest(
        @NotNull ResourceType offerResource,
        @Min(1) @Max(MarketService.MAX_AMOUNT) int offerAmount,
        @NotNull ResourceType wantResource,
        @Min(1) @Max(MarketService.MAX_AMOUNT) int wantAmount,
        @Min(1) @Max(MarketService.MAX_OFFER_COUNT) int count) {}
