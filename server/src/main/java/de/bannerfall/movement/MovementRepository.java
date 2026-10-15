package de.bannerfall.movement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    /** All movements of a village's units, in any direction (population, 02-game-rules §5.3). */
    List<Movement> findByHomeVillageId(long villageId);

    /** Commands the village has sent that are still on the way (ATTACK, SUPPORT), next first. */
    @Query(
            """
            select m from Movement m
            where m.homeVillage.id = :villageId
              and m.kind <> de.bannerfall.movement.MovementKind.RETURN
            order by m.arriveAt, m.id
            """)
    List<Movement> findOutgoing(long villageId);

    /** Everything arriving at the village, next first: attacks, supports, own units returning. */
    List<Movement> findByTargetVillageIdOrderByArriveAtAscIdAsc(long villageId);

    /**
     * Attacks on the way to the village, not counting the owner's own: after a conquest the
     * conqueror's later attacks still head there (they arrive like a support).
     */
    @Query(
            """
            select count(m) from Movement m join m.targetVillage t left join t.player p
            where t.id = :villageId
              and m.kind = de.bannerfall.movement.MovementKind.ATTACK
              and (p is null or m.ownerPlayerId <> p.id)
            """)
    long countIncomingAttacks(long villageId);

    /**
     * The player's attacks still on the way, from any of his villages, with their targets and the
     * targets' owners (the farm list's banner chances, 1.2.3).
     */
    @Query(
            """
            select m from Movement m join fetch m.targetVillage t left join fetch t.player
            where m.ownerPlayerId = :playerId
              and m.kind = de.bannerfall.movement.MovementKind.ATTACK
            """)
    List<Movement> findAttacksUnderWay(long playerId);

    /**
     * The ids of the villages to lock before the movement itself is read: a handler that read the
     * movement first would keep its old state after waiting for the locks.
     */
    @Query(
            """
            select new de.bannerfall.movement.MovementVillages(m.homeVillage.id, m.targetVillage.id)
            from Movement m where m.id = :id
            """)
    Optional<MovementVillages> findVillagesById(long id);

    /** The home village of a movement that belongs to the player, for the cancel's lock. */
    @Query(
            "select m.homeVillage.id from Movement m where m.id = :id and m.ownerPlayerId ="
                    + " :playerId")
    Optional<Long> findHomeVillageIdOfOwner(long id, long playerId);

    /**
     * The nobles of an attack, read without loading the movement (like {@link #findVillagesById});
     * empty if it is no attack (any more).
     */
    @Query(
            """
            select m.units.noble from Movement m
            where m.id = :id and m.kind = de.bannerfall.movement.MovementKind.ATTACK
            """)
    Optional<Integer> findAttackingNobles(long id);
}
