package de.bannerfall.report;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import java.util.List;
import java.util.Map;

/**
 * A support arrived or was sent home (kinds SUPPORT_ARRIVED, SUPPORT_RETURNED; 02-game-rules §6,
 * §14).
 *
 * @param from the village the units belong to (snapshot)
 * @param to the village they support(ed) (snapshot)
 * @param units the units that arrived, or that were sent home
 */
public record SupportReport(VillageRefDto from, VillageRefDto to, Map<UnitType, Integer> units)
        implements ReportPayload {

    public SupportReport {
        units = ReportMaps.units(units);
    }

    @Override
    public ReportSummary summary(ReportKind kind) {
        return new ReportSummary(from, to, null, List.of());
    }
}
