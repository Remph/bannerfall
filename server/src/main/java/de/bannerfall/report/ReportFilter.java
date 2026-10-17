package de.bannerfall.report;

import java.util.EnumSet;
import java.util.Set;

/**
 * The filter chips above the report list (1.3, 05-frontend <i>Reports</i>): each groups the kinds a
 * player looks for together, and every kind belongs to exactly one filter.
 */
public enum ReportFilter {
    /** Own attacks and conquests. */
    ATTACKS(EnumSet.of(ReportKind.ATTACK, ReportKind.CONQUEST)),
    /** Battles in own or supported villages, and lost villages. */
    DEFENSE(EnumSet.of(ReportKind.DEFENSE, ReportKind.VILLAGE_LOST)),
    /** Attacks with only scouts. */
    SCOUTING(EnumSet.of(ReportKind.SCOUT)),
    /** Supports that arrived or were sent home. */
    SUPPORT(EnumSet.of(ReportKind.SUPPORT_ARRIVED, ReportKind.SUPPORT_RETURNED)),
    /** Merchants that delivered resources. */
    TRADE(EnumSet.of(ReportKind.TRADE_ARRIVED));

    private final Set<ReportKind> kinds;

    ReportFilter(Set<ReportKind> kinds) {
        this.kinds = kinds;
    }

    /** The kinds this filter shows. */
    public Set<ReportKind> kinds() {
        return kinds;
    }

    /** The filter a kind belongs to. */
    public static ReportFilter of(ReportKind kind) {
        for (ReportFilter filter : values()) {
            if (filter.kinds.contains(kind)) {
                return filter;
            }
        }
        throw new IllegalStateException("no filter for " + kind);
    }
}
