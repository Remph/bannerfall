package de.bannerfall.account;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * 403 {@code ACCOUNT_BANNED} (M14e): the login of a banned account, or a request of a session whose
 * account was banned meanwhile. The answer carries {@code bannedUntil} and {@code reason}, so the
 * login page can show them.
 */
public class AccountBannedException extends ApiException {

    private final Instant bannedUntil;
    private final String reason;

    public AccountBannedException(Instant bannedUntil, String reason) {
        super(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_BANNED, "This account is banned");
        this.bannedUntil = bannedUntil;
        this.reason = reason;
    }

    public Instant bannedUntil() {
        return bannedUntil;
    }

    /** May be null. */
    public String reason() {
        return reason;
    }
}
