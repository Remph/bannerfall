package de.bannerfall.report;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One report for one player (02-game-rules §14). Created once, never changed as an entity: the read
 * flag and deleting go through bulk queries in {@link ReportRepository}, so a player opening and
 * deleting a report at the same time can't clash. No {@code @Version} for that reason.
 */
@Entity
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The recipient; a plain id, reports are loaded by player. */
    private long playerId;

    @Enumerated(EnumType.STRING)
    private ReportKind kind;

    /** Game time of what happened (the event's due time), not when it was processed. */
    private Instant happenedAt;

    private boolean read;

    /**
     * Never changed after creation. {@code @Immutable} spares Hibernate the deep copy (a JSON round
     * trip through the converter) and the comparison at flush that it does for converted values.
     */
    @Convert(converter = ReportPayloadConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Immutable
    private ReportPayload payload;

    private Instant createdAt;

    /** Who sent this copy (M40); null for a report the game created. Plain id, name a snapshot. */
    private Long sharedById;

    private String sharedByName;

    /** Whose report the copy shows ("you" in its payload is that player); null if not a copy. */
    private Long viewOfId;

    private String viewOfName;

    protected Report() {}

    /**
     * A new, unread report.
     *
     * @param payload must be the payload type of {@code kind}
     * @param createdAt wall-clock time of creating the row
     */
    public Report(
            long playerId,
            ReportKind kind,
            Instant happenedAt,
            ReportPayload payload,
            Instant createdAt) {
        if (!kind.payloadType().isInstance(payload)) {
            throw new IllegalArgumentException(
                    "A " + kind + " report needs a " + kind.payloadType().getSimpleName());
        }
        this.playerId = playerId;
        this.kind = kind;
        this.happenedAt = happenedAt;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /**
     * A copy of {@code original} that {@code senderId} sends to {@code recipientId} (M40): the same
     * kind, time and payload. A copy sent on keeps the player whose view it shows.
     *
     * @param read true for a copy the recipient won't see (he blocked the sender)
     */
    static Report sharedCopy(
            Report original,
            long recipientId,
            long senderId,
            String senderName,
            boolean read,
            Instant createdAt) {
        Report copy =
                new Report(
                        recipientId,
                        original.kind,
                        original.happenedAt,
                        original.payload,
                        createdAt);
        copy.read = read;
        copy.sharedById = senderId;
        copy.sharedByName = senderName;
        boolean copyOfACopy = original.viewOfId != null;
        copy.viewOfId = copyOfACopy ? original.viewOfId : original.playerId;
        copy.viewOfName = copyOfACopy ? original.viewOfName : senderName;
        return copy;
    }

    public Long getId() {
        return id;
    }

    public long getPlayerId() {
        return playerId;
    }

    public ReportKind getKind() {
        return kind;
    }

    public Instant getHappenedAt() {
        return happenedAt;
    }

    public boolean isRead() {
        return read;
    }

    public ReportPayload getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Who sent this copy and whose view it shows (M40); null for a report the game created. */
    public ReportSharedBy getSharedBy() {
        return sharedById == null
                ? null
                : new ReportSharedBy(sharedById, sharedByName, viewOfId, viewOfName);
    }
}
