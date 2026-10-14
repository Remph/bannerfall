package de.bannerfall.research;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * One research step of a player's unit (M30, 02-game-rules §5.4): the unit reaches {@link
 * #getLevel()} at {@link #getEndAt()}. The row is kept after that, because the level of a unit at a
 * time is the highest level among its rows that are done by then: nothing is applied when a
 * research finishes (no event, no catch-up). Only a running research can be cancelled; the village
 * where it runs keeps it from running another one. Change orders only while that village is locked.
 *
 * <p>No {@code @Version}: every change happens under the village lock.
 */
@Entity
public class ResearchOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The player whose unit it is: plain ids, orders are loaded by player or village. */
    private long playerId;

    /** The village where the research was started (its smithy is busy until {@link #endAt}). */
    private long villageId;

    @Enumerated(EnumType.STRING)
    private UnitType unit;

    /** The level the unit reaches, 2 or more. */
    private int level;

    private Instant startAt;
    private Instant endAt;

    /** What the research cost, for the refund of a cancel. */
    private int costWood;

    private int costClay;
    private int costIron;

    private Instant createdAt;

    protected ResearchOrder() {}

    private ResearchOrder(
            long playerId,
            long villageId,
            UnitType unit,
            int level,
            Instant startAt,
            Instant endAt,
            ResourceAmounts cost,
            Instant now) {
        this.playerId = playerId;
        this.villageId = villageId;
        this.unit = unit;
        this.level = level;
        this.startAt = startAt;
        this.endAt = endAt;
        this.costWood = cost.wood();
        this.costClay = cost.clay();
        this.costIron = cost.iron();
        this.createdAt = now;
    }

    /** A research that starts at {@code now} and lasts {@code seconds} (at least 1). */
    public static ResearchOrder start(
            long playerId,
            long villageId,
            UnitType unit,
            int level,
            long seconds,
            ResourceAmounts cost,
            Instant now) {
        if (level < 2 || seconds < 1) {
            throw new IllegalArgumentException(
                    "level must be at least 2 and seconds at least 1: " + level + ", " + seconds);
        }
        return new ResearchOrder(
                playerId, villageId, unit, level, now, now.plusSeconds(seconds), cost, now);
    }

    /** Done at {@code t}: the unit has this level from then on. */
    public boolean isDoneBy(Instant t) {
        return !endAt.isAfter(t);
    }

    /** Dev cheat: the research is done now. */
    public void finishNow(Instant now) {
        if (endAt.isAfter(now)) {
            endAt = now;
        }
    }

    /** What a cancel gives back: the whole cost. */
    public ResourceAmounts refund() {
        return new ResourceAmounts(costWood, costClay, costIron);
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

    public UnitType getUnit() {
        return unit;
    }

    public int getLevel() {
        return level;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
