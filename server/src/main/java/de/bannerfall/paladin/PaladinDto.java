package de.bannerfall.paladin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A player's paladin slots (02-game-rules §5.5, {@code GET /api/paladin}).
 *
 * @param maxSlots the world's maximum; 0 = no paladin in this world
 * @param villagesPerSlot villages for each further slot
 * @param villages villages the player owns now
 * @param slots slots the player has now
 * @param used paladins that take a slot: at home, supporting, on the way, in training, waiting to
 *     be dismissed
 * @param nextSlotAtVillages the village count that opens the next slot; null at the maximum
 * @param minutesPerField the paladin's walking time of one field in this world ({@code
 *     minutesPerField ÷ (speed × unitSpeed)}); the pace of a support he leads
 */
public record PaladinDto(
        int maxSlots,
        int villagesPerSlot,
        int villages,
        int slots,
        int used,
        @Schema(nullable = true) Integer nextSlotAtVillages,
        double minutesPerField) {}
