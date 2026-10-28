package de.bannerfall.tutorial;

import de.bannerfall.gamedata.ResourceAmounts;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the card shows now.
 *
 * @param index the 1-based place among the world's steps; 0 for a lesson
 * @param total how many steps the world has; 0 for a lesson
 * @param refund what finishing the step gives back; null for a step without a refund and for a
 *     lesson
 */
public record TutorialCurrentDto(
        TutorialStep key,
        TutorialStep.Kind kind,
        int index,
        int total,
        @Schema(nullable = true) ResourceAmounts refund) {}
