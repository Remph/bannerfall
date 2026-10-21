package de.bannerfall.automation;

import de.bannerfall.common.UnsafeChars;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /api/build-plans/{letter} (1.6). Leading and trailing spaces of the name are removed; the
 * lines are checked by {@link BuildPlanService#save}.
 */
public record BuildPlanRequest(
        @NotBlank @Size(max = BuildPlanService.MAX_NAME_LENGTH) @Pattern(
                        regexp = "[^" + UnsafeChars.CLASS + "]*",
                        message = "must not contain control characters")
                String name,
        @NotNull @Size(min = 1, max = BuildPlanService.MAX_LINES) List<@NotNull BuildPlanLine> lines) {}
