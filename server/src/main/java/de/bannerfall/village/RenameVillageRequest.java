package de.bannerfall.village;

import de.bannerfall.common.UnsafeChars;
import de.bannerfall.world.VillageNameLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /api/villages/{id}. Leading and trailing spaces are removed. */
public record RenameVillageRequest(
        @NotBlank @Size(max = VillageNameLanguage.MAX_NAME_LENGTH) @Pattern(
                        regexp = "[^" + UnsafeChars.CLASS + "]*",
                        message = "must not contain control characters")
                String name) {}
