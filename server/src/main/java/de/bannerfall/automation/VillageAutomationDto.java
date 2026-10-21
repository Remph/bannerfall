package de.bannerfall.automation;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.recruit.RecruitTemplateLetter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/villages/{id}/automation} (04-api <i>Village automation</i>, 1.6).
 *
 * @param villagesNeeded the world's {@code automationVillages} (0 = no automation in this world)
 * @param villages how many villages the player owns
 * @param plan the picked plan; null if none
 * @param enabled switched on
 * @param pausedByCancel stopped because the owner cancelled one of its orders
 * @param line index of the plan's current line ({@code lineCount} once every line is reached)
 * @param lineCount the plan's lines, the recruit line included; 0 without a plan
 * @param building what the automation queues next: the current line's building, or a warehouse or
 *     farm level that has to come first; null when there is nothing to queue
 * @param level the level it queues ({@code building}'s next level), 0 with no building
 * @param readyAt when the stock pays it (WAITING_RESOURCES); otherwise null
 * @param recruiting the recruit template it keeps filled, once the recruit line is reached; null
 *     before that
 * @param missing what the recruit template still lacks, units by hand left out (only while
 *     recruiting)
 * @param byHand units of the recruit template the automation never recruits (nobles, banner
 *     thieves)
 */
public record VillageAutomationDto(
        AutomationState state,
        int villagesNeeded,
        int villages,
        @Schema(nullable = true) BuildPlanLetter plan,
        boolean enabled,
        boolean pausedByCancel,
        int line,
        int lineCount,
        @Schema(nullable = true) BuildingType building,
        int level,
        @Schema(nullable = true) Instant readyAt,
        @Schema(nullable = true) RecruitTemplateLetter recruiting,
        Map<UnitType, Integer> missing,
        List<UnitType> byHand) {}
