package de.bannerfall.world;

import de.bannerfall.npc.NpcDifficulty;
import de.bannerfall.rules.NightBonus;
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
 * @param guildStartTogether guild mates start next to each other at the opening and on a respawn
 *     (1.6, 02-game-rules §2); the pre-game world info says so
 * @param npcGuildMaxMembers the most lords a guild of NPC lords may have (09-npc-lords §13), never
 *     above {@code guildMaxMembers}
 * @param barbarianWallsEnabled whether barbarian villages build walls (M29)
 * @param barbarianWallStartDays days after the opening before they start
 * @param barbarianWallMaxLevel the highest wall level they build
 * @param barbarianWallsRebuild whether a wall that rams knocked down grows back
 * @param unitLevelsEnabled whether players can research unit levels at the smithy (M30)
 * @param paladinVillagesPerSlot villages for each further paladin slot (M31)
 * @param paladinMaxSlots the most paladins a player can have; 0 = no paladin (M31)
 * @param bannersEnabled whether banners work in this world (M32, 02-game-rules §21.9)
 * @param bannerDropFactor multiplier of the banner drop chances; 0 = no drops (M32)
 * @param marketExchangeRatio what 1 of a resource costs at the market itself; 0 = off (v1.3,
 *     02-game-rules §12.1)
 * @param victoryMode how the world is won (v1.3, 02-game-rules §22); {@code GET /api/world/victory}
 *     has the details and the standings
 * @param dominationPercent the share of all villages a side must hold (DOMINATION)
 * @param victoryHoldHours how long a side must hold its share or its keeps (real hours)
 * @param keepCount the banner keeps the world should have (KEEPS)
 * @param keepPercent the share of the keeps a side must hold, rounded up (KEEPS)
 * @param endsAt the end date the host set, null if none (§22)
 * @param endedAt when the world ended, null while it runs (§22); the client's game time stands
 *     still there
 * @param phase PREGAME until the host opens the world (M14g), ENDED once it is over (§22)
 * @param opensAt the time the pre-game ends, null if the host has not set one (null once open)
 * @param registeredPlayers human players with an account (the pre-game page shows how many)
 * @param version the release this server runs, e.g. {@code 1.1.0} (M28; the tag next to the server
 *     time and the changelog banner)
 * @param nightBonus the night bonus (1.4, 02-game-rules §8.1); null while it is off
 * @param archersEnabled whether the archer and the mounted archer can be recruited (1.6,
 *     02-game-rules §5.6)
 * @param moraleFloor the lowest morale in percent (1.6, 02-game-rules §8.2), e.g. {@code 30}; null
 *     while morale is off
 * @param noblePriceStep how much dearer each village and noble beyond the free rank makes the next
 *     noble, as a share of the base price; 0 = the flat price (1.6, 02-game-rules §5.7)
 * @param mapSpread how far apart the sides start (1.6, 02-game-rules §2)
 */
public record WorldDto(
        String name,
        double speed,
        double unitSpeed,
        int mapSize,
        MapSpread mapSpread,
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
        boolean guildStartTogether,
        int npcGuildMaxMembers,
        boolean barbarianWallsEnabled,
        int barbarianWallStartDays,
        int barbarianWallMaxLevel,
        boolean barbarianWallsRebuild,
        boolean unitLevelsEnabled,
        int paladinVillagesPerSlot,
        int paladinMaxSlots,
        boolean bannersEnabled,
        double bannerDropFactor,
        double marketExchangeRatio,
        VictoryMode victoryMode,
        int dominationPercent,
        int victoryHoldHours,
        int keepCount,
        int keepPercent,
        @Schema(nullable = true) Instant endsAt,
        @Schema(nullable = true) Instant endedAt,
        WorldPhase phase,
        @Schema(nullable = true) Instant opensAt,
        long registeredPlayers,
        String version,
        @Schema(nullable = true) NightBonusDto nightBonus,
        boolean archersEnabled,
        @Schema(nullable = true) Integer moraleFloor,
        double noblePriceStep) {

    /**
     * The night bonus of a world that has it on: owned villages defend {@code factor} times as
     * strongly against attacks arriving from {@code startHour} to {@code endHour} in {@code zone}
     * (the end hour no longer counts; the window may run across midnight).
     *
     * @param zone an IANA time zone, e.g. {@code Europe/Berlin}
     */
    public record NightBonusDto(int startHour, int endHour, double factor, String zone) {

        static NightBonusDto of(NightBonus night) {
            return night.enabled()
                    ? new NightBonusDto(
                            night.startHour(),
                            night.endHour(),
                            night.factor(),
                            night.zone().getId())
                    : null;
        }
    }
}
