package de.bannerfall.recruit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DismissOrderRepository extends JpaRepository<DismissOrder, Long> {

    /**
     * All dismiss orders of a village (every building), each building's queue in running order (the
     * end times within one building are strictly increasing, like for recruit orders).
     */
    List<DismissOrder> findByVillageIdOrderByEndAtAscIdAsc(long villageId);
}
