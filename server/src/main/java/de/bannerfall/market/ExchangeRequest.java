package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/villages/{id}/market/exchange: "get {@code amount} of {@code receive} from the market,
 * paid in {@code give}", e.g. {@code {"give": "WOOD", "receive": "IRON", "amount": 1000}} (costs
 * 1500 wood at the default ratio 1.5).
 */
public record ExchangeRequest(
        @NotNull ResourceType give,
        @NotNull ResourceType receive,
        @Min(1) @Max(MarketService.MAX_AMOUNT) int amount) {}
