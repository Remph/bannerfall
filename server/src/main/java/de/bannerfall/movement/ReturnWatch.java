package de.bannerfall.movement;

import de.bannerfall.gamedata.ResourceAmounts;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * Attacks whose return their owner wants pushed once (1.6, 01-architecture §4.8b): one manual
 * attack, an attack train or a farm list run. The movements point to it ({@code
 * movement.return_watch_id}); when the last of them is home or died, {@link ReturnWatchService}
 * sends ATTACKS_RETURNED and deletes the row. Changed only under the lock of its village.
 */
@Entity
public class ReturnWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long playerId;

    /** The home village of the attacks. */
    private long villageId;

    /** The one target of a single attack or a train; null for a farm list run. */
    private Long targetVillageId;

    private boolean farmRun;

    /** How many attacks it waits for. */
    private int attacks;

    /** Attacks nobody came back from. */
    private int lost;

    private int lootWood;
    private int lootClay;
    private int lootIron;

    private Instant createdAt;

    protected ReturnWatch() {}

    ReturnWatch(
            long playerId,
            long villageId,
            Long targetVillageId,
            boolean farmRun,
            int attacks,
            Instant createdAt) {
        this.playerId = playerId;
        this.villageId = villageId;
        this.targetVillageId = targetVillageId;
        this.farmRun = farmRun;
        this.attacks = attacks;
        this.createdAt = createdAt;
    }

    /** Units came home with this loot (what they carried; the warehouse may take less). */
    void cameBack(ResourceAmounts loot) {
        lootWood += loot.wood();
        lootClay += loot.clay();
        lootIron += loot.iron();
    }

    /** Nobody survived one of the attacks. */
    void lostOne() {
        lost++;
    }

    /** One attack left the watch (its bell switched off, or it is gone). */
    void leftOne() {
        attacks--;
    }

    public Long getId() {
        return id;
    }

    public long getPlayerId() {
        return playerId;
    }

    public long getVillageId() {
        return villageId;
    }

    public Long getTargetVillageId() {
        return targetVillageId;
    }

    public boolean isFarmRun() {
        return farmRun;
    }

    public int getAttacks() {
        return attacks;
    }

    public int getLost() {
        return lost;
    }

    public ResourceAmounts getLoot() {
        return new ResourceAmounts(lootWood, lootClay, lootIron);
    }
}
