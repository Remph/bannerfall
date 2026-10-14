package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.village.Village;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/**
 * Merchants carrying resources from one village to another (02-game-rules §12). Its TRADE_ARRIVE
 * event is due at {@link #getArriveAt()}; the merchants are busy until {@link #getReturnAt()}, so
 * the row stays (marked {@code arrived}) until then and the nightly cleanup deletes it afterwards.
 *
 * <p>No {@code @Version}: it changes only once, when it arrives, under the target's lock.
 */
@Entity
public class TradeTransport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_village_id")
    private Village sourceVillage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_village_id")
    private Village targetVillage;

    private int wood;
    private int clay;
    private int iron;
    private int merchants;
    private Instant departAt;
    private Instant arriveAt;
    private Instant returnAt;
    private boolean arrived;
    private Instant createdAt;

    protected TradeTransport() {}

    /** Departs now and comes back after twice the travel time. */
    TradeTransport(
            Village source,
            Village target,
            ResourceAmounts resources,
            int merchants,
            Instant now,
            long travelSeconds) {
        this.sourceVillage = source;
        this.targetVillage = target;
        this.wood = resources.wood();
        this.clay = resources.clay();
        this.iron = resources.iron();
        this.merchants = merchants;
        this.departAt = now;
        this.arriveAt = now.plusSeconds(travelSeconds);
        this.returnAt = arriveAt.plusSeconds(travelSeconds);
        this.createdAt = now;
    }

    /** Dev cheat: lets the merchants arrive now (the merchants' way home stays as it was). */
    public void arriveNow(Instant now) {
        if (arriveAt.isAfter(now)) {
            arriveAt = now;
        }
    }

    /**
     * The source village changed owner at {@code at} (code review 1.2, D8): its merchants on the
     * road were the old owner's and don't come back to it. They stop counting as busy and leave its
     * list; the goods still arrive (02-game-rules §10).
     */
    public void lostWithVillage(Instant at) {
        Instant end = departAt.isAfter(at) ? departAt : at;
        if (returnAt.isAfter(end)) {
            returnAt = end;
        }
    }

    void markArrived() {
        arrived = true;
    }

    public Long getId() {
        return id;
    }

    public Village getSourceVillage() {
        return sourceVillage;
    }

    public Village getTargetVillage() {
        return targetVillage;
    }

    public ResourceAmounts getResources() {
        return new ResourceAmounts(wood, clay, iron);
    }

    public int getMerchants() {
        return merchants;
    }

    public Instant getDepartAt() {
        return departAt;
    }

    public Instant getArriveAt() {
        return arriveAt;
    }

    public Instant getReturnAt() {
        return returnAt;
    }

    public boolean isArrived() {
        return arrived;
    }
}
