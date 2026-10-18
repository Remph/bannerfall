package de.bannerfall.note;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The player's note on a village after {@code PUT /api/villages/{id}/note} (1.4).
 *
 * @param text trimmed; null once the note is deleted
 */
public record VillageNoteDto(long villageId, @Schema(nullable = true) String text) {}
