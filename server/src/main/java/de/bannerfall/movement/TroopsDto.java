package de.bannerfall.movement;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import java.util.List;
import java.util.Map;

/**
 * GET /api/villages/{id}/troops (04-api). Unit maps: missing types mean 0.
 *
 * @param home the village's own units at home
 * @param supportsHere units of other villages (own ones included) standing here
 * @param ownUnitsElsewhere this village's units standing in other villages
 * @param outgoing attacks and supports this village has sent, still on the way, next first
 * @param incoming everything arriving here, next first
 */
public record TroopsDto(
        Map<UnitType, Integer> home,
        List<SupportHereDto> supportsHere,
        List<UnitsElsewhereDto> ownUnitsElsewhere,
        List<MovementDto> outgoing,
        List<IncomingDto> incoming) {

    /** A support stack in this village; its id is for "send back". */
    public record SupportHereDto(
            long stackId, VillageRefDto homeVillage, Map<UnitType, Integer> units) {}

    /** A stack of this village's units somewhere else; its id is for "withdraw". */
    public record UnitsElsewhereDto(
            long stackId, VillageRefDto locationVillage, Map<UnitType, Integer> units) {}
}
