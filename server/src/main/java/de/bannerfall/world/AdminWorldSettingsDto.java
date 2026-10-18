package de.bannerfall.world;

import de.bannerfall.npc.NpcDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * {@code GET /api/admin/world-settings}: every setting of 02-game-rules §1 including the invite
 * code. {@code mapSize}, {@code barbarianInitialCount} and {@code npcLordCount} only matter when
 * the world is created, so they change only in the pre-game ({@link WorldSettingsUpdateRequest});
 * {@code npcDifficulty} has its own endpoint.
 *
 * @param phase PREGAME, OPEN (M14g) or ENDED (v1.3, 02-game-rules §22)
 * @param opensAt the time the pre-game ends, null if none is set (always null once open)
 * @param endsAt the end date the host set, null if none (§22)
 * @param endedAt when the world ended, null while it runs
 */
public record AdminWorldSettingsDto(
        String name,
        double speed,
        double unitSpeed,
        int mapSize,
        int startResources,
        double luckRange,
        int beginnerProtectionHours,
        int nobleMaxDistance,
        double loyaltyRegenPerHour,
        int buildQueueSize,
        int buildQueueMax,
        int recruitQueueSize,
        int cancelWindowSeconds,
        int barbarianInitialCount,
        int barbariansPerNewPlayer,
        boolean barbarianGrowthEnabled,
        double tradeMaxRatio,
        String inviteCode,
        int npcLordCount,
        NpcDifficulty npcDifficulty,
        int npcThinkMinutes,
        boolean guildsEnabled,
        int guildMaxMembers,
        boolean npcGuildsEnabled,
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
        boolean standardTheftEnabled,
        boolean npcBannersEnabled,
        double marketExchangeRatio,
        VictoryMode victoryMode,
        int dominationPercent,
        int victoryHoldHours,
        int keepCount,
        int keepPercent,
        VillageNameLanguage villageNameLanguage,
        boolean nightBonusEnabled,
        int nightBonusStartHour,
        int nightBonusEndHour,
        double nightBonusFactor,
        WorldPhase phase,
        @Schema(nullable = true) Instant opensAt,
        @Schema(nullable = true) Instant endsAt,
        @Schema(nullable = true) Instant endedAt) {

    static AdminWorldSettingsDto of(
            WorldSettings s, WorldPhase phase, Instant opensAt, Instant endsAt, Instant endedAt) {
        return new AdminWorldSettingsDto(
                s.name(),
                s.speed(),
                s.unitSpeed(),
                s.mapSize(),
                s.startResources(),
                s.luckRange(),
                s.beginnerProtectionHours(),
                s.nobleMaxDistance(),
                s.loyaltyRegenPerHour(),
                s.buildQueueSize(),
                s.buildQueueMax(),
                s.recruitQueueSize(),
                s.cancelWindowSeconds(),
                s.barbarianInitialCount(),
                s.barbariansPerNewPlayer(),
                s.barbarianGrowthEnabled(),
                s.tradeMaxRatio(),
                s.inviteCode(),
                s.npcLordCount(),
                s.npcDifficulty(),
                s.npcThinkMinutes(),
                s.guildsEnabled(),
                s.guildMaxMembers(),
                s.npcGuildsEnabled(),
                s.npcGuildMaxMembers(),
                s.barbarianWallsEnabled(),
                s.barbarianWallStartDays(),
                s.barbarianWallMaxLevel(),
                s.barbarianWallsRebuild(),
                s.unitLevelsEnabled(),
                s.paladinVillagesPerSlot(),
                s.paladinMaxSlots(),
                s.bannersEnabled(),
                s.bannerDropFactor(),
                s.standardTheftEnabled(),
                s.npcBannersEnabled(),
                s.marketExchangeRatio(),
                s.victoryMode(),
                s.dominationPercent(),
                s.victoryHoldHours(),
                s.keepCount(),
                s.keepPercent(),
                s.villageNameLanguage(),
                s.nightBonusEnabled(),
                s.nightBonusStartHour(),
                s.nightBonusEndHour(),
                s.nightBonusFactor(),
                phase,
                phase == WorldPhase.PREGAME ? opensAt : null,
                endsAt,
                endedAt);
    }
}
