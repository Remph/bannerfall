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

    protected WorldSettingsEntity() {}

    public WorldSettings toSettings() {
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
                bannerDropFactor);
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
