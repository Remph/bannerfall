package de.bannerfall.village;

/** A village in a player profile (04-api, {@code GET /api/players/{id}}): no owner-only values. */
public record ProfileVillageDto(long id, String name, int x, int y, int points) {}
