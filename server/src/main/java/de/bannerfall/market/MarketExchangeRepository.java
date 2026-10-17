package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarketExchangeRepository extends JpaRepository<MarketExchangeEntry, Long> {

    /** Per resource, what the village got from the market since {@code since}. */
    @Query(
            """
            select e.receiveResource as resource, sum(e.receiveAmount) as amount
            from MarketExchangeEntry e
            where e.villageId = :villageId and e.exchangedAt >= :since
            group by e.receiveResource
            """)
    List<ReceivedRow> sumReceivedSince(long villageId, Instant since);

    /** Removes the village's trades of earlier days: they no longer count. */
    @Modifying(flushAutomatically = true)
    @Query(
            "delete from MarketExchangeEntry e where e.villageId = :villageId and e.exchangedAt <"
                    + " :before")
    int deleteOlderThan(long villageId, Instant before);

    interface ReceivedRow {
        ResourceType getResource();

        long getAmount();
    }
}
