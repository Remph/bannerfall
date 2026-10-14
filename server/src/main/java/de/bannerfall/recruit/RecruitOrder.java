package de.bannerfall.recruit;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
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
 * Units in training in one building of a village (02-game-rules §5.2). The orders of one building
 * run one after another; the units of an order are done one by one, every {@link
 * #getSecondsPerUnit()} seconds from {@link #getStartAt()} on. No event: the village catch-up moves
 * finished units into the home stack ({@link #completeUpTo}) and deletes the order once all are
 * done. Change orders only while the village is locked.
 *
 * <p>No {@code @Version}: every change happens under the village lock.
 */
@Entity
public class RecruitOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A plain id: orders are loaded by village, never through the village. */
    private long villageId;

    /** The recruiting building; its queue is the one this order is in. */
    @Enumerated(EnumType.STRING)
    private BuildingType building;

    @Enumerated(EnumType.STRING)
    private UnitType unit;

    private int count;

    /** Units already moved into the home stack. */
    private int completedCount;

    /** Fixed at enqueue (level of the building at that moment). */
    private int secondsPerUnit;

    private Instant startAt;
    private Instant endAt;

    /** What one unit cost, for the refund. */
    private int unitCostWood;

    private int unitCostClay;
    private int unitCostIron;

    /**
     * The population of one unit at enqueue, kept for information only: the population in use is
     * always counted from the current game data ({@code UnitService.unitsOf}).
     */
    private int unitPop;

    private Instant createdAt;

    protected RecruitOrder() {}

    private RecruitOrder(
            long villageId,
            BuildingType building,
            UnitType unit,
            int count,
            int secondsPerUnit,
            Instant startAt,
            ResourceAmounts unitCost,
            int unitPop,
            Instant now) {
        this.villageId = villageId;
        this.building = building;
        this.unit = unit;
        this.count = count;
        this.completedCount = 0;
        this.secondsPerUnit = secondsPerUnit;
        this.unitCostWood = unitCost.wood();
        this.unitCostClay = unitCost.clay();
        this.unitCostIron = unitCost.iron();
        this.unitPop = unitPop;
        this.createdAt = now;
        setStart(startAt);
    }

    /**
     * A new order of {@code count} units that starts at {@code startAt}.
     *
     * @param building the building that recruits the unit (its queue)
     * @param unitCost what one unit cost (the player paid {@code count} times that)
     * @param unitPop the population of one unit at enqueue (for information, see the field)
     */
    public static RecruitOrder create(
            long villageId,
            BuildingType building,
            UnitType unit,
            int count,
            int secondsPerUnit,
            Instant startAt,
            ResourceAmounts unitCost,
            int unitPop,
            Instant now) {
        if (count < 1 || secondsPerUnit < 1) {
            throw new IllegalArgumentException(
                    "count and seconds per unit must be at least 1: "
                            + count
                            + ", "
                            + secondsPerUnit);
        }
        return new RecruitOrder(
                villageId, building, unit, count, secondsPerUnit, startAt, unitCost, unitPop, now);
    }

    /**
     * Marks the units finished by {@code t} as completed (02-game-rules §5.2, {@code finishedBy})
     * and answers how many of them are new, i.e. have to go into the home stack now. An earlier
     * {@code t} than before changes nothing.
     */
    public int completeUpTo(Instant t) {
        int finished = RecruitRules.finishedBy(startAt, secondsPerUnit, count, t);
        if (finished <= completedCount) {
            return 0;
        }
        int newlyFinished = finished - completedCount;
        completedCount = finished;
        return newlyFinished;
    }

    /** Dev cheat: every unit is done now. Answers how many were still in training. */
    public int finishNow() {
        int rest = remaining();
        completedCount = count;
        return rest;
    }

    /** All units are done (the order can be deleted). */
    public boolean isComplete() {
        return completedCount >= count;
    }

    /** Units still in training: they count against the farm and are refunded on cancel. */
    public int remaining() {
        return count - completedCount;
    }

    /** What a cancel gives back: the cost of the units still in training (02-game-rules §5.2). */
    public ResourceAmounts refund() {
        return unitCost().times(remaining());
    }

    /**
     * Moves an order that hasn't started to another start (re-timing after a cancel); the end
     * follows with the stored time per unit.
     *
     * @throws IllegalStateException if units of the order are already done: the lazy completion
     *     counts from the start, so a started order must keep it
     */
    public void moveTo(Instant newStartAt) {
        if (completedCount > 0) {
            throw new IllegalStateException(
                    "Recruit order " + id + " has started and can't be moved");
        }
        setStart(newStartAt);
    }

    private void setStart(Instant newStartAt) {
        startAt = newStartAt;
        endAt = newStartAt.plusSeconds((long) count * secondsPerUnit);
    }

    public ResourceAmounts unitCost() {
        return new ResourceAmounts(unitCostWood, unitCostClay, unitCostIron);
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

    public int getUnitPop() {
        return unitPop;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
