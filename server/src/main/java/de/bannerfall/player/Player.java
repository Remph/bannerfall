package de.bannerfall.player;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * The game identity: owns villages, has points. Humans are linked to their login account by id only
 * (game code never needs the account); NPC lords have no account.
 *
 * <p>{@code @DynamicUpdate}: an update writes only the columns that changed. Ending the beginner
 * protection is a bulk update that leaves {@code version} alone ({@link
 * PlayerRepository#endProtection}); without this, a worker that loaded the player just before and
 * then changed his points would write the old {@code protected_until} back (code review 1.2).
 */
@Entity
@DynamicUpdate
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for NPC lords. */
    private Long accountId;

    /** The account's username for humans, generated for NPC lords. */
    private String name;

    private boolean npc;

    /** Sum of the points of all villages. */
    private int points;

    private int villageCount;

    /** Beginner protection: can't be attacked before this. */
    private Instant protectedUntil;

    private Instant createdAt;

    /**
     * Multiplies the production of all the player's villages (02-game-rules §3): 1.0 for humans,
     * set by the NPC lord's round for NPC lords (09-npc-lords §8).
     */
    private double productionFactor = 1.0;

    @Version private long version;

    protected Player() {}

    private Player(Long accountId, String name, boolean npc, Instant protectedUntil, Instant now) {
        this.accountId = accountId;
        this.name = name;
        this.npc = npc;
        this.protectedUntil = protectedUntil;
        this.createdAt = now;
    }

    /** A human player for a newly registered account (no villages yet). */
    public static Player human(long accountId, String name, Instant protectedUntil, Instant now) {
        return new Player(accountId, name, false, protectedUntil, now);
    }

    /** A new NPC lord (no account, no beginner protection: {@code protectedUntil = now}, 09 §2). */
    public static Player npcLord(String name, double productionFactor, Instant now) {
        Player player = new Player(null, name, true, now, now);
        player.productionFactor = productionFactor;
        return player;
    }

    /** Counts a new (or newly conquered) village. */
    public void addVillage(int villagePoints) {
        villageCount++;
        points += villagePoints;
    }

    /** A village is gone (became barbarian); the points are recomputed separately. */
    public void loseVillage() {
        villageCount--;
    }

    /** New beginner protection, e.g. for a fresh start after losing every village. */
    public void protectUntil(Instant until) {
        this.protectedUntil = until;
    }

    /** The new sum over all villages; set by {@code PointsService}. */
    public void updatePoints(int newPoints) {
        this.points = newPoints;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public boolean isNpc() {
        return npc;
    }

    public int getPoints() {
        return points;
    }

    public int getVillageCount() {
        return villageCount;
    }

    public Instant getProtectedUntil() {
        return protectedUntil;
    }

    public double getProductionFactor() {
        return productionFactor;
    }

    public void setProductionFactor(double factor) {
        this.productionFactor = factor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
