package de.bannerfall.movement;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/movements/{id}/return-push (1.6): the bell on an outgoing attack or a return, e.g.
 * {@code {"on": true}}.
 */
public record ReturnPushRequest(@NotNull Boolean on) {}
