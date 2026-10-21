package de.bannerfall.automation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    List<BuildPlan> findByPlayerIdOrderByLetterAsc(long playerId);

    Optional<BuildPlan> findByPlayerIdAndLetter(long playerId, BuildPlanLetter letter);
}
