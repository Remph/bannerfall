package de.bannerfall.building;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * POST /api/villages/{id}/build-orders/{orderId}/move: the new place of the order.
 *
 * @param position 0-based index in {@code VillageDto.buildQueue} (0 = the running order)
 */
public record BuildOrderMoveRequest(@NotNull @PositiveOrZero Integer position) {}
