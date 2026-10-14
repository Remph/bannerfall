package de.bannerfall.farm;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmTargetRepository extends JpaRepository<FarmTarget, Long> {

    /** The village's list in the order the targets were added. */
    List<FarmTarget> findByVillageIdOrderByIdAsc(long villageId);

    Optional<FarmTarget> findByIdAndVillageId(long id, long villageId);

    boolean existsByVillageIdAndTargetVillageId(long villageId, long targetVillageId);

    long countByVillageId(long villageId);
}
