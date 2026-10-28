package de.bannerfall.tutorial;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The player's tutorial (04-api <i>Tutorial</i>).
 *
 * @param enabled whether the world has the tutorial on ({@code tutorialEnabled})
 * @param active whether this player has it switched on; false: no card, {@code current} is null
 * @param current the card; null when the tutorial is off or has nothing left to show
 * @param steps every step and lesson the world has, in the order of the card; empty when off
 * @param refunds the refunds this call paid
 */
public record TutorialDto(
        boolean enabled,
        boolean active,
        @Schema(nullable = true) TutorialCurrentDto current,
        List<TutorialStepDto> steps,
        List<TutorialRefundDto> refunds) {}
