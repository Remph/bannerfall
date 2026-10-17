package de.bannerfall.village;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.player.Player;
import de.bannerfall.rules.Loyalty;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A village on the map. Resources and loyalty are stored lazily: the values at an anchor time,
 * brought forward by {@link VillageStateService#catchUp}. Change a village only while it is locked
 * ({@link VillageLocker}).
 */
@Entity
public class Village {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = barbarian village. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    /** Null for barbarians (the client shows a localized name). */
    private String name;

    private int x;
    private int y;

    /** Stored amounts at {@link #resourcesUpdatedAt}, fractional (02-game-rules §3). */
    private double wood;

    private double clay;
    private double iron;
    private Instant resourcesUpdatedAt;

    private double loyalty;
    private Instant loyaltyUpdatedAt;

    private int points;

    @Embedded private BuildingLevels buildings;

    private Instant createdAt;

    /**
     * When the owner got the village (v1.3): founded or conquered. His village list puts the one he
     * has held the longest first. Null for barbarian villages.
     */
    private Instant ownerSince;

    /**
     * A ram knocked this barbarian village's wall down (M29): without "barbarians rebuild walls" it
     * stays down. False for villages of players.
     */
    private boolean wallRazed;

    /**
     * A banner keep (v1.3, 02-game-rules §22): counts for the KEEPS victory. Stays a keep whoever
     * owns it.
     */
    private boolean keep;

    @Version private long version;

    protected Village() {}

    /**
     * A new village with full loyalty and the same amount of each resource.
     *
     * @param player owner, null for a barbarian village
     */
    public Village(
            Player player,
            String name,
            Coordinates position,
            double startResources,
            BuildingLevels buildings,
            int points,
            Instant now) {
        this.player = player;
        this.name = name;
        this.x = position.x();
        this.y = position.y();
        this.wood = startResources;
        this.clay = startResources;
        this.iron = startResources;
        this.resourcesUpdatedAt = now;
        this.loyalty = Loyalty.MAX;
        this.loyaltyUpdatedAt = now;
        this.buildings = buildings;
        this.points = points;
        this.createdAt = now;
        this.ownerSince = player == null ? null : now;
    }

    /**
     * The village loses its owner (02-game-rules §11: a deleted player's villages). Barbarian
     * villages have no name. The caller has removed the units and orders.
     */
    public void becomeBarbarian() {
        this.player = null;
        this.name = null;
        this.ownerSince = null;
        this.wallRazed = false;
    }

    public boolean isKeep() {
        return keep;
    }

    /** Makes this new barbarian village a banner keep (§22). */
    public void markKeep() {
        this.keep = true;
    }

    public boolean isWallRazed() {
        return wallRazed;
    }

    /** Rams lowered the wall of this barbarian village (M29). */
    public void markWallRazed() {
        this.wallRazed = true;
    }

    /**
     * A conqueror takes the village (02-game-rules §10): new owner, loyalty starts at 25 at {@code
     * at}. A barbarian village gets {@code nameIfNone}, a player's village keeps its name. The
     * caller has removed the units and orders that are lost.
     */
    public void conquer(Player newOwner, String nameIfNone, Instant at) {
        this.player = newOwner;
        this.ownerSince = at;
        if (name == null) {
            this.name = nameIfNone;
        }
        setLoyalty(Loyalty.AFTER_CONQUEST, Loyalty.newAnchor(loyaltyUpdatedAt, at));
    }

    public void rename(String newName) {
        this.name = newName;
    }

    /** New stored amounts and their anchor time (catch-up, paying, receiving). */
    public void setResources(double wood, double clay, double iron, Instant updatedAt) {
        this.wood = wood;
        this.clay = clay;
        this.iron = iron;
        this.resourcesUpdatedAt = updatedAt;
    }

    /**
     * The stock as whole amounts, rounded down: what costs are checked against (02-game-rules
     * conventions).
     */
    public ResourceAmounts floorResources() {
        return new ResourceAmounts(
                (int) Math.floor(wood), (int) Math.floor(clay), (int) Math.floor(iron));
    }

    /**
     * Pays a cost. The caller has caught the village up and checked {@code
     * cost.fitsInto(floorResources())}; the anchor time stays.
     */
    public void spend(ResourceAmounts cost) {
        wood -= cost.wood();
        clay -= cost.clay();
        iron -= cost.iron();
    }

    /**
     * Adds whole amounts <b>without</b> looking at the warehouse capacity: only for the dev cheat.
     * Game rules use {@link #receiveResources}. The caller has caught the village up; the anchor
     * time stays.
     */
    public void addResources(ResourceAmounts amounts) {
        wood += amounts.wood();
        clay += amounts.clay();
        iron += amounts.iron();
    }

    /**
     * Incoming resources (02-game-rules §3): {@code stored = max(stored, min(capacity, stored +
     * amount))} per resource. What doesn't fit is lost; stock that is already above the capacity
     * stays. Used for refunds (§4.3), loot and trades. The caller has caught the village up; the
     * anchor time stays.
     *
     * @return what was lost, in whole amounts as the stock is shown (the trade report says it)
     */
    public ResourceAmounts receiveResources(ResourceAmounts amounts, int capacity) {
        double oldWood = wood;
        double oldClay = clay;
        double oldIron = iron;
        wood = received(wood, amounts.wood(), capacity);
        clay = received(clay, amounts.clay(), capacity);
        iron = received(iron, amounts.iron(), capacity);
        return new ResourceAmounts(
                lost(oldWood, wood, amounts.wood()),
                lost(oldClay, clay, amounts.clay()),
                lost(oldIron, iron, amounts.iron()));
    }

    private static double received(double stored, int amount, int capacity) {
        return Math.max(stored, Math.min(capacity, stored + amount));
    }

    /** The amount minus what the shown (floored) stock grew by; never below 0. */
    private static int lost(double before, double after, int amount) {
        int grew = (int) (Math.floor(after) - Math.floor(before));
        return Math.max(0, amount - grew);
    }

    /** The sum of the building points; set by {@code PointsService}. */
    public void setPoints(int points) {
        this.points = points;
    }

    public void setLoyalty(double loyalty, Instant updatedAt) {
        this.loyalty = loyalty;
        this.loyaltyUpdatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    /** Lazy proxy; prefer ids in queries. Null for barbarians. */
    public Player getPlayer() {
        return player;
    }

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Coordinates coordinates() {
        return new Coordinates(x, y);
    }

    public double getWood() {
        return wood;
    }

    public double getClay() {
        return clay;
    }

    public double getIron() {
        return iron;
    }

    public Instant getResourcesUpdatedAt() {
        return resourcesUpdatedAt;
    }

    public double getLoyalty() {
        return loyalty;
    }

    public Instant getLoyaltyUpdatedAt() {
        return loyaltyUpdatedAt;
    }

    public int getPoints() {
        return points;
    }

    public BuildingLevels getBuildings() {
        return buildings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOwnerSince() {
        return ownerSince;
    }
}
