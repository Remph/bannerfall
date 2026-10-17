package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One trade with the market itself (02-game-rules §12.1): the village gave {@code giveAmount} of
 * one resource and got {@code receiveAmount} of another. Only kept to count the day's amounts.
 */
@Entity
@Table(name = "market_exchange")
class MarketExchangeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long villageId;

    @Enumerated(EnumType.STRING)
    private ResourceType giveResource;

    private int giveAmount;

    @Enumerated(EnumType.STRING)
    private ResourceType receiveResource;

    private int receiveAmount;
    private Instant exchangedAt;

    protected MarketExchangeEntry() {}

    MarketExchangeEntry(
            long villageId,
            ResourceType giveResource,
            int giveAmount,
            ResourceType receiveResource,
            int receiveAmount,
            Instant exchangedAt) {
        this.villageId = villageId;
        this.giveResource = giveResource;
        this.giveAmount = giveAmount;
        this.receiveResource = receiveResource;
        this.receiveAmount = receiveAmount;
        this.exchangedAt = exchangedAt;
    }
}
