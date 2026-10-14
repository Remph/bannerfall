package de.bannerfall.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The content of a report (03-data-model <i>report</i>): stored as JSON in {@code report.payload}
 * and sent as it is by {@code GET /api/reports/{id}}. The property {@code type} tells the records
 * apart. Each recipient gets his own copy, already cut down to what he may see (02-game-rules §14),
 * so nothing hidden is ever stored for him.
 *
 * <p>Village refs inside are snapshots taken when it happened, so a report stays readable after a
 * rename or a conquest. Stored payloads live for a long time, so old reports must stay readable
 * when a record changes: a removed component is ignored, a new one is null (objects), 0/false
 * (primitives) or empty (unit maps, see the records' constructors) in old reports ({@link
 * ReportPayloadConverter}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BattleReport.class, name = "BATTLE"),
    @JsonSubTypes.Type(value = SupportReport.class, name = "SUPPORT"),
    @JsonSubTypes.Type(value = ConquestReport.class, name = "CONQUEST"),
    @JsonSubTypes.Type(value = TradeReport.class, name = "TRADE")
})
// The same names for the OpenAPI description, so the generated TS types say type: "BATTLE".
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "BATTLE", schema = BattleReport.class),
            @DiscriminatorMapping(value = "SUPPORT", schema = SupportReport.class),
            @DiscriminatorMapping(value = "CONQUEST", schema = ConquestReport.class),
            @DiscriminatorMapping(value = "TRADE", schema = TradeReport.class)
        })
public sealed interface ReportPayload
        permits BattleReport, SupportReport, ConquestReport, TradeReport {

    /** The few fields the report list shows. */
    ReportSummary summary();
}
