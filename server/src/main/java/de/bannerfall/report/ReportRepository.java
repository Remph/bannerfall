package de.bannerfall.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * A player's reports, newest first (equal times: the later row first), without the copies sent
     * by players he blocked (M40, like their private messages). A copy counts from when it arrived,
     * so a sent report lands on top even when it happened earlier.
     */
    @Query(
            value =
                    "select r.* from report r where r.player_id = :playerId"
                            + NOT_FROM_BLOCKED
                            + " order by"
                            + " case when r.shared_by_id is null then r.happened_at"
                            + " else r.created_at end desc, r.id desc",
            countQuery =
                    "select count(*) from report r where r.player_id = :playerId"
                            + NOT_FROM_BLOCKED,
            nativeQuery = true)
    Page<Report> findVisible(long playerId, Pageable pageable);

    Optional<Report> findByIdAndPlayerId(long id, long playerId);

    /** The player's unread reports, without the copies sent by players he blocked. */
    @Query(
            value =
                    "select count(*) from report r where r.player_id = :playerId and not r.read"
                            + NOT_FROM_BLOCKED,
            nativeQuery = true)
    long countUnreadVisible(long playerId);

    /** Leaves out the copies sent by a player the list's owner blocked (02-game-rules §17). */
    String NOT_FROM_BLOCKED =
            " and not exists (select 1 from player_block b"
                    + " where b.blocker_id = r.player_id and b.blocked_id = r.shared_by_id)";

    /** The copies {@code sharedById} sent {@code playerId} that are still unread (M40). */
    long countByPlayerIdAndSharedByIdAndReadFalse(long playerId, long sharedById);

    /** The copies {@code sharedById} sent {@code playerId} since {@code since} (M40). */
    long countByPlayerIdAndSharedByIdAndCreatedAtGreaterThanEqual(
            long playerId, long sharedById, Instant since);

    /** The id of the player's newest report (0 if there is none). */
    @Query("select coalesce(max(r.id), 0L) from Report r where r.playerId = :playerId")
    long maxIdOfPlayer(long playerId);

    /** A player's reports after {@code afterId}, oldest first (an NPC lord reads them, 09 §4). */
    List<Report> findByPlayerIdAndIdGreaterThanOrderByIdAsc(long playerId, long afterId);

    /**
     * Deletes the reports of NPC lords created before {@code before} (nobody else reads them, 09
     * §4); the number of rows deleted.
     */
    @Modifying
    @Transactional
    @Query(
            """
            delete from Report r
            where r.createdAt < :before
              and r.playerId in (select p.id from Player p where p.npc = true)
            """)
    int deleteNpcReportsCreatedBefore(Instant before);

    @Modifying
    @Query("update Report r set r.read = true where r.id = :id and r.read = false")
    int markRead(long id);

    @Modifying
    @Query("update Report r set r.read = true where r.playerId = :playerId and r.read = false")
    int markAllRead(long playerId);

    /** Deletes the report if it is the player's; the number of rows deleted (0 or 1). */
    @Modifying
    @Query("delete from Report r where r.id = :id and r.playerId = :playerId")
    int deleteOwned(long id, long playerId);

    /** Deletes the player's reports with an id up to {@code upToId}; the number of rows deleted. */
    @Modifying
    @Query("delete from Report r where r.playerId = :playerId and r.id <= :upToId")
    int deleteOwnedUpTo(long playerId, long upToId);

    /**
     * What "Delete barbarian reports" removes (1.2.3, 02-game-rules §14): the player's own attack
     * and scout reports on a village that was barbarian then (the defender snapshot has no owner),
     * except those where he gained a banner (found, or stolen by his thieves; the "New banner"
     * pill, {@code BattleReport.bannersGained}). Copies other players sent stay. Reads the stored
     * JSON, so an unreadable payload counts too when its defender has no owner.
     */
    String BARBARIAN_WITHOUT_BANNER =
            " and r.kind in ('ATTACK', 'SCOUT') and r.shared_by_id is null"
                    + " and r.payload -> 'defender' ->> 'playerId' is null"
                    + " and coalesce(jsonb_typeof(r.payload -> 'banners' -> 'found'), 'null')"
                    + " <> 'object'"
                    + " and not coalesce(r.payload -> 'banners' -> 'theft' ->> 'result' = 'STOLEN'"
                    + " and jsonb_typeof(r.payload -> 'banners' -> 'theft' -> 'banner')"
                    + " = 'object', false)";

    /**
     * How many reports "Delete barbarian reports" would delete with the same {@code upToId}, so the
     * number the confirmation names is the number that goes.
     */
    @Query(
            value =
                    "select count(*) from report r where r.player_id = :playerId and r.id <="
                            + " :upToId"
                            + BARBARIAN_WITHOUT_BANNER,
            nativeQuery = true)
    long countBarbarianWithoutBannerUpTo(long playerId, long upToId);

    /**
     * Deletes the player's reports about barbarian villages ({@link #BARBARIAN_WITHOUT_BANNER})
     * with an id up to {@code upToId}; the number of rows deleted.
     */
    @Modifying
    @Query(
            value =
                    "delete from report r where r.player_id = :playerId and r.id <= :upToId"
                            + BARBARIAN_WITHOUT_BANNER,
            nativeQuery = true)
    int deleteBarbarianWithoutBannerUpTo(long playerId, long upToId);
}
