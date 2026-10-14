package de.bannerfall.admin;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.account.AccountRepository;
import de.bannerfall.account.Role;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may call {@code /api/admin/**} ({@code SecurityConfig}): the host token, or a logged-in
 * account that is ADMIN <i>now</i>. The role in the session is the one from the login; reading it
 * from the database for every admin request (they are rare) makes a role change by the host count
 * at once, both ways, and shuts out an admin whose player was deleted (M14d).
 */
@Component
public class AdminAccess {

    private final AccountRepository accounts;

    AdminAccess(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Authentication authentication) {
        if (authentication instanceof HostTokenAuthentication) {
            return true;
        }
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return false;
        }
        return accounts.findById(principal.accountId())
                .filter(account -> account.getRole() == Role.ADMIN && !account.isDisabled())
                .isPresent();
    }
}
