package de.bannerfall.recruit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitOrderRepository extends JpaRepository<RecruitOrder, Long> {

    /**
     * All recruit orders of a village (every building), each building's queue in running order.
     * Within one building the end times are strictly increasing (orders run one after another and
     * last at least a second); the id only breaks ties between buildings.
     */
    List<RecruitOrder> findByVillageIdOrderByEndAtAscIdAsc(long villageId);

    /**
     * The orders of several villages that are not done by {@code at}, each building's queue in
     * running order (the Now list reads them without a catch-up).
     */
    List<RecruitOrder> findByVillageIdInAndEndAtAfterOrderByEndAtAscIdAsc(
            Collection<Long> villageIds, Instant at);
}
