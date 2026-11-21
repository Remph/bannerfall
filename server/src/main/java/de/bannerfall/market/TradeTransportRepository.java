package de.bannerfall.market;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface TradeTransportRepository extends JpaRepository<TradeTransport, Long> {

    /** Merchants of the village that are away at {@code now} (on the way or coming back). */
    @Query(
            """
            select coalesce(sum(t.merchants), 0) from TradeTransport t
            where t.sourceVillage.id = :villageId and t.returnAt > :now
            """)
    long sumBusyMerchants(long villageId, Instant now);

    /** The village's merchants that are away at {@code now}, oldest first. */
    @Query(
            """
            select t from TradeTransport t
            where t.sourceVillage.id = :villageId and t.returnAt > :now
            order by t.departAt, t.id
            """)
    List<TradeTransport> findOutgoing(long villageId, Instant now);

    /** The merchants of several villages that are away at {@code now}, oldest first (Now list). */
    @Query(
            """
            select t from TradeTransport t
              join fetch t.sourceVillage sv left join fetch sv.player
              join fetch t.targetVillage tv left join fetch tv.player
            where t.sourceVillage.id in :villageIds and t.returnAt > :now
            order by t.departAt, t.id
            """)
    List<TradeTransport> findOutgoingOf(Collection<Long> villageIds, Instant now);

    /**
     * Merchants of other players on their way to some of these villages, the next arrival first
     * (Now list, 2.1): what the player's own merchants bring home between his villages is already
     * in {@link #findOutgoingOf}.
     */
    @Query(
            """
            select t from TradeTransport t
              join fetch t.sourceVillage sv left join fetch sv.player sp
              join fetch t.targetVillage tv left join fetch tv.player
            where t.targetVillage.id in :villageIds and t.arrived = false
              and (sp is null or sp.id <> :playerId)
            order by t.arriveAt, t.id
            """)
    List<TradeTransport> findIncomingFromOthers(Collection<Long> villageIds, long playerId);

    /** The village's merchants still away after {@code at} (a change of owner). */
    List<TradeTransport> findBySourceVillageIdAndReturnAtAfter(long villageId, Instant at);

    /** Transports the village sent that have not arrived yet (dev cheat). */
    List<TradeTransport> findBySourceVillageIdAndArrivedFalse(long villageId);

    /** Transports on their way to the village, the next arrival first. */
    @Query(
            """
            select t from TradeTransport t
            where t.targetVillage.id = :villageId and t.arrived = false
            order by t.arriveAt, t.id
            """)
    List<TradeTransport> findIncoming(long villageId);

    /** Only the target's id, so the arrival can lock it before reading the transport. */
    @Query("select t.targetVillage.id from TradeTransport t where t.id = :id")
    Optional<Long> findTargetVillageIdById(long id);

    /** The nightly cleanup: arrived transports whose merchants are back. */
    @Modifying
    @Transactional
    @Query("delete from TradeTransport t where t.arrived = true and t.returnAt <= :now")
    int deleteFinishedBefore(Instant now);
}
