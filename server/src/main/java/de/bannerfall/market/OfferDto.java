package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A market offer (04-api <i>Market</i>): "give {@code offerAmount} {@code offerResource} for {@code
 * wantAmount} {@code wantResource}", still {@code remainingCount} times.
 *
 * @param distance fields from the village the list was asked for; null in a village's own offers
 * @param travelSeconds one way for merchants, from that village; null in a village's own offers
 */
public record OfferDto(
        long id,
        VillageRefDto village,
        ResourceType offerResource,
        int offerAmount,
        ResourceType wantResource,
        int wantAmount,
        int remainingCount,
        Instant createdAt,
        @Schema(nullable = true) Double distance,
        @Schema(nullable = true) Long travelSeconds) {

    /** One of the village's own offers. */
    static OfferDto own(TradeOffer offer) {
        return of(offer, null, null);
    }

    static OfferDto of(TradeOffer offer, Double distance, Long travelSeconds) {
        return new OfferDto(
                offer.getId(),
                VillageRefDto.of(offer.getVillage()),
                offer.getOfferResource(),
                offer.getOfferAmount(),
                offer.getWantResource(),
                offer.getWantAmount(),
                offer.getRemainingCount(),
                offer.getCreatedAt(),
                distance,
                travelSeconds);
    }
}
