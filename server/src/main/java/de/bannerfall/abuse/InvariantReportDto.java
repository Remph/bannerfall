package de.bannerfall.abuse;

import java.time.Instant;
import java.util.List;

/**
 * The result of an invariant check (M14e): empty {@code problems} = nothing wrong.
 *
 * @param checkedAt when it ran
 */
public record InvariantReportDto(Instant checkedAt, List<InvariantProblemDto> problems) {}
