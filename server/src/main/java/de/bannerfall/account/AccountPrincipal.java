package de.bannerfall.account;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * The logged-in user as Spring Security keeps it in the session. Carries the account id so
 * controllers don't have to look the account up by name: {@code @AuthenticationPrincipal
 * AccountPrincipal me}.
 */
public class AccountPrincipal extends User {

    @Serial private static final long serialVersionUID = 1L;

    private final long accountId;

    /** {@code Account.sessionVersion} at the login: an older one ends the session (M14e). */
    private final int sessionVersion;

    AccountPrincipal(Account account) {
        super(
                account.getUsername(),
                // an account of the account server has none; erased right after the login anyway
                Objects.requireNonNullElse(account.getPasswordHash(), ""),
                !account.isDisabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.accountId = account.getId();
        this.sessionVersion = account.getSessionVersion();
    }

    public long accountId() {
        return accountId;
    }

    public int sessionVersion() {
        return sessionVersion;
    }
}
