package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.village.VillageRefDto;
import java.time.Instant;

/**
 * Merchants on the way (04-api <i>Market</i>): outgoing ones until they are back home ({@code
 * returnAt}), incoming ones until they arrive.
 *
 * @param arrived the resources were delivered and the merchants are walking home
 */
public record TransportDto(
        long id,
        VillageRefDto sourceVillage,
        VillageRefDto targetVillage,
        ResourceAmounts resources,
        int merchants,
        Instant departAt,
        Instant arriveAt,
        Instant returnAt,
        boolean arrived) {

    static TransportDto of(TradeTransport transport) {
        return new TransportDto(
                transport.getId(),
                VillageRefDto.of(transport.getSourceVillage()),
                VillageRefDto.of(transport.getTargetVillage()),
                transport.getResources(),
                transport.getMerchants(),
                transport.getDepartAt(),
                transport.getArriveAt(),
                transport.getReturnAt(),
                transport.isArrived());
    }
}
