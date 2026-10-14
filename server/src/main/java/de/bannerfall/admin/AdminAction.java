package de.bannerfall.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the audit log (08-host-console §6). Written once, never changed. */
@Entity
@Immutable
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant at;

    /** The admin's username, or {@code host-cli} for the host token. */
    private String actor;

    private String action;

    private String targetType;

    private Long targetId;

    /** A JSON object, written by {@link AdminAuditService}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    protected AdminAction() {}

    AdminAction(
            Instant at,
            String actor,
            String action,
            String targetType,
            Long targetId,
            String details) {
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }
}
