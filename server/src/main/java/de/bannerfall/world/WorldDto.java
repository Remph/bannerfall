package de.bannerfall.world;

import de.bannerfall.npc.NpcDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * GET /api/world: the settings every player may see (no invite code). The world screen of the
 * pre-game and the "World" popup of the settings menu list them (M14g).
 *
 * @param buildQueueSize orders per village at the normal price, the running one included
 * @param buildQueueMax orders per village with the paid slots behind {@code buildQueueSize} (the
 *     Town Hall shows "6/10", M13c)
 * @param recruitQueueSize orders per recruiting building and village, the running one included
 * @param tradeMaxRatio the widest ratio of a market offer (the market form checks it, M13b)
 * @param guildsEnabled whether guilds can be created and joined (M15a)
 * @param guildMaxMembers the most members a guild may have
 * @param barbarianWallsEnabled whether barbarian villages build walls (M29)
 * @param barbarianWallStartDays days after the opening before they start
 * @param barbarianWallMaxLevel the highest wall level they build
 * @param barbarianWallsRebuild whether a wall that rams knocked down grows back
 * @param unitLevelsEnabled whether players can research unit levels at the smithy (M30)
 * @param paladinVillagesPerSlot villages for each further paladin slot (M31)
 * @param paladinMaxSlots the most paladins a player can have; 0 = no paladin (M31)
 * @param bannersEnabled whether banners work in this world (M32, 02-game-rules §21.9)
 * @param bannerDropFactor multiplier of the banner drop chances; 0 = no drops (M32)
 * @param phase PREGAME until the host opens the world (M14g)
 * @param opensAt the time the pre-game ends, null if the host has not set one (null once open)
 * @param registeredPlayers human players with an account (the pre-game page shows how many)
 * @param version the release this server runs, e.g. {@code 1.1.0} (M28; the tag next to the server
 *     time and the changelog banner)
 */
public record WorldDto(
        String name,
        double speed,
        double unitSpeed,
        int mapSize,
        int buildQueueSize,
        int buildQueueMax,
        int recruitQueueSize,
        double tradeMaxRatio,
        Instant startedAt,
        int startResources,
        double luckRange,
        int beginnerProtectionHours,
        int nobleMaxDistance,
        double loyaltyRegenPerHour,
        int cancelWindowSeconds,
        boolean barbarianGrowthEnabled,
        int npcLordCount,
        NpcDifficulty npcDifficulty,
        boolean guildsEnabled,
        int guildMaxMembers,
        boolean barbarianWallsEnabled,
        int barbarianWallStartDays,
        int barbarianWallMaxLevel,
        boolean barbarianWallsRebuild,
        boolean unitLevelsEnabled,
        int paladinVillagesPerSlot,
        int paladinMaxSlots,
        boolean bannersEnabled,
        double bannerDropFactor,
        WorldPhase phase,
        @Schema(nullable = true) Instant opensAt,
        long registeredPlayers,
        String version) {}
