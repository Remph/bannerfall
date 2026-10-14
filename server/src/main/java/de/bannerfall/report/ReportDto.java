package de.bannerfall.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One report with its full payload ({@code GET /api/reports/{id}}).
 *
 * @param payload null if the stored payload can't be read any more (see {@link
 *     ReportPayloadConverter})
 * @param sharedBy who sent this copy and whose view it shows (M40); null for a report the game
 *     created
 */
public record ReportDto(
        long id,
        ReportKind kind,
        Instant happenedAt,
        @Schema(nullable = true) ReportPayload payload,
        @Schema(nullable = true) ReportSharedBy sharedBy) {

    static ReportDto of(Report report) {
        return new ReportDto(
                report.getId(),
                report.getKind(),
                report.getHappenedAt(),
                report.getPayload(),
                report.getSharedBy());
    }
}
