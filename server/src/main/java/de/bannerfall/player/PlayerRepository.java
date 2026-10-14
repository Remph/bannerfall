package de.bannerfall.player;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByAccountId(long accountId);

    /**
     * {@code select ... for update} on the player row: serialises what must not run twice at once
     * for one player although it spans several villages (the paladin slots, M31). Taken after the
     * village lock, never before.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :id")
    Optional<Player> lockById(long id);

    /** A human player by name, ignoring case (usernames are unique that way). */
    @Query("select p from Player p where lower(p.name) = lower(:name) and p.npc = false")
    Optional<Player> findHumanByName(String name);

    /**
     * The village count of an NPC lord, empty if there is no such lord. A plain value, not the
     * entity: the {@code NPC_THINK} event's transaction must not hold the player row that the
     * round's own transactions change (09-npc-lords §3).
     */
    @Query("select p.villageCount from Player p where p.id = :id and p.npc = true")
    Optional<Integer> findNpcVillageCount(long id);

    /** All NPC lords, newest first (admin list). */
    @Query("select p from Player p where p.npc = true order by p.id desc")
    List<Player> findNpcLords();

    /** The names of all NPC lords (to pick an unused one). */
    @Query("select p.name from Player p where p.npc = true")
    List<String> findNpcNames();

    /**
     * NPC lords that still own a village, with no pending decision round (09 §3, start-up check).
     */
    @Query(
            value =
                    """
                    select p.id from player p
                    where p.npc and p.village_count > 0
                      and not exists (select 1 from game_event e
                                      where e.kind = 'NPC_THINK' and e.ref_id = p.id
                                        and e.status = 'PENDING')
                    order by p.id
                    """,
            nativeQuery = true)
    List<Long> findNpcLordIdsWithoutPendingRound();

    /** Human players that own at least one village (the rubber band compares with them, 09 §8). */
    @Query("select p from Player p where p.npc = false and p.villageCount > 0")
    List<Player> findHumansWithVillages();

    /**
     * Human players without a village whose account is neither deleted nor banned for good, oldest
     * first: the players who registered in the pre-game and get their village when the world opens
     * (M14g).
     */
    @Query(
            """
            select p from Player p
            where p.npc = false and p.villageCount = 0
              and exists (select 1 from Account a
                          where a.id = p.accountId and a.disabled = false
                            and (a.bannedUntil is null or a.bannedUntil < :permanentBan))
            order by p.id
            """)
    List<Player> findWaitingForVillage(Instant permanentBan);

    /** Human players whose account is not deleted (the pre-game page shows the number). */
    @Query(
            """
            select count(p) from Player p
            where p.npc = false
              and exists (select 1 from Account a where a.id = p.accountId and a.disabled = false)
            """)
    long countRegisteredHumans();

    /**
     * Ends the beginner protection at {@code now} if it lasts longer. A bulk update on purpose: it
     * doesn't touch {@code version}, so it can't collide with a points update of the same player
     * from another village (event worker) and fail with an optimistic-lock error.
     *
     * @return 1 if the protection was ended, 0 if it was over already
     */
    @Modifying
    @Query(
            """
            update Player p set p.protectedUntil = :now
            where p.id = :playerId and p.protectedUntil > :now
            """)
    int endProtection(long playerId, Instant now);

    /**
     * A page of the ranking: NPC lords and humans whose account is not deleted, best first; equal
     * points share a rank (two players on rank 2, the next is rank 4).
     */
    @Query(
            value =
                    """
                    select r.ranking as rank, r.id as playerId, r.name as name, r.npc as npc,
                           r.points as points, r.village_count as villageCount,
                           t.id as guildId, t.tag as guildTag
                    from (select rank() over (order by p.points desc) as ranking,
                                 p.id, p.name, p.npc, p.points, p.village_count
                          from player p left join account a on a.id = p.account_id
                          where p.npc or not a.disabled) r
                    left join guild_member m on m.player_id = r.id
                    left join guild t on t.id = m.guild_id
                    order by r.ranking, r.id
                    limit :size offset :offset
                    """,
            nativeQuery = true)
    List<RankingRow> findRankingPage(int size, long offset);

    /** How many players the ranking lists. */
    @Query(
            value =
                    """
                    select count(*) from player p left join account a on a.id = p.account_id
                    where p.npc or not a.disabled
                    """,
            nativeQuery = true)
    long countRanked();

    /** The player's rank, empty if he doesn't exist or was deleted. */
    @Query(
            value =
                    """
                    select r.ranking
                    from (select rank() over (order by p.points desc) as ranking, p.id
                          from player p left join account a on a.id = p.account_id
                          where p.npc or not a.disabled) r
                    where r.id = :id
                    """,
            nativeQuery = true)
    Optional<Long> findRank(long id);
}
