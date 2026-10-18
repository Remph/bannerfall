package de.bannerfall.farm;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmTemplateRepository extends JpaRepository<FarmTemplate, Long> {

    List<FarmTemplate> findByPlayerIdOrderByLetterAsc(long playerId);

    Optional<FarmTemplate> findByPlayerIdAndLetter(long playerId, FarmTemplateLetter letter);
}
