package de.bannerfall.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One line of the report list: the report without its payload, plus its summary.
 *
 * @param summary null if the stored payload can't be read any more (see {@link
 *     ReportPayloadConverter})
 * @param sharedBy who sent this copy (M40); null for a report the game created
 */
public record ReportItemDto(
        long id,
        ReportKind kind,
        Instant happenedAt,
        boolean read,
        @Schema(nullable = true) ReportSummary summary,
        @Schema(nullable = true) ReportSharedBy sharedBy) {

    static ReportItemDto of(Report report) {
        return new ReportItemDto(
                report.getId(),
                report.getKind(),
                report.getHappenedAt(),
                report.isRead(),
                report.getPayload() == null ? null : report.getPayload().summary(),
                report.getSharedBy());
    }
}
