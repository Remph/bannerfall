package de.bannerfall.victory;

import de.bannerfall.world.VictoryMode;
import de.bannerfall.world.WorldPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/world/victory} (v1.3, 02-game-rules §22): how this world is won and how close
 * each side is.
 *
 * @param keepCount the banner keeps on the map (KEEPS; the setting is how many there should be)
 * @param keepsNeeded how many of them a side must hold: {@code keepPercent} rounded up
 * @param endsAt the end date the host set, null if none
 * @param allVillages every village on the map, barbarians and keeps included (the domination share
 *     is counted against it)
 * @param sides the leading sides by what the mode counts (villages, keeps; points without a
 *     condition), at most 10; empty in the pre-game and once the world has ended
 * @param leader the side that meets the condition at the last check, null if none
 * @param leaderSince since when it meets it without a break
 * @param winsAt when it wins if it keeps holding
 * @param result how the world ended; null while it runs
 */
public record VictoryDto(
        VictoryMode mode,
        int dominationPercent,
        int holdHours,
        int keepCount,
        int keepPercent,
        int keepsNeeded,
        @Schema(nullable = true) Instant endsAt,
        WorldPhase phase,
        int allVillages,
        List<VictorySide> sides,
        @Schema(nullable = true) VictorySide leader,
        @Schema(nullable = true) Instant leaderSince,
        @Schema(nullable = true) Instant winsAt,
        @Schema(nullable = true) WorldResultDto result) {}
