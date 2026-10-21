package de.bannerfall.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VillageAutomationRepository extends JpaRepository<VillageAutomation, Long> {

    /** Drops a village's automation (it changes owner); 0 if it had none. */
    @Modifying
    @Query("delete from VillageAutomation a where a.villageId = :villageId")
    int deleteForVillage(@Param("villageId") long villageId);

    /** The player's villages that work through the plan {@code letter} and are switched on. */
    @Query(
            """
            select a from VillageAutomation a, Village v
            where v.id = a.villageId and v.player.id = :playerId
              and a.planLetter = :letter and a.enabled = true
            """)
    List<VillageAutomation> findEnabledByPlayerAndPlan(
            @Param("playerId") long playerId, @Param("letter") BuildPlanLetter letter);
}
