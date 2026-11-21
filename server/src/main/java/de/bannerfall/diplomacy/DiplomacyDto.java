package de.bannerfall.diplomacy;

import de.bannerfall.victory.VictorySide;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /api/diplomacy} (2.1 S2b, 12-solo-and-allies §7): the court, the human's allies and
 * the other lords with their trust in him.
 *
 * @param soloMode lords can be invited (a solo world)
 * @param leader the human leads a guild: he may invite lords (in a solo world)
 * @param guildFull his guild has no room left
 * @param court the court's side (the lord guild with the most points, solo worlds only); null
 *     without one
 * @param mine the human's own side (his guild's while guilds are on); null while it owns no village
 * @param allVillages every village on the map (the domination share counts against it)
 * @param allies the lords in his guild, by points
 * @param lords the other lords, most trust first
 */
public record DiplomacyDto(
        boolean soloMode,
        boolean leader,
        boolean guildFull,
        @Schema(nullable = true) VictorySide court,
        @Schema(nullable = true) VictorySide mine,
        int allVillages,
        List<LordDto> allies,
        List<LordDto> lords) {}
