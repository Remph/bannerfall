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
 * @param phase PREGAME or OPEN (M14g)
 * @param opensAt the time the pre-game ends, null if none is set (always null once open)
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
        @Schema(nullable = true) Instant opensAt) {

    static AdminWorldSettingsDto of(WorldSettings s, WorldPhase phase, Instant opensAt) {
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
                s.barbarianWallsEnabled(),
                s.barbarianWallStartDays(),
                s.barbarianWallMaxLevel(),
                s.barbarianWallsRebuild(),
                s.unitLevelsEnabled(),
                s.paladinVillagesPerSlot(),
                s.paladinMaxSlots(),
                s.bannersEnabled(),
                s.bannerDropFactor(),
                phase,
                phase == WorldPhase.PREGAME ? opensAt : null);
    }
}
