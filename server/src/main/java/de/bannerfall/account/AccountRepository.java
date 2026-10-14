package de.bannerfall.account;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsernameKey(String usernameKey);

    boolean existsByUsernameKey(String usernameKey);

    /**
     * The admins that can still log in, locked: two demotions at the same moment can't both see a
     * second admin and leave none ({@code LAST_ADMIN}, M14d).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select a from Account a where a.role = de.bannerfall.account.Role.ADMIN and a.disabled"
                    + " = false order by a.id")
    List<Account> lockActiveAdmins();

    /** Remembers a login. A bulk update: no entity is loaded for it. */
    @Transactional
    @Modifying
    @Query("update Account a set a.lastLoginAt = :at where a.id = :id")
    void recordLogin(long id, Instant at);
}
