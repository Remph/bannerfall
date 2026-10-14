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

    private Instant createdAt;

    /** When the helper last sent this template; null if never. */
    private Instant lastSentAt;

    protected FarmTarget() {}

    FarmTarget(long villageId, long targetVillageId, UnitCounts units, Instant createdAt) {
        this.villageId = villageId;
        this.targetVillageId = targetVillageId;
        this.units = units;
        this.createdAt = createdAt;
    }

    void setUnits(UnitCounts units) {
        this.units = units;
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

    public Instant getLastSentAt() {
        return lastSentAt;
    }
}
