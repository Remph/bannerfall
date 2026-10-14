package de.bannerfall.unit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnitStackRepository extends JpaRepository<UnitStack, Long> {

    /** The village's own army at home. Every village has it. */
    @Query(
            """
            select s from UnitStack s
            where s.homeVillage.id = :villageId and s.locationVillage.id = :villageId
            """)
    Optional<UnitStack> findHomeStack(long villageId);

    /** All stacks of the village's units: at home and supporting elsewhere. */
    List<UnitStack> findByHomeVillageId(long villageId);

    /** All stacks standing in the village: its own army and the supports of other villages. */
    List<UnitStack> findByLocationVillageId(long villageId);

    /** The units of {@code homeVillageId} standing in {@code locationVillageId}, if any. */
    Optional<UnitStack> findByHomeVillageIdAndLocationVillageId(
            long homeVillageId, long locationVillageId);

    /** Where the units of these villages stand outside them (support stacks), for locking. */
    @Query(
            """
            select distinct s.locationVillage.id from UnitStack s
            where s.homeVillage.id in :villageIds and s.locationVillage.id <> s.homeVillage.id
            """)
    List<Long> findSupportLocationIds(Collection<Long> villageIds);

    /**
     * The ids of the villages to lock before the stack itself is read (a stack read before waiting
     * for the locks would be stale).
     */
    @Query(
            """
            select new de.bannerfall.unit.StackVillages(s.homeVillage.id, s.locationVillage.id)
            from UnitStack s where s.id = :id
            """)
    Optional<StackVillages> findVillagesById(long id);

    /**
     * The home villages of a player's support stacks standing in {@code villageId} (other than its
     * own army), for locking them together with it (09-npc-lords §3). Plain ids: the stacks are
     * read after the locks.
     */
    @Query(
            """
            select s.homeVillage.id from UnitStack s
            where s.locationVillage.id = :villageId and s.homeVillage.id <> :villageId
              and s.homeVillage.player.id = :playerId
            """)
    List<Long> findOwnSupportHomeIds(long villageId, long playerId);
}
