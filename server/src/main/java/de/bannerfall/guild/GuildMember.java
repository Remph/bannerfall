package de.bannerfall.guild;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A player's membership. The player id is the key: a player is in at most one guild. A plain id
 * instead of a relation, like in the other tables that point at a player.
 */
@Entity
public class GuildMember {

    @Id private Long playerId;

    private Long guildId;

    @Enumerated(EnumType.STRING)
    private GuildRole role;

    private Instant joinedAt;

    protected GuildMember() {}

    public GuildMember(long playerId, long guildId, GuildRole role, Instant joinedAt) {
        this.playerId = playerId;
        this.guildId = guildId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public void setRole(GuildRole role) {
        this.role = role;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Long getGuildId() {
        return guildId;
    }

    public GuildRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
