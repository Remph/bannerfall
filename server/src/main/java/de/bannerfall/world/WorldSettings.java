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
 * {@code marketExchangeRatio} (v1.3, 02-game-rules §12.1) is what trading with the market costs:
 * {@code ratio} of one resource for 1 of another (0 = no trading with the market). {@code
 * victoryMode}, {@code dominationPercent}, {@code victoryHoldHours}, {@code keepCount} and {@code
 * keepPercent} (v1.3, 02-game-rules §22) decide how the world is won. {@code villageNameLanguage}
 * (v1.3, 02-game-rules §2) is the language of the names the game gives villages.
 *
 * <p>Built by name through {@link Builder} (1.3); the canonical constructor is called only by
 * {@link Builder#build()}. {@code WorldSettingsTest} checks by reflection that every setting has
 * its builder method, survives {@link #toBuilder()} and is handed on by {@code
 * WorldSettingsEntity}, so a forgotten line fails a test instead of falling back to the default.
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
        double bannerDropFactor,
        double marketExchangeRatio,
        VictoryMode victoryMode,
        int dominationPercent,
        int victoryHoldHours,
        int keepCount,
        int keepPercent,
        VillageNameLanguage villageNameLanguage) {

    /**
     * The defaults of the table in 02-game-rules §1 (speed 1; the invite code is a placeholder):
     * the initial values of {@link Builder}.
     */
    public static WorldSettings defaults() {
        return builder().build();
    }

    /** A builder that starts at the defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding these settings, to change some of them by name. */
    public Builder toBuilder() {
        return new Builder()
                .name(name)
                .speed(speed)
                .unitSpeed(unitSpeed)
                .mapSize(mapSize)
                .startResources(startResources)
                .luckRange(luckRange)
                .beginnerProtectionHours(beginnerProtectionHours)
                .nobleMaxDistance(nobleMaxDistance)
                .loyaltyRegenPerHour(loyaltyRegenPerHour)
                .buildQueueSize(buildQueueSize)
                .buildQueueMax(buildQueueMax)
                .recruitQueueSize(recruitQueueSize)
                .cancelWindowSeconds(cancelWindowSeconds)
                .barbarianInitialCount(barbarianInitialCount)
                .barbariansPerNewPlayer(barbariansPerNewPlayer)
                .barbarianGrowthEnabled(barbarianGrowthEnabled)
                .tradeMaxRatio(tradeMaxRatio)
                .inviteCode(inviteCode)
                .npcLordCount(npcLordCount)
                .npcDifficulty(npcDifficulty)
                .npcThinkMinutes(npcThinkMinutes)
                .npcCustom(npcCustom)
                .guildsEnabled(guildsEnabled)
                .guildMaxMembers(guildMaxMembers)
                .barbarianWallsEnabled(barbarianWallsEnabled)
                .barbarianWallStartDays(barbarianWallStartDays)
                .barbarianWallMaxLevel(barbarianWallMaxLevel)
                .barbarianWallsRebuild(barbarianWallsRebuild)
                .unitLevelsEnabled(unitLevelsEnabled)
                .paladinVillagesPerSlot(paladinVillagesPerSlot)
                .paladinMaxSlots(paladinMaxSlots)
                .bannersEnabled(bannersEnabled)
                .bannerDropFactor(bannerDropFactor)
                .marketExchangeRatio(marketExchangeRatio)
                .victoryMode(victoryMode)
                .dominationPercent(dominationPercent)
                .victoryHoldHours(victoryHoldHours)
                .keepCount(keepCount)
                .keepPercent(keepPercent)
                .villageNameLanguage(villageNameLanguage);
    }

    /** Same settings with another unit speed (tests, balancing). */
    public WorldSettings withUnitSpeed(double newUnitSpeed) {
        return toBuilder().unitSpeed(newUnitSpeed).build();
    }

    /** Same settings with another world speed (tests, balancing). */
    public WorldSettings withSpeed(double newSpeed) {
        return toBuilder().speed(newSpeed).build();
    }

    /** Same settings with other barbarian wall settings (M29; tests, balancing). */
    public WorldSettings withBarbarianWalls(
            boolean enabled, int startDays, int maxLevel, boolean rebuild) {
        return toBuilder()
                .barbarianWallsEnabled(enabled)
                .barbarianWallStartDays(startDays)
                .barbarianWallMaxLevel(maxLevel)
                .barbarianWallsRebuild(rebuild)
                .build();
    }

    /** Same settings with unit levels switched on or off (M30; tests, balancing). */
    public WorldSettings withUnitLevels(boolean enabled) {
        return toBuilder().unitLevelsEnabled(enabled).build();
    }

    /** Same settings with other paladin settings (M31; tests, balancing). */
    public WorldSettings withPaladin(int villagesPerSlot, int maxSlots) {
        return toBuilder()
                .paladinVillagesPerSlot(villagesPerSlot)
                .paladinMaxSlots(maxSlots)
                .build();
    }

    /** Same settings with other banner settings (M32; tests, balancing). */
    public WorldSettings withBanners(boolean enabled, double dropFactor) {
        return toBuilder().bannersEnabled(enabled).bannerDropFactor(dropFactor).build();
    }

    /**
     * The settings by name (1.3: the record has 39 components, and two swapped arguments of the
     * same type would compile). Starts at the defaults; {@link #build()} is the only place that
     * calls the canonical constructor, so a new setting is added here, in {@link #toBuilder()} and
     * where it is read ({@code WorldSettingsEntity.toSettings()}).
     */
    public static final class Builder {
        private String name = "Bannerfall 1";
        private double speed = 1.0;
        private double unitSpeed = 1.0;
        private int mapSize = 100;
        private int startResources = 500;
        private double luckRange = 0.25;
        private int beginnerProtectionHours = 72;
        private int nobleMaxDistance = 70;
        private double loyaltyRegenPerHour = 1.0;
        private int buildQueueSize = 5;
        private int buildQueueMax = 10;
        private int recruitQueueSize = 5;
        private int cancelWindowSeconds = 600;
        private int barbarianInitialCount = 30;
        private int barbariansPerNewPlayer = 3;
        private boolean barbarianGrowthEnabled = true;
        private double tradeMaxRatio = 3.0;
        private String inviteCode = "change-me";
        private int npcLordCount = 4;
        private NpcDifficulty npcDifficulty = NpcDifficulty.NORMAL;
        private int npcThinkMinutes = 20;
        private NpcBalanceSettings.Difficulty npcCustom = null;
        private boolean guildsEnabled = false;
        private int guildMaxMembers = 10;
        private boolean barbarianWallsEnabled = false;
        private int barbarianWallStartDays = 3;
        private int barbarianWallMaxLevel = 5;
        private boolean barbarianWallsRebuild = false;
        private boolean unitLevelsEnabled = false;
        private int paladinVillagesPerSlot = 4;
        private int paladinMaxSlots = 5;
        private boolean bannersEnabled = true;
        private double bannerDropFactor = 1.0;
        private double marketExchangeRatio = 1.5;
        private VictoryMode victoryMode = VictoryMode.DOMINATION;
        private int dominationPercent = 60;
        private int victoryHoldHours = 48;
        private int keepCount = 5;
        private int keepPercent = 51;
        private VillageNameLanguage villageNameLanguage = VillageNameLanguage.EN;

        private Builder() {}

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder speed(double value) {
            this.speed = value;
            return this;
        }

        public Builder unitSpeed(double value) {
            this.unitSpeed = value;
            return this;
        }

        public Builder mapSize(int value) {
            this.mapSize = value;
            return this;
        }

        public Builder startResources(int value) {
            this.startResources = value;
            return this;
        }

        public Builder luckRange(double value) {
            this.luckRange = value;
            return this;
        }

        public Builder beginnerProtectionHours(int value) {
            this.beginnerProtectionHours = value;
            return this;
        }

        public Builder nobleMaxDistance(int value) {
            this.nobleMaxDistance = value;
            return this;
        }

        public Builder loyaltyRegenPerHour(double value) {
            this.loyaltyRegenPerHour = value;
            return this;
        }

        public Builder buildQueueSize(int value) {
            this.buildQueueSize = value;
            return this;
        }

        public Builder buildQueueMax(int value) {
            this.buildQueueMax = value;
            return this;
        }

        public Builder recruitQueueSize(int value) {
            this.recruitQueueSize = value;
            return this;
        }

        public Builder cancelWindowSeconds(int value) {
            this.cancelWindowSeconds = value;
            return this;
        }

        public Builder barbarianInitialCount(int value) {
            this.barbarianInitialCount = value;
            return this;
        }

        public Builder barbariansPerNewPlayer(int value) {
            this.barbariansPerNewPlayer = value;
            return this;
        }

        public Builder barbarianGrowthEnabled(boolean value) {
            this.barbarianGrowthEnabled = value;
            return this;
        }

        public Builder tradeMaxRatio(double value) {
            this.tradeMaxRatio = value;
            return this;
        }

        public Builder inviteCode(String value) {
            this.inviteCode = value;
            return this;
        }

        public Builder npcLordCount(int value) {
            this.npcLordCount = value;
            return this;
        }

        public Builder npcDifficulty(NpcDifficulty value) {
            this.npcDifficulty = value;
            return this;
        }

        public Builder npcThinkMinutes(int value) {
            this.npcThinkMinutes = value;
            return this;
        }

        public Builder npcCustom(NpcBalanceSettings.Difficulty value) {
            this.npcCustom = value;
            return this;
        }

        public Builder guildsEnabled(boolean value) {
            this.guildsEnabled = value;
            return this;
        }

        public Builder guildMaxMembers(int value) {
            this.guildMaxMembers = value;
            return this;
        }

        public Builder barbarianWallsEnabled(boolean value) {
            this.barbarianWallsEnabled = value;
            return this;
        }

        public Builder barbarianWallStartDays(int value) {
            this.barbarianWallStartDays = value;
            return this;
        }

        public Builder barbarianWallMaxLevel(int value) {
            this.barbarianWallMaxLevel = value;
            return this;
        }

        public Builder barbarianWallsRebuild(boolean value) {
            this.barbarianWallsRebuild = value;
            return this;
        }

        public Builder unitLevelsEnabled(boolean value) {
            this.unitLevelsEnabled = value;
            return this;
        }

        public Builder paladinVillagesPerSlot(int value) {
            this.paladinVillagesPerSlot = value;
            return this;
        }

        public Builder paladinMaxSlots(int value) {
            this.paladinMaxSlots = value;
            return this;
        }

        public Builder bannersEnabled(boolean value) {
            this.bannersEnabled = value;
            return this;
        }

        public Builder bannerDropFactor(double value) {
            this.bannerDropFactor = value;
            return this;
        }

        public Builder marketExchangeRatio(double value) {
            this.marketExchangeRatio = value;
            return this;
        }

        public Builder victoryMode(VictoryMode value) {
            this.victoryMode = value;
            return this;
        }

        public Builder dominationPercent(int value) {
            this.dominationPercent = value;
            return this;
        }

        public Builder victoryHoldHours(int value) {
            this.victoryHoldHours = value;
            return this;
        }

        public Builder keepCount(int value) {
            this.keepCount = value;
            return this;
        }

        public Builder keepPercent(int value) {
            this.keepPercent = value;
            return this;
        }

        public Builder villageNameLanguage(VillageNameLanguage value) {
            this.villageNameLanguage = value;
            return this;
        }

        public WorldSettings build() {
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
                    bannersEnabled,
                    bannerDropFactor,
                    marketExchangeRatio,
                    victoryMode,
                    dominationPercent,
                    victoryHoldHours,
                    keepCount,
                    keepPercent,
                    villageNameLanguage);
        }
    }
}
