package de.bannerfall.market;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TradeOfferRepository extends JpaRepository<TradeOffer, Long> {

    /** The village's open offers, oldest first. */
    List<TradeOffer> findByVillageIdOrderByIdAsc(long villageId);

    /**
     * Open offers of every village that doesn't belong to the player (barbarian villages have none:
     * they are deleted when a village changes owner), with their villages and owners.
     */
    @Query(
            """
            select o from TradeOffer o join fetch o.village v join fetch v.player p
            where p.id <> :playerId
            """)
    List<TradeOffer> findOpenOffersOfOthers(long playerId);

    /** Only the village id, not the offer: the caller locks the village first, then reads it. */
    @Query("select o.village.id from TradeOffer o where o.id = :id")
    Optional<Long> findVillageIdById(long id);

    /** The offer's village id if the offer belongs to one of the player's villages. */
    @Query(
            """
            select o.village.id from TradeOffer o
            where o.id = :id and o.village.player.id = :playerId
            """)
    Optional<Long> findVillageIdOfOwner(long id, long playerId);
}
