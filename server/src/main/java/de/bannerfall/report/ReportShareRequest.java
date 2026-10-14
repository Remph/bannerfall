package de.bannerfall.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code POST /api/reports/{id}/share} (M40): the players to send the report to, by name. */
record ReportShareRequest(
        @NotNull @Size(min = 1, max = ReportShareService.MAX_RECIPIENTS) List<@NotBlank @Size(max = 64) String> names) {}
