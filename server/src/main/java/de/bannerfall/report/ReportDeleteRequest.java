package de.bannerfall.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code POST /api/reports/delete} (1.3): the reports chosen in the list's select mode, at most a
 * page of the largest size.
 */
record ReportDeleteRequest(
        @NotNull @Size(min = 1, max = ReportController.MAX_PAGE_SIZE) List<@NotNull Long> ids) {}
