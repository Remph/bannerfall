package de.bannerfall.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /api/offers/{offerId}/accept: accept the offer {@code count} times from the player's village
 * {@code villageId}.
 */
public record AcceptRequest(
        long villageId, @Min(1) @Max(MarketService.MAX_OFFER_COUNT) int count) {}
