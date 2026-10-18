package de.bannerfall.recruit;

import de.bannerfall.common.UnsafeChars;
import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * PUT /api/recruit-templates/{letter} (1.4). Leading and trailing spaces of the name are removed.
 */
public record RecruitTemplateRequest(
        @NotBlank @Size(max = RecruitTemplateService.MAX_NAME_LENGTH) @Pattern(
                        regexp = "[^" + UnsafeChars.CLASS + "]*",
                        message = "must not contain control characters")
                String name,
        @NotNull Map<UnitType, @NotNull Integer> units) {}
