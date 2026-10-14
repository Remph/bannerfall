package de.bannerfall.research;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.constraints.NotNull;

/** {@code POST /api/villages/{id}/research-orders}: research the next level of a unit. */
public record ResearchOrderRequest(@NotNull UnitType unit) {}
