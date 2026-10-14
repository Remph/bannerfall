package de.bannerfall.world;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface WorldSettingsRepository extends JpaRepository<WorldSettingsEntity, Short> {

    /** {@code select ... for update} on the settings row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldSettingsEntity> findWithLockById(short id);
}
