package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One banner (02-game-rules §21.1), or one paladin standard (1.4, §21.18): standards are banners of
 * their own family ({@link BannerType#isStandard()}). It belongs to a player and is either in his
 * <b>stash</b> ({@link #getEquippedVillageId()} is null, safe) or <b>equipped</b> in exactly one
 * village (at most one banner and one standard per village: two partial unique indexes). A banner's
 * effect belongs to the village it flies in, a standard's to the village's paladins.
 *
 * <p>A banner changes place only while the village it is in (or is going into) is locked, and a
 * player's own stash and slots only under the lock of his player row ({@code BannerService}); there
 * is no {@code @Version}.
 *
 * <p>Plain ids, like the research orders: banners are loaded by player or by village.
 */
@Entity
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long ownerId;

    @Enumerated(EnumType.STRING)
    private BannerType type;

    /** 1–3. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int tier;

    /** The village it flies in; null = in the owner's stash. */
    private Long equippedVillageId;

    /** Since when it flies there; null in the stash. */
    private Instant equippedAt;

    @Enumerated(EnumType.STRING)
    private BannerOrigin origin;

    private Instant createdAt;

    protected Banner() {}

    /** A new banner in the owner's stash. */
    public Banner(long ownerId, BannerType type, int tier, BannerOrigin origin, Instant now) {
        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException("tier must be 1..3: " + tier);
        }
        this.ownerId = ownerId;
        this.type = type;
        this.tier = tier;
        this.origin = origin;
        this.createdAt = now;
    }

    /** Puts the banner into the village's slot. The caller has checked slot, hall and locks. */
    public void equip(long villageId, Instant now) {
        this.equippedVillageId = villageId;
        this.equippedAt = now;
    }

    /** Takes the banner out of its slot into the owner's stash. */
    public void unequip() {
        this.equippedVillageId = null;
        this.equippedAt = null;
    }

    /** The banner goes to another player's stash (a conquest, M32b theft). */
    public void passTo(long newOwnerId) {
        this.ownerId = newOwnerId;
        unequip();
    }

    /** An NPC lord conquered the village the banner flies in: it stays there, now his. */
    public void holdFor(long newOwnerId) {
        this.ownerId = newOwnerId;
    }

    public boolean isEquipped() {
        return equippedVillageId != null;
    }

    /** A paladin standard (1.4, 02-game-rules §21.18) rather than a village banner. */
    public boolean isStandard() {
        return type.isStandard();
    }

    public Long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public BannerType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public Long getEquippedVillageId() {
        return equippedVillageId;
    }

    public Instant getEquippedAt() {
        return equippedAt;
    }

    public BannerOrigin getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
