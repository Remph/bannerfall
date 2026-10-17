package de.bannerfall.victory;

import de.bannerfall.world.EndReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * How a world ended (02-game-rules §22).
 *
 * @param winner null if nobody held a village when the end date came
 * @param sides the final standings, most points first (at most 100)
 */
public record WorldResultDto(
        EndReason reason,
        Instant endedAt,
        @Schema(nullable = true) VictorySide winner,
        List<VictorySide> sides) {}
