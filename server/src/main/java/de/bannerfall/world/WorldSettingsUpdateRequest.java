package de.bannerfall.world;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/admin/world-settings}: the settings the host may change in a running world, with
 * the ranges that keep the game working (02-game-rules §1). {@code buildQueueMax} must not be below
 * {@code buildQueueSize}, {@code marketExchangeRatio} is 0 (off) or at least 1, and the night bonus
 * must not start and end at the same hour (all checked in {@link WorldService#updateSettings}).
 */
public record WorldSettingsUpdateRequest(
        @NotBlank @Size(max = 64) String name,
        @DecimalMin("0.1") @DecimalMax("100") double speed,
        @DecimalMin("0.1") @DecimalMax("100") double unitSpeed,
        @Min(0) @Max(1_000_000) int startResources,
        @DecimalMin("0") @DecimalMax("1") double luckRange,
        @Min(0) @Max(720) int beginnerProtectionHours,
        @Min(1) @Max(1000) int nobleMaxDistance,
        @DecimalMin("0") @DecimalMax("100") double loyaltyRegenPerHour,
        @Min(1) @Max(20) int buildQueueSize,
        @Min(1) @Max(30) int buildQueueMax,
        @Min(1) @Max(20) int recruitQueueSize,
        @Min(0) @Max(86_400) int cancelWindowSeconds,
        @Min(0) @Max(20) int barbariansPerNewPlayer,
        boolean barbarianGrowthEnabled,
        @DecimalMin("1") @DecimalMax("10") double tradeMaxRatio,
        @NotBlank @Size(max = 64) String inviteCode,
        @Min(1) @Max(1440) int npcThinkMinutes,
        @Schema(nullable = true) @Min(20) @Max(500) Integer mapSize,
        @Schema(nullable = true) @Min(0) @Max(5000) Integer barbarianInitialCount,
        @Schema(nullable = true) @Min(0) @Max(50) Integer npcLordCount,
        @Schema(nullable = true) Boolean guildsEnabled,
        @Schema(nullable = true) @Min(2) @Max(50) Integer guildMaxMembers,
        @Schema(nullable = true) Boolean npcGuildsEnabled,
        @Schema(nullable = true) @Min(2) @Max(50) Integer npcGuildMaxMembers,
        @Schema(nullable = true) Boolean barbarianWallsEnabled,
        @Schema(nullable = true) @Min(0) @Max(365) Integer barbarianWallStartDays,
        @Schema(nullable = true) @Min(0) @Max(20) Integer barbarianWallMaxLevel,
        @Schema(nullable = true) Boolean barbarianWallsRebuild,
        @Schema(nullable = true) Boolean unitLevelsEnabled,
        @Schema(nullable = true) @Min(1) @Max(100) Integer paladinVillagesPerSlot,
        @Schema(nullable = true) @Min(0) @Max(20) Integer paladinMaxSlots,
        @Schema(nullable = true) Boolean bannersEnabled,
        @Schema(nullable = true) @DecimalMin("0") @DecimalMax("3") Double bannerDropFactor,
        @Schema(nullable = true) Boolean standardTheftEnabled,
        @Schema(nullable = true) Boolean npcBannersEnabled,
        @Schema(nullable = true) @DecimalMin("0") @DecimalMax("10") Double marketExchangeRatio,
        @Schema(nullable = true) VictoryMode victoryMode,
        @Schema(nullable = true) @Min(10) @Max(100) Integer dominationPercent,
        @Schema(nullable = true) @Min(1) @Max(720) Integer victoryHoldHours,
        @Schema(nullable = true) @Min(1) @Max(12) Integer keepCount,
        @Schema(nullable = true) @Min(1) @Max(100) Integer keepPercent,
        @Schema(nullable = true) VillageNameLanguage villageNameLanguage,
        @Schema(nullable = true) Boolean nightBonusEnabled,
        @Schema(nullable = true) @Min(0) @Max(23) Integer nightBonusStartHour,
        @Schema(nullable = true) @Min(0) @Max(23) Integer nightBonusEndHour,
        @Schema(nullable = true) @DecimalMin("1.1") @DecimalMax("3") Double nightBonusFactor) {}
