package de.bannerfall.admin;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findAllByOrderByAtDescIdDesc(Pageable pageable);
}
