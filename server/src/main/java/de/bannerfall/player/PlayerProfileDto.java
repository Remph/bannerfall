package de.bannerfall.player;

import de.bannerfall.achievement.EarnedAchievementDto;
import de.bannerfall.guild.GuildRefDto;
import de.bannerfall.statistics.PlayerStatsDto;
import de.bannerfall.village.ProfileVillageDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /api/players/{id}}. An NPC lord's personality is never part of it (02-game-rules
 * §11b). {@code guild} is the player's guild (M15a), null if he is in none. {@code achievements}
 * are the ones he earned (M16, 02-game-rules §19), highest level of each; empty for an NPC lord.
 * {@code stats} (1.5) are his statistics (02-game-rules §24), NPC lords' too. {@code careerUrl}
 * (1.5) is his career page at the account server, for a player who logs in through it on a world
 * that reports careers (01-architecture §4.9b); null otherwise.
 */
public record PlayerProfileDto(
        long id,
        String name,
        boolean npc,
        int points,
        long rank,
        List<ProfileVillageDto> villages,
        @Schema(nullable = true) GuildRefDto guild,
        List<EarnedAchievementDto> achievements,
        PlayerStatsDto stats,
        @Schema(nullable = true) String careerUrl) {}
