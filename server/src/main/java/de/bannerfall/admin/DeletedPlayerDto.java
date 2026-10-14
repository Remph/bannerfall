package de.bannerfall.admin;

/** The answer of {@code POST /api/admin/players/{id}/delete}. */
public record DeletedPlayerDto(long playerId, int villagesConverted) {}
