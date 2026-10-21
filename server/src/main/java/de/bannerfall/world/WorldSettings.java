package de.bannerfall.world;

import de.bannerfall.npc.NpcBalanceSettings;
import de.bannerfall.npc.NpcDifficulty;
import de.bannerfall.rules.Morale;
import de.bannerfall.rules.NightBonus;
import java.time.ZoneId;

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
 * won battle drops one (0 = no drops; the economy effects of a banner already in a village stay);
 * {@code standardTheftEnabled} (1.4, §21.18) lets the defenders' thieves take the paladin standard
 * of an attack they beat; {@code npcBannersEnabled} (1.4, 09-npc-lords §14) lets NPC lords find,
 * fly, fuse and lose banners and standards like players, only while banners are on; {@code
 * npcLearningEnabled} (1.6, 09 §15) lets NPC lords adapt their troop mix and wall to what attacks
 * them and to what wins for human players. {@code npcShipmentsEnabled} (1.6, 09-npc-lords §5.5)
 * lets them ship what would overflow to their own villages that still build. {@code
 * marketExchangeRatio} (v1.3, 02-game-rules §12.1) is what trading with the market costs: {@code
 * ratio} of one resource for 1 of another (0 = no trading with the market). {@code victoryMode},
 * {@code dominationPercent}, {@code victoryHoldHours}, {@code keepCount} and {@code keepPercent}
 * (v1.3, 02-game-rules §22) decide how the world is won. {@code villageNameLanguage} (v1.3,
 * 02-game-rules §2) is the language of the names the game gives villages. {@code mapSpread} (1.6,
 * §2) is how far apart the sides start: every placement and NPC radius is scaled by it. {@code
 * nightBonusEnabled}, {@code nightBonusStartHour}, {@code nightBonusEndHour} and {@code
 * nightBonusFactor} (1.4, 02-game-rules §8.1) make owned villages defend more strongly against
 * attacks that arrive at night. {@code archersEnabled} (1.6, 02-game-rules §5.6) lets players and
 * NPC lords recruit the archer and the mounted archer; archers that exist keep fighting when it is
 * switched off. {@code moraleEnabled} and {@code moraleFloor} (1.6, 02-game-rules §8.2, the floor
 * in percent) weaken attacks on much smaller players. {@code noblePriceStep} (1.6, 02-game-rules
 * §5.7) makes every village and noble beyond the free rank raise a player's next noble price by
 * that share of the base price (0 = the flat price). {@code automationVillages} (1.6, 02-game-rules
 * §25) is the number of villages from which a player's villages can work through build plans by
 * themselves (0 = off). {@code guildStartTogether} (1.6, 02-game-rules §2) places guild mates next
 * to each other at the opening and on a respawn.
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
        MapSpread mapSpread,
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
        boolean guildStartTogether,
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
        boolean npcLearningEnabled,
        boolean npcShipmentsEnabled,
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
        boolean archersEnabled,
        boolean moraleEnabled,
        int moraleFloor,
        double noblePriceStep,
        int automationVillages) {

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
                .mapSpread(mapSpread)
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
                .guildStartTogether(guildStartTogether)
                .npcGuildsEnabled(npcGuildsEnabled)
                .npcGuildMaxMembers(npcGuildMaxMembers)
                .barbarianWallsEnabled(barbarianWallsEnabled)
                .barbarianWallStartDays(barbarianWallStartDays)
                .barbarianWallMaxLevel(barbarianWallMaxLevel)
                .barbarianWallsRebuild(barbarianWallsRebuild)
                .unitLevelsEnabled(unitLevelsEnabled)
                .paladinVillagesPerSlot(paladinVillagesPerSlot)
                .paladinMaxSlots(paladinMaxSlots)
                .bannersEnabled(bannersEnabled)
                .bannerDropFactor(bannerDropFactor)
                .standardTheftEnabled(standardTheftEnabled)
                .npcBannersEnabled(npcBannersEnabled)
                .npcLearningEnabled(npcLearningEnabled)
                .npcShipmentsEnabled(npcShipmentsEnabled)
                .marketExchangeRatio(marketExchangeRatio)
                .victoryMode(victoryMode)
                .dominationPercent(dominationPercent)
                .victoryHoldHours(victoryHoldHours)
                .keepCount(keepCount)
                .keepPercent(keepPercent)
                .villageNameLanguage(villageNameLanguage)
                .nightBonusEnabled(nightBonusEnabled)
                .nightBonusStartHour(nightBonusStartHour)
                .nightBonusEndHour(nightBonusEndHour)
                .nightBonusFactor(nightBonusFactor)
                .archersEnabled(archersEnabled)
                .moraleEnabled(moraleEnabled)
                .moraleFloor(moraleFloor)
                .noblePriceStep(noblePriceStep)
                .automationVillages(automationVillages);
    }

    /** The night bonus of this world (1.4, 02-game-rules §8.1), its hours read in {@code zone}. */
    public NightBonus nightBonus(ZoneId zone) {
        return new NightBonus(
                nightBonusEnabled, nightBonusStartHour, nightBonusEndHour, nightBonusFactor, zone);
    }

    /** The morale of this world (1.6, 02-game-rules §8.2). */
    public Morale morale() {
        return new Morale(moraleEnabled, moraleFloor / 100.0);
    }

    /** Same settings with morale switched on or off (1.6; tests, balancing). */
    public WorldSettings withMorale(boolean enabled) {
        return toBuilder().moraleEnabled(enabled).build();
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

    /**
     * Whether the Banner Hall can be built (1.4, 02-game-rules §21.3): it trains the paladin since
     * 1.4 and serves the banners, so it is there while either of them is on.
     */
    public boolean bannerHallAvailable() {
        return bannersEnabled || paladinMaxSlots > 0;
    }

    /**
     * Whether NPC lords take part in banners and standards (1.4, 09-npc-lords §14): the lord
     * setting and the banners themselves are on.
     */
    public boolean lordBanners() {
        return bannersEnabled && npcBannersEnabled;
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
        private MapSpread mapSpread = MapSpread.NORMAL;
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
        private boolean guildStartTogether = true;
        private boolean npcGuildsEnabled = false;
        private int npcGuildMaxMembers = 3;
        private boolean barbarianWallsEnabled = false;
        private int barbarianWallStartDays = 3;
        private int barbarianWallMaxLevel = 5;
        private boolean barbarianWallsRebuild = false;
        private boolean unitLevelsEnabled = false;
        private int paladinVillagesPerSlot = 4;
        private int paladinMaxSlots = 5;
        private boolean bannersEnabled = true;
        private double bannerDropFactor = 1.0;
        private boolean standardTheftEnabled = true;
        private boolean npcBannersEnabled = false;
        private boolean npcLearningEnabled = false;
        private boolean npcShipmentsEnabled = true;
        private double marketExchangeRatio = 1.5;
        private VictoryMode victoryMode = VictoryMode.DOMINATION;
        private int dominationPercent = 60;
        private int victoryHoldHours = 48;
        private int keepCount = 5;
        private int keepPercent = 51;
        private VillageNameLanguage villageNameLanguage = VillageNameLanguage.EN;
        private boolean nightBonusEnabled = false;
        private int nightBonusStartHour = 23;
        private int nightBonusEndHour = 7;
        private double nightBonusFactor = 2.0;
        private boolean archersEnabled = false;
        private boolean moraleEnabled = true;
        private int moraleFloor = 30;
        private double noblePriceStep = 0.25;
        private int automationVillages = 3;

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

        public Builder mapSpread(MapSpread value) {
            this.mapSpread = value;
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

        public Builder guildStartTogether(boolean value) {
            this.guildStartTogether = value;
            return this;
        }

        public Builder npcGuildsEnabled(boolean value) {
            this.npcGuildsEnabled = value;
            return this;
        }

        public Builder npcGuildMaxMembers(int value) {
            this.npcGuildMaxMembers = value;
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

        public Builder standardTheftEnabled(boolean value) {
            this.standardTheftEnabled = value;
            return this;
        }

        public Builder npcBannersEnabled(boolean value) {
            this.npcBannersEnabled = value;
            return this;
        }

        public Builder npcLearningEnabled(boolean value) {
            this.npcLearningEnabled = value;
            return this;
        }

        public Builder npcShipmentsEnabled(boolean value) {
            this.npcShipmentsEnabled = value;
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

        public Builder nightBonusEnabled(boolean value) {
            this.nightBonusEnabled = value;
            return this;
        }

        public Builder nightBonusStartHour(int value) {
            this.nightBonusStartHour = value;
            return this;
        }

        public Builder nightBonusEndHour(int value) {
            this.nightBonusEndHour = value;
            return this;
        }

        public Builder nightBonusFactor(double value) {
            this.nightBonusFactor = value;
            return this;
        }

        public Builder archersEnabled(boolean value) {
            this.archersEnabled = value;
            return this;
        }

        public Builder moraleEnabled(boolean value) {
            this.moraleEnabled = value;
            return this;
        }

        public Builder moraleFloor(int value) {
            this.moraleFloor = value;
            return this;
        }

        public Builder noblePriceStep(double value) {
            this.noblePriceStep = value;
            return this;
        }

        public Builder automationVillages(int value) {
            this.automationVillages = value;
            return this;
        }

        public WorldSettings build() {
            return new WorldSettings(
                    name,
                    speed,
                    unitSpeed,
                    mapSize,
                    mapSpread,
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
                    guildStartTogether,
                    npcGuildsEnabled,
                    npcGuildMaxMembers,
                    barbarianWallsEnabled,
                    barbarianWallStartDays,
                    barbarianWallMaxLevel,
                    barbarianWallsRebuild,
                    unitLevelsEnabled,
                    paladinVillagesPerSlot,
                    paladinMaxSlots,
                    bannersEnabled,
                    bannerDropFactor,
                    standardTheftEnabled,
                    npcBannersEnabled,
                    npcLearningEnabled,
                    npcShipmentsEnabled,
                    marketExchangeRatio,
                    victoryMode,
                    dominationPercent,
                    victoryHoldHours,
                    keepCount,
                    keepPercent,
                    villageNameLanguage,
                    nightBonusEnabled,
                    nightBonusStartHour,
                    nightBonusEndHour,
                    nightBonusFactor,
                    archersEnabled,
                    moraleEnabled,
                    moraleFloor,
                    noblePriceStep,
                    automationVillages);
        }
    }
}
