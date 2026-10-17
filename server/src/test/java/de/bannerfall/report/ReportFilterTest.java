package de.bannerfall.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The filter chips of the report list ({@link ReportFilter}): a kind without a filter would make
 * {@code GET /api/reports} fail for every player who has such a report.
 */
class ReportFilterTest {

    @Test
    void everyKindIsInExactlyOneFilter() {
        for (ReportKind kind : ReportKind.values()) {
            assertThat(Arrays.stream(ReportFilter.values()).filter(f -> f.kinds().contains(kind)))
                    .as("filters of %s", kind)
                    .hasSize(1);
            assertThat(ReportFilter.of(kind).kinds()).contains(kind);
        }
    }
}
