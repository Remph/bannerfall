package de.bannerfall.farm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FarmTargetRepository extends JpaRepository<FarmTarget, Long> {

    /** The village's list in the order the targets were added. */
    List<FarmTarget> findByVillageIdOrderByIdAsc(long villageId);

    Optional<FarmTarget> findByIdAndVillageId(long id, long villageId);

    boolean existsByVillageIdAndTargetVillageId(long villageId, long targetVillageId);

    long countByVillageId(long villageId);

    /** The entries that use a template, in any of the player's villages. */
    List<FarmTarget> findByTemplateId(long templateId);

    /**
     * An attack from the village at the target, however it was sent (1.4): the entry's "last sent"
     * shows the latest one. The number of rows changed (0 or 1).
     */
    @Modifying
    @Query(
            "update FarmTarget t set t.lastSentAt = :now"
                    + " where t.villageId = :villageId and t.targetVillageId = :targetVillageId")
    int markSent(long villageId, long targetVillageId, Instant now);
}
