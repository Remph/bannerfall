package de.bannerfall.admin;

import de.bannerfall.account.AccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is doing an admin action right now: the host token or a logged-in admin (08 §4, §6). */
public final class AdminActor {

    /** The audit actor of the host token. */
    public static final String HOST_CLI = "host-cli";

    /** The automatic temporary lock of an account (M14e). */
    public static final String AUTO_LOCK = "auto-lock";

    /** Actions without a request behind them (tests, start-up). */
    static final String SYSTEM = "system";

    private AdminActor() {}

    /** {@value #HOST_CLI}, the admin's username, or {@value #SYSTEM} outside a request. */
    public static String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof HostTokenAuthentication) {
            return HOST_CLI;
        }
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.getUsername();
        }
        return SYSTEM;
    }

    /** The admin's account id; null for the host token (which has no account). */
    public static Long currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.accountId();
        }
        return null;
    }
}
