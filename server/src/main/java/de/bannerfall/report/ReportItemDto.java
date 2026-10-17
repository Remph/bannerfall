package de.bannerfall.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One line of the report list: the report without its payload, plus its summary.
 *
 * @param summary null if the stored payload can't be read any more (see {@link
 *     ReportPayloadConverter})
 * @param sharedBy who sent this copy (M40); null for a report the game created
 * @param listedAt where the list sorts it (1.3, the day headings): when it happened, for a copy
 *     when it arrived
 */
public record ReportItemDto(
        long id,
        ReportKind kind,
        Instant happenedAt,
        boolean read,
        @Schema(nullable = true) ReportSummary summary,
        @Schema(nullable = true) ReportSharedBy sharedBy,
        Instant listedAt) {

    static ReportItemDto of(Report report) {
        return new ReportItemDto(
                report.getId(),
                report.getKind(),
                report.getHappenedAt(),
                report.isRead(),
                report.getPayload() == null ? null : report.getPayload().summary(report.getKind()),
                report.getSharedBy(),
                report.getSharedBy() == null ? report.getHappenedAt() : report.getCreatedAt());
    }
}
