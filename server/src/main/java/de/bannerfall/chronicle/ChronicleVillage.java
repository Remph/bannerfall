package de.bannerfall.chronicle;

import io.swagger.v3.oas.annotations.media.Schema;

/** A village as the chronicle names it: its name then (null for a barbarian one) and its field. */
public record ChronicleVillage(long id, @Schema(nullable = true) String name, int x, int y) {}
