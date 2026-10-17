package de.bannerfall.market;

import java.util.List;

/**
 * GET /api/villages/{id}/market: the village's merchants, its transports and its own offers.
 *
 * @param outgoing the village's merchants that are away, oldest first (on the way, or delivered and
 *     coming back)
 * @param incoming transports on their way to the village, the next arrival first
 * @param offers the village's open offers, oldest first
 * @param exchange trading with the market itself (v1.3)
 */
public record MarketDto(
        MerchantsDto merchants,
        List<TransportDto> outgoing,
        List<TransportDto> incoming,
        List<OfferDto> offers,
        ExchangeDto exchange) {

    /**
     * @param total merchants of the market level
     * @param free not on the way and not reserved by an offer
     */
    public record MerchantsDto(int total, int free) {}
}
