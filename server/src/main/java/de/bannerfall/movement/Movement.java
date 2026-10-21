package de.bannerfall.movement;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.village.Village;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Duration;
import java.time.Instant;

/**
 * Troops on the way (02-game-rules §6). Each movement has one MOVEMENT_ARRIVE event at {@link
 * #getArriveAt()}; the {@link MovementArriveHandler} deletes the row when it arrives.
 *
 * <p>No {@code @Version}: a movement is only changed while its home village is locked (send,
 * cancel, arrival), so two changes never overlap.
 */
@Entity
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private MovementKind kind;

    /** The units' home: the source of an ATTACK or SUPPORT, the target of a RETURN. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_village_id")
    private Village homeVillage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_village_id")
    private Village sourceVillage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_village_id")
    private Village targetVillage;

    /** The owner of the units (of the home village), for queries and permissions. */
    private long ownerPlayerId;

    @Embedded private UnitCounts units;

    /** ATTACK with catapults only, else null. */
    @Enumerated(EnumType.STRING)
    private BuildingType catapultTarget;

    /** The source village's War, Plunder and Haste when the command left (M32b, §21.2). */
    private double bannerAttack;

    private double bannerCarry;
    private double bannerHaste;

    /** The paladin standard an attack took along (1.4, §21.18); all null without one. */
    private Long standardBannerId;

    @Enumerated(EnumType.STRING)
    private BannerType standardType;

    private Short standardTier;
    private Short standardEffectiveTier;

    /** RETURN only. */
    private int lootWood;

    private int lootClay;
    private int lootIron;

    private Instant departAt;
    private Instant arriveAt;
    private Instant createdAt;

    /**
     * The {@link ReturnWatch} that pushes when the units are home (1.6); null if the owner doesn't
     * want to hear about it. An attack hands it on to its RETURN.
     */
    private Long returnWatchId;

    protected Movement() {}

    private Movement(
            MovementKind kind,
            Village homeVillage,
            Village sourceVillage,
            Village targetVillage,
            long ownerPlayerId,
            UnitCounts units,
            BuildingType catapultTarget,
            ResourceAmounts loot,
            CommandBanner banner,
            Instant departAt,
            Instant arriveAt,
            Instant createdAt) {
        this.kind = kind;
        this.homeVillage = homeVillage;
        this.sourceVillage = sourceVillage;
        this.targetVillage = targetVillage;
        this.ownerPlayerId = ownerPlayerId;
        this.units = units;
        this.catapultTarget = catapultTarget;
        this.lootWood = loot.wood();
        this.lootClay = loot.clay();
        this.lootIron = loot.iron();
        this.bannerAttack = banner.attack();
        this.bannerCarry = banner.carry();
        this.bannerHaste = banner.haste();
        CommandBanner.Standard standard = banner.standard();
        if (standard != null) {
            this.standardBannerId = standard.bannerId();
            this.standardType = standard.type();
            this.standardTier = (short) standard.tier();
            this.standardEffectiveTier = (short) standard.effectiveTier();
        }
        this.departAt = departAt;
        this.arriveAt = arriveAt;
        this.createdAt = createdAt;
    }

    /**
     * An ATTACK or SUPPORT that leaves its home village {@code source} at {@code departAt} (now: a
     * command is created when it departs).
     *
     * @param units a fresh instance, owned by the movement from now on
     * @param catapultTarget null unless it is an attack with catapults
     */
    public static Movement command(
            MovementKind kind,
            Village source,
            Village target,
            long ownerPlayerId,
            UnitCounts units,
            BuildingType catapultTarget,
            Instant departAt,
            Instant arriveAt) {
        return command(
                kind,
                source,
                target,
                ownerPlayerId,
                units,
                catapultTarget,
                CommandBanner.NONE,
                departAt,
                arriveAt);
    }

    /**
     * Like {@link #command(MovementKind, Village, Village, long, UnitCounts, BuildingType, Instant,
     * Instant)} with the source village's battle banners (02-game-rules §21.2).
     *
     * @param banner the snapshot of the source's War, Plunder and Haste
     */
    public static Movement command(
            MovementKind kind,
            Village source,
            Village target,
            long ownerPlayerId,
            UnitCounts units,
            BuildingType catapultTarget,
            CommandBanner banner,
            Instant departAt,
            Instant arriveAt) {
        if (kind == MovementKind.RETURN) {
            throw new IllegalArgumentException("A RETURN is not a command");
        }
        return new Movement(
                kind,
                source,
                source,
                target,
                ownerPlayerId,
                units,
                catapultTarget,
                ResourceAmounts.ZERO,
                // only paladins carry a standard (§21.18)
                banner.forUnits(units.get(UnitType.PALADIN) > 0),
                departAt,
                arriveAt,
                departAt);
    }

    /**
     * Units walking from {@code from} to their {@code home}: survivors of an attack (with loot), a
     * withdrawn or sent back support.
     *
     * @param units a fresh instance, owned by the movement from now on
     * @param departAt game time: for a return after a battle the arrival of the attack
     * @param createdAt wall-clock time of creating the row
     */
    public static Movement returning(
            Village home,
            Village from,
            long ownerPlayerId,
            UnitCounts units,
            ResourceAmounts loot,
            Instant departAt,
            Instant arriveAt,
            Instant createdAt) {
        return returning(
                home,
                from,
                ownerPlayerId,
                units,
                loot,
                CommandBanner.NONE,
                departAt,
                arriveAt,
                createdAt);
    }

    /**
     * Like {@link #returning(Village, Village, long, UnitCounts, ResourceAmounts, Instant, Instant,
     * Instant)} for the survivors of an attack, who keep the banner numbers of their trip.
     */
    public static Movement returning(
            Village home,
            Village from,
            long ownerPlayerId,
            UnitCounts units,
            ResourceAmounts loot,
            CommandBanner banner,
            Instant departAt,
            Instant arriveAt,
            Instant createdAt) {
        return new Movement(
                MovementKind.RETURN,
                home,
                from,
                home,
                ownerPlayerId,
                units,
                null,
                loot,
                banner,
                departAt,
                arriveAt,
                createdAt);
    }

    /**
     * Until when the owner may cancel (02-game-rules §6), exclusive: the end of the cancel window
     * after departure, or the arrival if that comes first. Null for a RETURN, which can't be
     * cancelled.
     */
    public Instant cancellableUntil(int cancelWindowSeconds) {
        if (kind == MovementKind.RETURN) {
            return null;
        }
        Instant windowEnd = departAt.plusSeconds(cancelWindowSeconds);
        return windowEnd.isBefore(arriveAt) ? windowEnd : arriveAt;
    }

    public boolean isCancellableAt(Instant now, int cancelWindowSeconds) {
        Instant until = cancellableUntil(cancelWindowSeconds);
        return until != null && now.isBefore(until);
    }

    /** Where a cancel at {@code now} would arrive home: as long back as it has been on the way. */
    public Instant returnArrivalIfCancelledAt(Instant now) {
        return now.plus(Duration.between(departAt, now));
    }

    /**
     * Cancels the command at {@code now} (02-game-rules §6): it turns around and becomes a RETURN
     * from its old target to its home that departs now and arrives at {@link
     * #returnArrivalIfCancelledAt}. The caller moves the event.
     *
     * @throws IllegalStateException if it is not cancellable at {@code now}
     */
    public void cancel(Instant now, int cancelWindowSeconds) {
        if (!isCancellableAt(now, cancelWindowSeconds)) {
            throw new IllegalStateException("Movement " + id + " can't be cancelled at " + now);
        }
        Instant returnAt = returnArrivalIfCancelledAt(now);
        kind = MovementKind.RETURN;
        sourceVillage = targetVillage;
        targetVillage = homeVillage;
        catapultTarget = null;
        departAt = now;
        arriveAt = returnAt;
    }

    /** Dev cheat: arrives at {@code now} if it would arrive later. The caller moves the event. */
    public void arriveNow(Instant now) {
        if (arriveAt.isAfter(now)) {
            arriveAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReturnWatchId() {
        return returnWatchId;
    }

    /** Joins the watch (its id), or leaves it (null). */
    public void watchReturn(Long returnWatchId) {
        this.returnWatchId = returnWatchId;
    }

    public MovementKind getKind() {
        return kind;
    }

    public Village getHomeVillage() {
        return homeVillage;
    }

    public Village getSourceVillage() {
        return sourceVillage;
    }

    public Village getTargetVillage() {
        return targetVillage;
    }

    public long getOwnerPlayerId() {
        return ownerPlayerId;
    }

    /** Live: don't change it, copy it ({@link UnitCounts#copy()}). */
    public UnitCounts getUnits() {
        return units;
    }

    /**
     * Takes every unit of {@code type} out of the movement (1.4: paladins without a Banner Hall,
     * {@code PaladinHallMove}); the arrival stays as it is.
     *
     * @return how many there were
     */
    public int removeAll(UnitType type) {
        int count = units.get(type);
        units.set(type, 0);
        return count;
    }

    public BuildingType getCatapultTarget() {
        return catapultTarget;
    }

    /** The source's War, Plunder and Haste when the command left; NONE before M32b. */
    public CommandBanner getBanner() {
        return new CommandBanner(
                bannerAttack,
                bannerCarry,
                bannerHaste,
                standardType == null
                        ? null
                        : new CommandBanner.Standard(
                                standardBannerId,
                                standardType,
                                standardTier,
                                standardEffectiveTier));
    }

    public ResourceAmounts getLoot() {
        return new ResourceAmounts(lootWood, lootClay, lootIron);
    }

    public Instant getDepartAt() {
        return departAt;
    }

    public Instant getArriveAt() {
        return arriveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
