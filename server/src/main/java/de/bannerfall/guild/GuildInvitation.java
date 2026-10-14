package de.bannerfall.guild;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A leader's invitation of a player. It stays until it is accepted, declined or withdrawn. */
@Entity
public class GuildInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long guildId;

    /** The invited player. */
    private Long playerId;

    private Long invitedBy;

    private Instant createdAt;

    protected GuildInvitation() {}

    public GuildInvitation(long guildId, long playerId, long invitedBy, Instant createdAt) {
        this.guildId = guildId;
        this.playerId = playerId;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGuildId() {
        return guildId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Long getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
