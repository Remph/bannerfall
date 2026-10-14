package de.bannerfall.world;

import de.bannerfall.npc.NpcBalanceSettings;
import de.bannerfall.npc.NpcDifficulty;

/**
 * The settings of 02-game-rules §1, one immutable snapshot. {@code WorldService} loads it from the
 * {@code world_settings} row ({@code started_at} is kept apart: {@code WorldService.startedAt()});
 * the NPC settings come from M12b ({@code npcCustom}: the CUSTOM difficulty's values, null until
 * the admin first picks CUSTOM). The four barbarian wall settings (M29) are explained in {@code
 * Barbarians}; their defaults leave a world as it was. {@code unitLevelsEnabled} (M30,
 * 02-game-rules §5.4) lets players research levels for their units; off means every unit is level
 * 1. {@code paladinVillagesPerSlot} and {@code paladinMaxSlots} (M31, 02-game-rules §5.5) give a
 * player one paladin slot at the start, one more per that many villages and at most {@code
 * paladinMaxSlots} (0 = no paladin in this world). {@code bannersEnabled} and {@code
 * bannerDropFactor} (M32, 02-game-rules §21.9) switch the banners on and scale the chance that a
 * won battle drops one (0 = no drops; the economy effects of a banner already in a village stay).
 */
public record WorldSettings(
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
        NpcBalanceSettings.Difficulty npcCustom,
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
        double bannerDropFactor) {

    /**
     * The defaults of the table in 02-game-rules §1 (speed 1; the invite code is a placeholder).
     */
    public static WorldSettings defaults() {
        return new WorldSettings(
                "Bannerfall 1",
                1.0,
                1.0,
                100,
                500,
                0.25,
                72,
                70,
                1.0,
                5,
                10,
                5,
                600,
                30,
                3,
                true,
                3.0,
                "change-me",
                4,
                NpcDifficulty.NORMAL,
                20,
                null,
                false,
                10,
                false,
                3,
                5,
                false,
                false,
                4,
                5,
                true,
                1.0);
    }

    /** Same settings with another unit speed (tests, balancing). */
    public WorldSettings withUnitSpeed(double newUnitSpeed) {
        return new WorldSettings(
                name,
                speed,
                newUnitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                barbarianWallsEnabled,
                barbarianWallStartDays,
                barbarianWallMaxLevel,
                barbarianWallsRebuild,
                unitLevelsEnabled,
                paladinVillagesPerSlot,
                paladinMaxSlots,
                bannersEnabled,
                bannerDropFactor);
    }

    /** Same settings with another world speed (tests, balancing). */
    public WorldSettings withSpeed(double newSpeed) {
        return new WorldSettings(
                name,
                newSpeed,
                unitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                barbarianWallsEnabled,
                barbarianWallStartDays,
                barbarianWallMaxLevel,
                barbarianWallsRebuild,
                unitLevelsEnabled,
                paladinVillagesPerSlot,
                paladinMaxSlots,
                bannersEnabled,
                bannerDropFactor);
    }

    /** Same settings with other barbarian wall settings (M29; tests, balancing). */
    public WorldSettings withBarbarianWalls(
            boolean enabled, int startDays, int maxLevel, boolean rebuild) {
        return new WorldSettings(
                name,
                speed,
                unitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                enabled,
                startDays,
                maxLevel,
                rebuild,
                unitLevelsEnabled,
                paladinVillagesPerSlot,
                paladinMaxSlots,
                bannersEnabled,
                bannerDropFactor);
    }

    /** Same settings with unit levels switched on or off (M30; tests, balancing). */
    public WorldSettings withUnitLevels(boolean enabled) {
        return new WorldSettings(
                name,
                speed,
                unitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                barbarianWallsEnabled,
                barbarianWallStartDays,
                barbarianWallMaxLevel,
                barbarianWallsRebuild,
                enabled,
                paladinVillagesPerSlot,
                paladinMaxSlots,
                bannersEnabled,
                bannerDropFactor);
    }

    /** Same settings with other paladin settings (M31; tests, balancing). */
    public WorldSettings withPaladin(int villagesPerSlot, int maxSlots) {
        return new WorldSettings(
                name,
                speed,
                unitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                barbarianWallsEnabled,
                barbarianWallStartDays,
                barbarianWallMaxLevel,
                barbarianWallsRebuild,
                unitLevelsEnabled,
                villagesPerSlot,
                maxSlots,
                bannersEnabled,
                bannerDropFactor);
    }

    /** Same settings with other banner settings (M32; tests, balancing). */
    public WorldSettings withBanners(boolean enabled, double dropFactor) {
        return new WorldSettings(
                name,
                speed,
                unitSpeed,
                mapSize,
                startResources,
                luckRange,
                beginnerProtectionHours,
                nobleMaxDistance,
                loyaltyRegenPerHour,
                buildQueueSize,
                buildQueueMax,
                recruitQueueSize,
                cancelWindowSeconds,
                barbarianInitialCount,
                barbariansPerNewPlayer,
                barbarianGrowthEnabled,
                tradeMaxRatio,
                inviteCode,
                npcLordCount,
                npcDifficulty,
                npcThinkMinutes,
                npcCustom,
                guildsEnabled,
                guildMaxMembers,
                barbarianWallsEnabled,
                barbarianWallStartDays,
                barbarianWallMaxLevel,
                barbarianWallsRebuild,
                unitLevelsEnabled,
                paladinVillagesPerSlot,
                paladinMaxSlots,
                enabled,
                dropFactor);
    }
}
