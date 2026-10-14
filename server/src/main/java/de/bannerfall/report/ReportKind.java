package de.bannerfall.report;

/**
 * What a report is about (02-game-rules §14), seen from its recipient. Each kind has one payload
 * type.
 */
public enum ReportKind {
    /** The attacker's view of a battle. */
    ATTACK(BattleReport.class),
    /** The view of the attacked village's owner and of every supporter whose units fought. */
    DEFENSE(BattleReport.class),
    /** The attacker's view of an attack with only scouts (no main battle). */
    SCOUT(BattleReport.class),
    /** A support arrived; for its sender and for the supported village's owner. */
    SUPPORT_ARRIVED(SupportReport.class),
    /**
     * A support was sent home: withdrawn by its owner or sent back by the supported village's
     * owner; for the other one of the two.
     */
    SUPPORT_RETURNED(SupportReport.class),
    /** The player conquered a village (02-game-rules §10). */
    CONQUEST(ConquestReport.class),
    /** The player lost a village to a conqueror. */
    VILLAGE_LOST(ConquestReport.class),
    /** Merchants delivered resources; for the owner of the village they arrived at (M13). */
    TRADE_ARRIVED(TradeReport.class);

    private final Class<? extends ReportPayload> payloadType;

    ReportKind(Class<? extends ReportPayload> payloadType) {
        this.payloadType = payloadType;
    }

    /** The payload record this kind carries. */
    public Class<? extends ReportPayload> payloadType() {
        return payloadType;
    }
}
