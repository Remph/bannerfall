package de.bannerfall.recruit;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.RecruitRules;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * Units being dismissed in one building's queue of a village (02-game-rules §5.2a). The units left
 * the home stack when the order was placed; the order counts them down one by one, every {@link
 * #getSecondsPerUnit()} seconds from {@link #getStartAt()} on, and each dismissed unit frees its
 * population. The units still waiting ({@link #remaining()}) count against the farm and are put
 * back home by a cancel. No event: the village catch-up marks the finished ones ({@link
 * #completeUpTo}) and deletes the order once all are gone. Change orders only while the village is
 * locked; no {@code @Version} for the same reason as {@link RecruitOrder}.
 */
@Entity
public class DismissOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A plain id: orders are loaded by village, never through the village. */
    private long villageId;

    /** The building that recruits the unit; its dismiss queue is the one this order is in. */
    @Enumerated(EnumType.STRING)
    private BuildingType building;

    @Enumerated(EnumType.STRING)
    private UnitType unit;

    private int count;

    /** Units already dismissed. */
    private int completedCount;

    /** Fixed at enqueue (a fifth of the recruit time of that moment). */
    private int secondsPerUnit;

    private Instant startAt;
    private Instant endAt;
    private Instant createdAt;

    protected DismissOrder() {}

    private DismissOrder(
            long villageId,
            BuildingType building,
            UnitType unit,
            int count,
            int secondsPerUnit,
            Instant startAt,
            Instant now) {
        this.villageId = villageId;
        this.building = building;
        this.unit = unit;
        this.count = count;
        this.completedCount = 0;
        this.secondsPerUnit = secondsPerUnit;
        this.createdAt = now;
        setStart(startAt);
    }

    /** A new order of {@code count} units that starts at {@code startAt}. */
    public static DismissOrder create(
            long villageId,
            BuildingType building,
            UnitType unit,
            int count,
            int secondsPerUnit,
            Instant startAt,
            Instant now) {
        if (count < 1 || secondsPerUnit < 1) {
            throw new IllegalArgumentException(
                    "count and seconds per unit must be at least 1: "
                            + count
                            + ", "
                            + secondsPerUnit);
        }
        return new DismissOrder(villageId, building, unit, count, secondsPerUnit, startAt, now);
    }

    /**
     * Marks the units dismissed by {@code t} (the same {@code finishedBy} as for recruiting). An
     * earlier {@code t} than before changes nothing.
     */
    public void completeUpTo(Instant t) {
        int finished = RecruitRules.finishedBy(startAt, secondsPerUnit, count, t);
        if (finished > completedCount) {
            completedCount = finished;
        }
    }

    /** All units are gone (the order can be deleted). */
    public boolean isComplete() {
        return completedCount >= count;
    }

    /** Units still waiting: they count against the farm and go back home on a cancel. */
    public int remaining() {
        return count - completedCount;
    }

    /**
     * Moves an order that hasn't started to another start (re-timing after a cancel).
     *
     * @throws IllegalStateException if units of the order are already dismissed
     */
    public void moveTo(Instant newStartAt) {
        if (completedCount > 0) {
            throw new IllegalStateException(
                    "Dismiss order " + id + " has started and can't be moved");
        }
        setStart(newStartAt);
    }

    private void setStart(Instant newStartAt) {
        startAt = newStartAt;
        endAt = newStartAt.plusSeconds((long) count * secondsPerUnit);
    }

    public Long getId() {
        return id;
    }

    public long getVillageId() {
        return villageId;
    }

    public BuildingType getBuilding() {
        return building;
    }

    public UnitType getUnit() {
        return unit;
    }

    public int getCount() {
        return count;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getSecondsPerUnit() {
        return secondsPerUnit;
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
