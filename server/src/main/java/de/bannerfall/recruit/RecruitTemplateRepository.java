package de.bannerfall.recruit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitTemplateRepository extends JpaRepository<RecruitTemplate, Long> {

    List<RecruitTemplate> findByPlayerIdOrderByLetterAsc(long playerId);

    Optional<RecruitTemplate> findByPlayerIdAndLetter(long playerId, RecruitTemplateLetter letter);
}
