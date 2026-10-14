package de.bannerfall.npc;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * What belongs to an NPC lord besides its player row (03-data-model *npc_brain*): personality,
 * template and the memory. Only the lord's own round writes it, one transaction after the other.
 */
@Entity
@Table(name = "npc_brain")
public class NpcBrain {

    /** The lord's player id. */
    @Id private Long playerId;

    @Enumerated(EnumType.STRING)
    private NpcPersonality personality;

    /** Key of the template in {@code gamedata/npc/}; the personality's name for now. */
    private String template;

    private Instant lastThinkAt;

    @Convert(converter = NpcMemoryConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private NpcMemory memory;

    @Version private long version;

    protected NpcBrain() {}

    public NpcBrain(long playerId, NpcPersonality personality) {
        this.playerId = playerId;
        this.personality = personality;
        this.template = personality.name();
        this.memory = NpcMemory.empty();
    }

    public Long getPlayerId() {
        return playerId;
    }

    public NpcPersonality getPersonality() {
        return personality;
    }

    public String getTemplate() {
        return template;
    }

    public Instant getLastThinkAt() {
        return lastThinkAt;
    }

    public NpcMemory getMemory() {
        return memory;
    }

    public void setMemory(NpcMemory memory) {
        this.memory = memory;
    }

    public void thoughtAt(Instant at) {
        this.lastThinkAt = at;
    }
}
