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
 * One line of the banner log (02-game-rules §21.9). The banner id has no foreign key: the log
 * outlives a banner that was destroyed or revoked.
 */
@Entity
public class BannerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long bannerId;

    @Enumerated(EnumType.STRING)
    private BannerEventKind kind;

    /** The banner's type and tier when it happened (M32c; null on lines older than V29). */
    @Enumerated(EnumType.STRING)
    private BannerType type;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer tier;

    /** Who had it before; null for a new banner. */
    private Long fromPlayerId;

    /** Who has it now; null when it is gone. */
    private Long toPlayerId;

    /** The village it was equipped in or taken from; null for stash moves. */
    private Long villageId;

    private Instant happenedAt;

    /** Free text: the host's reason for a grant or revoke. */
    private String detail;

    protected BannerEvent() {}

    /** A line about {@code banner}, which has been saved (it has its id). */
    public BannerEvent(
            Banner banner,
            BannerEventKind kind,
            Long fromPlayerId,
            Long toPlayerId,
            Long villageId,
            Instant happenedAt,
            String detail) {
        this.bannerId = banner.getId();
        this.kind = kind;
        this.type = banner.getType();
        this.tier = banner.getTier();
        this.fromPlayerId = fromPlayerId;
        this.toPlayerId = toPlayerId;
        this.villageId = villageId;
        this.happenedAt = happenedAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public long getBannerId() {
        return bannerId;
    }

    public BannerEventKind getKind() {
        return kind;
    }

    public BannerType getType() {
        return type;
    }

    public Integer getTier() {
        return tier;
    }

    public Long getFromPlayerId() {
        return fromPlayerId;
    }

    public Long getToPlayerId() {
        return toPlayerId;
    }

    public Long getVillageId() {
        return villageId;
    }

    public Instant getHappenedAt() {
        return happenedAt;
    }

    public String getDetail() {
        return detail;
    }
}
