package de.bannerfall.note;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/villages/{id}/note} (1.4). An empty or blank text deletes the note; otherwise the
 * text is checked like a chat message ({@code ChatText}).
 */
record VillageNoteRequest(@NotNull String text) {}
