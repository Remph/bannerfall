package de.bannerfall.farm;

import de.bannerfall.unit.UnitCounts;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * One entry of a village's farm list (02-game-rules §18): the target village and the units the
 * player usually sends. Changed only under the lock of its village.
 */
@Entity
public class FarmTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The village whose list it is, and whose units are sent. */
    private Long villageId;

    private Long targetVillageId;

    @Embedded private UnitCounts units;

    /**
     * The player's template the entry uses (1.4), or null for its own units. {@link #units} is
     * always the units it sends: a copy of the template's, changed with it.
     */
    private Long templateId;

    private Instant createdAt;

    /** When the helper last sent this template; null if never. */
    private Instant lastSentAt;

    /**
     * The latest attack from the village at the target that landed (1.6), when it landed; null
     * before the first. The three below belong to it.
     */
    private Instant lastRaidAt;

    /** Whether the attackers won that battle. */
    private Boolean lastRaidWon;

    /** What they took home, all resources together (0 when they lost). */
    private Integer lastRaidLoot;

    /** What the survivors could carry, Plunder banner included (0 when they lost). */
    private Integer lastRaidCapacity;

    protected FarmTarget() {}

    FarmTarget(
            long villageId,
            long targetVillageId,
            UnitCounts units,
            Long templateId,
            Instant createdAt) {
        this.villageId = villageId;
        this.targetVillageId = targetVillageId;
        this.units = units;
        this.templateId = templateId;
        this.createdAt = createdAt;
    }

    /** What the entry sends from now on: a template's units (its id) or its own (null). */
    void setUnits(UnitCounts units, Long templateId) {
        this.units = units;
        this.templateId = templateId;
    }

    /** The template's new units, for an entry that uses it. */
    void followTemplate(UnitCounts units) {
        this.units = units.copy();
    }

    void sentAt(Instant now) {
        this.lastSentAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getVillageId() {
        return villageId;
    }

    public Long getTargetVillageId() {
        return targetVillageId;
    }

    public UnitCounts getUnits() {
        return units;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public Instant getLastRaidAt() {
        return lastRaidAt;
    }

    public Boolean getLastRaidWon() {
        return lastRaidWon;
    }

    public Integer getLastRaidLoot() {
        return lastRaidLoot;
    }

    public Integer getLastRaidCapacity() {
        return lastRaidCapacity;
    }
}
