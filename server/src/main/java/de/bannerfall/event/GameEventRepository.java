package de.bannerfall.event;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * The status is written as a literal ({@code 'PENDING'}) in the native queries on purpose: only
 * then can PostgreSQL use the partial indexes of V3.
 */
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    /**
     * Ids of the PENDING events that are due at {@code until} or earlier, in processing order: by
     * {@code dueAt}, events at the same instant in creation (id) order.
     */
    @Query(
            value =
                    """
                    select id from game_event
                    where status = 'PENDING' and due_at <= :until
                    order by due_at, id
                    limit :limit
                    """,
            nativeQuery = true)
    List<Long> findDueIds(Instant until, int limit);

    /** {@code select ... for update} of one event. Waits if somebody else holds the row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from GameEvent e where e.id = :id")
    Optional<GameEvent> findByIdForUpdate(long id);

    /**
     * Locks the PENDING events of one thing ({@code kind} is a {@link GameEventKind} name) with
     * {@code for update skip locked}: an event that another transaction holds right now (the worker
     * is processing it) is not waited for but left out, see {@link EventScheduler}.
     */
    @Query(
            value =
                    """
                    select * from game_event
                    where kind = :kind and ref_id = :refId and status = 'PENDING'
                    order by due_at, id
                    for update skip locked
                    """,
            nativeQuery = true)
    List<GameEvent> lockPendingSkippingLocked(String kind, long refId);

    /** Whether an event of this kind (a {@link GameEventKind} name) and reference is waiting. */
    @Query(
            value =
                    "select exists (select 1 from game_event where kind = :kind and ref_id is not"
                            + " distinct from cast(:refId as bigint) and status = 'PENDING')",
            nativeQuery = true)
    boolean existsPendingFor(String kind, Long refId);

    /** Whether an event of this kind (a {@link GameEventKind} name) is waiting. */
    @Query(
            value =
                    "select exists (select 1 from game_event where kind = :kind and status ="
                            + " 'PENDING')",
            nativeQuery = true)
    boolean existsPending(String kind);

    /** The kinds that still have events waiting (names of {@link GameEventKind}). */
    @Query(
            value = "select distinct kind from game_event where status = 'PENDING'",
            nativeQuery = true)
    List<String> findPendingKinds();

    /** Deletes the DONE events processed before {@code cutoff}; returns how many. */
    @Modifying
    @Transactional
    @Query(
            value = "delete from game_event where status = 'DONE' and processed_at < :cutoff",
            nativeQuery = true)
    int deleteDoneBefore(Instant cutoff);

    /** The newest FAILED events first (admin list). */
    List<GameEvent> findByStatusOrderByProcessedAtDescIdDesc(
            GameEventStatus status, org.springframework.data.domain.Pageable pageable);
}
