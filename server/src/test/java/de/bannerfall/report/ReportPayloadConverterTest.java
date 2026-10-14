package de.bannerfall.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The stored JSON of the payloads (the {@code jsonb} column), without a database. */
class ReportPayloadConverterTest {

    private static final VillageRefDto BEN = new VillageRefDto(1, "Benhausen", 48, 52, 7L, "Ben");
    private static final VillageRefDto BARBARIANS = new VillageRefDto(2, null, 50, 50, null, null);

    private final ReportPayloadConverter converter = new ReportPayloadConverter();

    @Test
    void aBattleReportComesBackAsItWasStored() {
        BattleReport report =
                new BattleReport(
                        BEN,
                        BARBARIANS,
                        BattleOutcome.ATTACKER_WON,
                        -0.125,
                        Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 5, UnitType.CATAPULT, 4),
                        Map.of(UnitType.AXEMAN, 41),
                        BuildingType.SMITHY,
                        new BattleReport.DefenderSide(
                                Map.of(UnitType.SPEARMAN, 100),
                                Map.of(UnitType.SPEARMAN, 100),
                                List.of(
                                        new BattleReport.DefenderStack(
                                                BARBARIANS,
                                                Map.of(UnitType.SPEARMAN, 100),
                                                Map.of(UnitType.SPEARMAN, 100))),
                                5,
                                4,
                                3,
                                3),
                        new BattleReport.ScoutingReport(
                                true,
                                new ResourceAmounts(300, 2000, 2000),
                                Map.of(BuildingType.WALL, 5, BuildingType.SMITHY, 3),
                                new BattleReport.ScoutedBanner(BannerType.HARVEST, 3)),
                        new ResourceAmounts(150, 425, 425),
                        new BattleReport.LoyaltyReport(100, 72, false));

        String json = converter.convertToDatabaseColumn(report);

        assertThat(json).contains("\"type\":\"BATTLE\"");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(report);
    }

    @Test
    void aConquestReportComesBackAsItWasStored() {
        ConquestReport report = new ConquestReport(BARBARIANS, BEN, "Ben's village");

        String json = converter.convertToDatabaseColumn(report);

        assertThat(json).contains("\"type\":\"CONQUEST\"");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(report);
        assertThat(report.summary().from()).isEqualTo(BEN);
        assertThat(report.summary().to()).isEqualTo(BARBARIANS);
    }

    @Test
    void aSupportReportComesBackAsItWasStored() {
        SupportReport report = new SupportReport(BEN, BARBARIANS, Map.of(UnitType.SPEARMAN, 10));

        String json = converter.convertToDatabaseColumn(report);

        assertThat(json).contains("\"type\":\"SUPPORT\"");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(report);
    }

    @Test
    void aTradeReportComesBackAsItWasStored() {
        TradeReport report =
                new TradeReport(
                        BEN,
                        BARBARIANS,
                        new ResourceAmounts(1000, 0, 250),
                        new ResourceAmounts(300, 0, 0));

        String json = converter.convertToDatabaseColumn(report);

        assertThat(json).contains("\"type\":\"TRADE\"");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(report);
        assertThat(report.summary().from()).isEqualTo(BEN);
    }

    @Test
    void aTradeReportFromBeforeM13bLostNothing() {
        TradeReport report =
                new TradeReport(BEN, BARBARIANS, new ResourceAmounts(1000, 0, 250), null);
        String json = converter.convertToDatabaseColumn(report).replace(",\"lost\":", ",\"x\":");

        TradeReport read = (TradeReport) converter.convertToEntityAttribute(json);

        assertThat(read.lost()).isEqualTo(ResourceAmounts.ZERO);
    }

    @Test
    void nullablePartsStayNull() {
        BattleReport report =
                new BattleReport(
                        BEN,
                        BARBARIANS,
                        BattleOutcome.SCOUTS_ONLY,
                        null,
                        Map.of(UnitType.SCOUT, 4),
                        Map.of(UnitType.SCOUT, 4),
                        null,
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        null);

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(report)))
                .isEqualTo(report);
    }

    @Test
    void oldReportsStayReadableWhenPropertiesComeAndGo() {
        // a property that no longer exists, and maps and loot that didn't exist yet
        String json =
                """
                {"type":"SUPPORT","from":{"id":1,"x":48,"y":52},"to":{"id":2,"x":50,"y":50},
                 "removedLongAgo":true}
                """;

        ReportPayload payload = converter.convertToEntityAttribute(json);

        assertThat(payload).isInstanceOf(SupportReport.class);
        assertThat(((SupportReport) payload).units()).isEmpty();
        BattleReport battle =
                (BattleReport)
                        converter.convertToEntityAttribute(
                                """
                                {"type":"BATTLE","outcome":"DEFENDER_WON"}
                                """);
        assertThat(battle.attackerUnits()).isEmpty();
        assertThat(battle.loot()).isEqualTo(ResourceAmounts.ZERO);
        assertThat(battle.loyalty()).isNull(); // reports from before M11
        // a missing number reads as 0
        BattleReport withSide =
                (BattleReport)
                        converter.convertToEntityAttribute(
                                """
                                {"type":"BATTLE","defenderSide":{"wallAfter":3}}
                                """);
        assertThat(withSide.defenderSide().wallBefore()).isZero();
        assertThat(withSide.defenderSide().wallAfter()).isEqualTo(3);
        assertThat(withSide.defenderSide().stacks()).isEmpty();
    }

    @Test
    void unitMapsKeepOnlyCountsAboveZeroInEnumOrder() {
        SupportReport report =
                new SupportReport(
                        BEN,
                        BARBARIANS,
                        Map.of(UnitType.NOBLE, 1, UnitType.SPEARMAN, 10, UnitType.AXEMAN, 0));

        assertThat(report.units().keySet()).containsExactly(UnitType.SPEARMAN, UnitType.NOBLE);
    }

    @Test
    void aReportCarriesThePayloadOfItsKind() {
        SupportReport support = new SupportReport(BEN, BARBARIANS, Map.of());
        Instant now = Instant.parse("2026-10-04T12:00:00Z");

        assertThat(new Report(7, ReportKind.SUPPORT_ARRIVED, now, support, now).isRead()).isFalse();
        assertThatThrownBy(() -> new Report(7, ReportKind.ATTACK, now, support, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
