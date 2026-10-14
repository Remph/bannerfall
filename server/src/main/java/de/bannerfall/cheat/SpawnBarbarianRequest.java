package de.bannerfall.cheat;

import jakarta.validation.constraints.NotNull;

/** POST /api/dev/barbarians: the field for the new barbarian village. */
public record SpawnBarbarianRequest(@NotNull Integer x, @NotNull Integer y) {}
