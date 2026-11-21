package de.bannerfall.diplomacy;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One ally on the order sheet (2.1 S3, 12-solo-and-allies §8): its trust and how far its nearest
 * village is from the target. The travel times are a guide: the lord sends from the village it
 * chooses.
 *
 * @param fields the distance from its nearest village; null if it has no other village
 * @param attackSeconds the travel time of its slowest attack unit at home there; null if none
 * @param supportSeconds the same for its support units; null if none
 * @param sendSeconds its merchants' travel time; null if it has no other village
 * @param order its current order from the human; null if none
 */
public record OrderAllyDto(
        long playerId,
        String name,
        int trust,
        @Schema(nullable = true) Double fields,
        @Schema(nullable = true) Long attackSeconds,
        @Schema(nullable = true) Long supportSeconds,
        @Schema(nullable = true) Long sendSeconds,
        @Schema(nullable = true) OrderDto order) {}
