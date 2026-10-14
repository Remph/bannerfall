package de.bannerfall.building;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BuildOrderRepository extends JpaRepository<BuildOrder, Long> {

    /**
     * The queue of a village in running order. Orders run one after another and last at least a
     * second, so the end times are strictly increasing; the id only breaks the tie after the dev
     * cheat has set several ends to the same instant. Then the orders are completed right away, all
     * at the same instant, so their order no longer matters (ids grow in queue order unless an
     * order was moved).
     */
    List<BuildOrder> findByVillageIdOrderByEndAtAscIdAsc(long villageId);

    /**
     * Only the village id, not the order: lets {@code BuildCompleteHandler} lock the village first
     * and read the order afterwards, so it sees what a request that held the lock changed.
     */
    @Query("select o.villageId from BuildOrder o where o.id = :id")
    Optional<Long> findVillageIdById(long id);
}
