package de.bannerfall.world;

import de.bannerfall.npc.NpcBalanceSettings;
import de.bannerfall.npc.NpcDifficulty;
import de.bannerfall.npc.NpcDifficultyConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The single row of {@code world_settings}. Game code never reads it directly: {@link WorldService}
 * turns it into the immutable {@link WorldSettings} record and caches that.
 */
@Entity
@Table(name = "world_settings")
public class WorldSettingsEntity {

    /** Always 1 (check constraint). */
    @Id private short id;

    private String name;
    private double speed;
    private double unitSpeed;
    private int mapSize;
    private int startResources;
    private double luckRange;
    private int beginnerProtectionHours;
    private int nobleMaxDistance;
    private double loyaltyRegenPerHour;
    private int buildQueueSize;
    private int buildQueueMax;
    private int recruitQueueSize;
    private int cancelWindowSeconds;
    private int barbarianInitialCount;
    private int barbariansPerNewPlayer;
    private boolean barbarianGrowthEnabled;
    private double tradeMaxRatio;
    private String inviteCode;
    private int npcLordCount;

    @Enumerated(EnumType.STRING)
    private NpcDifficulty npcDifficulty;

    private int npcThinkMinutes;

    /** Guilds can be created, invited to and joined (M15a). */
    private boolean guildsEnabled;

    private int guildMaxMembers;

    /** NPC lords form guilds (09-npc-lords §13). */
    private boolean npcGuildsEnabled;

    private int npcGuildMaxMembers;

    /** Barbarian walls (M29, 02-game-rules §11): see {@code Barbarians}. */
    private boolean barbarianWallsEnabled;

    private int barbarianWallStartDays;
    private int barbarianWallMaxLevel;
    private boolean barbarianWallsRebuild;

    /** Unit levels (M30, 02-game-rules §5.4). */
    private boolean unitLevelsEnabled;

    /** The paladin (M31, 02-game-rules §5.5). */
    private int paladinVillagesPerSlot;

    private int paladinMaxSlots;

    /** Banners (M32, 02-game-rules §21.9). */
    private boolean bannersEnabled;

    private double bannerDropFactor;

    /** Trading with the market (v1.3, 02-game-rules §12.1); 0 = off. */
    private double marketExchangeRatio;

    /** How the world is won (v1.3, 02-game-rules §22). */
    @Enumerated(EnumType.STRING)
    private VictoryMode victoryMode;

    private int dominationPercent;
    private int victoryHoldHours;
    private int keepCount;
    private int keepPercent;

    /** The language of the names the game gives villages (v1.3, 02-game-rules §2). */
    @Enumerated(EnumType.STRING)
    private VillageNameLanguage villageNameLanguage;

    /** The CUSTOM difficulty's values; null until the admin first picks CUSTOM. */
    @Convert(converter = NpcDifficultyConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private NpcBalanceSettings.Difficulty npcCustom;

    private Instant startedAt;

    /** When the world's content was created ({@code WorldInitializer}); null before. */
    private Instant initializedAt;

    /** True from the first start of a fresh production world until it opens (M14g). */
    private boolean pregame;

    /** When the pre-game ends; null = the host has not set a time (M14g). */
    private Instant opensAt;

    /** The end date the host set (v1.3, 02-game-rules §22); null = none. */
    private Instant endsAt;

    /** When the world ended; null while it runs. Set = the world is frozen (§22). */
    private Instant endedAt;

    /**
     * The side that meets the victory condition at the last check ({@code VictorySide.key()}), null
     * if none; {@link #victoryLeaderSince} is when it first met it without a break.
     */
    private String victoryLeader;

    private Instant victoryLeaderSince;

    protected WorldSettingsEntity() {}

    public WorldSettings toSettings() {
        return WorldSettings.builder()
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
                .marketExchangeRatio(marketExchangeRatio)
                .victoryMode(victoryMode)
                .dominationPercent(dominationPercent)
                .victoryHoldHours(victoryHoldHours)
                .keepCount(keepCount)
                .keepPercent(keepPercent)
                .villageNameLanguage(villageNameLanguage)
                .build();
    }

    /** When the world was created (the V2 migration ran). Not part of {@link WorldSettings}. */
    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getInitializedAt() {
        return initializedAt;
    }

    /** The content exists from now on; that ends the pre-game, if there was one. */
    public void markInitialized(Instant at) {
        this.initializedAt = at;
        this.pregame = false;
    }

    public boolean isPregame() {
        return pregame;
    }

    public void setPregame(boolean pregame) {
        this.pregame = pregame;
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public void setOpensAt(Instant opensAt) {
        this.opensAt = opensAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getVictoryLeader() {
        return victoryLeader;
    }

    public Instant getVictoryLeaderSince() {
        return victoryLeaderSince;
    }

    /** The side that meets the condition since {@code since}; both null = nobody. */
    public void setVictoryLeader(String leader, Instant since) {
        this.victoryLeader = leader;
        this.victoryLeaderSince = since;
    }

    /** Pre-game only: what is fixed once the content is created (M14g). */
    void applySetup(Integer newMapSize, Integer newBarbarianInitialCount, Integer newNpcLordCount) {
        if (newMapSize != null) {
            mapSize = newMapSize;
        }
        if (newBarbarianInitialCount != null) {
            barbarianInitialCount = newBarbarianInitialCount;
        }
        if (newNpcLordCount != null) {
            npcLordCount = newNpcLordCount;
        }
    }

    /**
     * Applies the admin's changes (M14a); what {@link WorldSettingsUpdateRequest} leaves out stays.
     */
    void apply(WorldSettingsUpdateRequest u) {
        name = u.name();
        speed = u.speed();
        unitSpeed = u.unitSpeed();
        startResources = u.startResources();
        luckRange = u.luckRange();
        beginnerProtectionHours = u.beginnerProtectionHours();
        nobleMaxDistance = u.nobleMaxDistance();
        loyaltyRegenPerHour = u.loyaltyRegenPerHour();
        buildQueueSize = u.buildQueueSize();
        buildQueueMax = u.buildQueueMax();
        recruitQueueSize = u.recruitQueueSize();
        cancelWindowSeconds = u.cancelWindowSeconds();
        barbariansPerNewPlayer = u.barbariansPerNewPlayer();
        barbarianGrowthEnabled = u.barbarianGrowthEnabled();
        tradeMaxRatio = u.tradeMaxRatio();
        inviteCode = u.inviteCode().strip();
        npcThinkMinutes = u.npcThinkMinutes();
        // Left out (null) = unchanged, so an older client or script keeps working (M15a).
        if (u.guildsEnabled() != null) {
            guildsEnabled = u.guildsEnabled();
        }
        if (u.guildMaxMembers() != null) {
            guildMaxMembers = u.guildMaxMembers();
        }
        if (u.npcGuildsEnabled() != null) {
            npcGuildsEnabled = u.npcGuildsEnabled();
        }
        if (u.npcGuildMaxMembers() != null) {
            npcGuildMaxMembers = u.npcGuildMaxMembers();
        }
        if (u.barbarianWallsEnabled() != null) {
            barbarianWallsEnabled = u.barbarianWallsEnabled();
        }
        if (u.barbarianWallStartDays() != null) {
            barbarianWallStartDays = u.barbarianWallStartDays();
        }
        if (u.barbarianWallMaxLevel() != null) {
            barbarianWallMaxLevel = u.barbarianWallMaxLevel();
        }
        if (u.barbarianWallsRebuild() != null) {
            barbarianWallsRebuild = u.barbarianWallsRebuild();
        }
        if (u.unitLevelsEnabled() != null) {
            unitLevelsEnabled = u.unitLevelsEnabled();
        }
        if (u.paladinVillagesPerSlot() != null) {
            paladinVillagesPerSlot = u.paladinVillagesPerSlot();
        }
        if (u.paladinMaxSlots() != null) {
            paladinMaxSlots = u.paladinMaxSlots();
        }
        if (u.bannersEnabled() != null) {
            bannersEnabled = u.bannersEnabled();
        }
        if (u.bannerDropFactor() != null) {
            bannerDropFactor = u.bannerDropFactor();
        }
        if (u.marketExchangeRatio() != null) {
            marketExchangeRatio = u.marketExchangeRatio();
        }
        if (u.victoryMode() != null) {
            victoryMode = u.victoryMode();
        }
        if (u.dominationPercent() != null) {
            dominationPercent = u.dominationPercent();
        }
        if (u.victoryHoldHours() != null) {
            victoryHoldHours = u.victoryHoldHours();
        }
        if (u.keepCount() != null) {
            keepCount = u.keepCount();
        }
        if (u.keepPercent() != null) {
            keepPercent = u.keepPercent();
        }
        if (u.villageNameLanguage() != null) {
            villageNameLanguage = u.villageNameLanguage();
        }
    }

    public void setNpcDifficulty(NpcDifficulty difficulty) {
        this.npcDifficulty = difficulty;
    }

    public NpcBalanceSettings.Difficulty getNpcCustom() {
        return npcCustom;
    }

    public void setNpcCustom(NpcBalanceSettings.Difficulty custom) {
        this.npcCustom = custom;
    }
}
