package de.bannerfall.building;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One upgrade in a village's build queue (02-game-rules §4.3). The orders of a village run one
 * after another: each starts when the one before it ends. Its BUILD_COMPLETE event is due at {@link
 * #getEndAt()}. Change orders only while the village is locked.
 *
 * <p>No {@code @Version}: every change happens under the village lock.
 */
@Entity
public class BuildOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A plain id: orders are loaded by village, never through the village. */
    private long villageId;

    @Enumerated(EnumType.STRING)
    private BuildingType building;

    /** For display; completion adds one level to whatever the village has then. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int targetLevel;

    /** Kept so the queue can be re-timed after a cancel. */
    private int durationSeconds;

    private Instant startAt;
    private Instant endAt;

    /** What was paid, for the refund. */
    private int costWood;

    private int costClay;
    private int costIron;
    private int costPop;

    private Instant createdAt;

    protected BuildOrder() {}

    private BuildOrder(
            long villageId,
            BuildingType building,
            int targetLevel,
            int durationSeconds,
            Instant startAt,
            ResourceAmounts cost,
            int costPop,
            Instant now) {
        this.villageId = villageId;
        this.building = building;
        this.targetLevel = targetLevel;
        this.durationSeconds = durationSeconds;
        this.startAt = startAt;
        this.endAt = startAt.plusSeconds(durationSeconds);
        this.costWood = cost.wood();
        this.costClay = cost.clay();
        this.costIron = cost.iron();
        this.costPop = costPop;
        this.createdAt = now;
    }

    /**
     * A new order that runs from {@code startAt} for {@code durationSeconds}.
     *
     * @param cost what the player paid
     * @param costPop the extra population the upgrade needs (counted while the order is queued)
     */
    public static BuildOrder create(
            long villageId,
            BuildingType building,
            int targetLevel,
            int durationSeconds,
            Instant startAt,
            ResourceAmounts cost,
            int costPop,
            Instant now) {
        return new BuildOrder(
                villageId, building, targetLevel, durationSeconds, startAt, cost, costPop, now);
    }

    /** Moves the order to another start; the end follows with the stored duration. */
    public void moveTo(Instant newStartAt) {
        startAt = newStartAt;
        endAt = newStartAt.plusSeconds(durationSeconds);
    }

    /**
     * The building lost {@code levels} levels in a battle (02-game-rules §9.2): the order stays and
     * still adds one level when it completes, so it aims that much lower.
     */
    public void lowerTargetLevel(int levels) {
        if (levels < 0 || targetLevel - levels < 1) {
            throw new IllegalArgumentException(
                    "Build order " + id + " at level " + targetLevel + " can't lose " + levels);
        }
        targetLevel -= levels;
    }

    /** Dev cheat: the order ends now (and has started by now). */
    public void finishNow(Instant now) {
        if (startAt.isAfter(now)) {
            startAt = now;
        }
        endAt = now;
    }

    /** What the order cost, refunded in full when it is cancelled. */
    public ResourceAmounts cost() {
        return new ResourceAmounts(costWood, costClay, costIron);
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

    public int getTargetLevel() {
        return targetLevel;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public int getCostPop() {
        return costPop;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
