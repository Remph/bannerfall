package de.bannerfall.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReturnWatchRepository extends JpaRepository<ReturnWatch, Long> {

    /** The village changed hands: its movements are gone, and nobody waits for them any more. */
    @Modifying
    @Query("delete from ReturnWatch w where w.villageId = :villageId")
    int deleteByVillage(long villageId);
}
