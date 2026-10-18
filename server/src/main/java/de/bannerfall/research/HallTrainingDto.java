package de.bannerfall.research;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /api/villages/{id}/banner-hall} (Hall training 1.4, 04-api): the units whose levels
 * the Banner Hall trains, the paladin and the banner thief, each listed only in a world that has
 * it. The entries are the smithy page's, with {@code trainedIn = BANNER_HALL}.
 *
 * @param hallLevel the village's Banner Hall level (0 = not built)
 * @param units the paladin, then the banner thief
 * @param running the training that runs in this village's hall (one at a time); null if none
 * @param theftChancePerThief the chance one surviving banner thief adds at thief level 1, 2 and 3
 *     (02-game-rules §21.6.2); null with banners off
 */
public record HallTrainingDto(
        int hallLevel,
        List<SmithyDto.SmithyUnitDto> units,
        @Schema(nullable = true) ResearchOrderDto running,
        @Schema(nullable = true) List<Double> theftChancePerThief) {}
