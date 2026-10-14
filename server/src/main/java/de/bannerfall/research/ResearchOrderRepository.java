package de.bannerfall.research;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchOrderRepository extends JpaRepository<ResearchOrder, Long> {

    /** Every research of a player, finished ones included (at most two per unit). */
    List<ResearchOrder> findByPlayerId(long playerId);

    /** The researches of a player that are done by {@code at}: what his unit levels come from. */
    List<ResearchOrder> findByPlayerIdAndEndAtLessThanEqual(long playerId, Instant at);

    /** The researches started in a village that are not done by {@code at}. */
    List<ResearchOrder> findByVillageIdAndEndAtAfter(long villageId, Instant at);
}
