package de.bannerfall.report;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.village.VillageRefDto;

/**
 * Merchants delivered resources (kind TRADE_ARRIVED; 02-game-rules §12, §14): a plain transport or
 * one half of an accepted offer.
 *
 * @param from the village that sent them (snapshot)
 * @param to the village they arrived at (snapshot)
 * @param resources what the merchants carried
 * @param lost the part of it that didn't fit into the warehouse (since M13b; reports written before
 *     have none, read as nothing lost)
 */
public record TradeReport(
        VillageRefDto from, VillageRefDto to, ResourceAmounts resources, ResourceAmounts lost)
        implements ReportPayload {

    public TradeReport {
        resources = resources == null ? ResourceAmounts.ZERO : resources;
        lost = lost == null ? ResourceAmounts.ZERO : lost;
    }

    @Override
    public ReportSummary summary() {
        return new ReportSummary(from, to, null);
    }
}
