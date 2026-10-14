package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.rules.Merchants;
import de.bannerfall.village.Village;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * An open market offer (02-game-rules §12): "give {@code offerAmount} of one resource for {@code
 * wantAmount} of another, {@code remainingCount} times". The village paid for all of it when the
 * offer was created; the offer reserves {@code remainingCount × ceil(offerAmount / 1000)} of its
 * merchants. Change it only while its village is locked.
 */
@Entity
public class TradeOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "village_id")
    private Village village;

    @Enumerated(EnumType.STRING)
    private ResourceType offerResource;

    private int offerAmount;

    @Enumerated(EnumType.STRING)
    private ResourceType wantResource;

    private int wantAmount;
    private int remainingCount;
    private Instant createdAt;

    @Version private long version;

    protected TradeOffer() {}

    TradeOffer(
            Village village,
            ResourceType offerResource,
            int offerAmount,
            ResourceType wantResource,
            int wantAmount,
            int count,
            Instant createdAt) {
        this.village = village;
        this.offerResource = offerResource;
        this.offerAmount = offerAmount;
        this.wantResource = wantResource;
        this.wantAmount = wantAmount;
        this.remainingCount = count;
        this.createdAt = createdAt;
    }

    /** {@code count} units of the offer side, e.g. 3 × 1000 wood. */
    ResourceAmounts offered(int count) {
        return amounts(offerResource, Math.multiplyExact(offerAmount, count));
    }

    /** {@code count} units of the wanted side. */
    ResourceAmounts wanted(int count) {
        return amounts(wantResource, Math.multiplyExact(wantAmount, count));
    }

    /** The merchants it still reserves. */
    int reservedMerchants() {
        return Merchants.forOffer(offerAmount, remainingCount);
    }

    /** {@code count} units were accepted ({@code 1 ≤ count ≤ remainingCount}). */
    void take(int count) {
        if (count < 1 || count > remainingCount) {
            throw new IllegalArgumentException(
                    "Can't take " + count + " of " + remainingCount + " from offer " + id);
        }
        remainingCount -= count;
    }

    static ResourceAmounts amounts(ResourceType type, int amount) {
        return switch (type) {
            case WOOD -> new ResourceAmounts(amount, 0, 0);
            case CLAY -> new ResourceAmounts(0, amount, 0);
            case IRON -> new ResourceAmounts(0, 0, amount);
        };
    }

    public Long getId() {
        return id;
    }

    public Village getVillage() {
        return village;
    }

    public ResourceType getOfferResource() {
        return offerResource;
    }

    public int getOfferAmount() {
        return offerAmount;
    }

    public ResourceType getWantResource() {
        return wantResource;
    }

    public int getWantAmount() {
        return wantAmount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
