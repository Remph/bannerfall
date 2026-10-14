package de.bannerfall.account;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

/**
 * Logs users in and out of the HTTP session. We use JSON endpoints instead of Spring's form login,
 * so this does by hand what the form login filter would do: check the password, change the session
 * id, store the SecurityContext in the session (Spring Security 6+ no longer does that
 * automatically) and hand out a fresh CSRF token.
 */
@Service
class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AccountRepository accounts;
    private final AccountSessions sessions;
    private final Clock clock;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    LoginService(
            AuthenticationManager authenticationManager,
            LoginRateLimiter rateLimiter,
            CsrfTokenRepository csrfTokenRepository,
            AccountRepository accounts,
            AccountSessions sessions,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.csrfTokenRepository = csrfTokenRepository;
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    AccountPrincipal login(
            LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        String key = Account.keyOf(login.username());
        String address = request.getRemoteAddr();
        if (!rateLimiter.tryAttempt(key, address)) {
            // No name lookup here: a script hammering one name shouldn't cost a query each time.
            // The failed logins before the block already named the account.
            log.info("Login refused: too many failed logins");
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many failed logins, try again in a minute");
        }
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    login.username(), login.password()));
        } catch (BadCredentialsException | DisabledException e) {
            // Unknown user, wrong password and a deleted player look the same from outside. The
            // attempt was counted already (tryAttempt).
            // INFO, not WARN: a mistyped password is a normal answer. Never log the password.
            log.info("Failed login for {}", loggableName(key));
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.BAD_CREDENTIALS,
                    "Wrong username or password");
        }
        rateLimiter.recordSuccess(key, address);
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        // Only who knows the password learns of the ban (M14e); no session is started.
        Account account = accounts.findById(principal.accountId()).orElseThrow();
        if (account.isBannedAt(clock.instant())) {
            log.info("Login refused: '{}' is banned", account.getUsername());
            throw new AccountBannedException(account.getBannedUntil(), account.getBanReason());
        }
        startSession(authentication, request, response);
        accounts.recordLogin(principal.accountId(), clock.instant());
        return principal;
    }

    /** Logs in an account without a password check (right after registration). */
    void loginWithoutPassword(
            Account account, HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal principal = new AccountPrincipal(account);
        principal.eraseCredentials(); // the password hash doesn't belong in the session
        startSession(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()),
                request,
                response);
        accounts.recordLogin(account.getId(), clock.instant());
    }

    /**
     * Keeps the request's session after the account's sessions were ended by its own password
     * change (M14e): a new session id and a principal with the new session version.
     */
    void renewSession(Account account, HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal principal = new AccountPrincipal(account);
        principal.eraseCredentials();
        startSession(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()),
                request,
                response);
    }

    void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        renewCsrfToken(request, response);
    }

    private void startSession(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // session fixation protection
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        sessions.loggedIn(
                ((AccountPrincipal) authentication.getPrincipal()).accountId(),
                request.getSession());
        renewCsrfToken(request, response);
    }

    /**
     * The account's own name if the typed username belongs to one, otherwise nothing: an unknown
     * "username" may be a password typed into the wrong field. Stored names passed the registration
     * rules (letters, digits, _ and -), so they can't forge extra log lines either.
     */
    private String loggableName(String usernameKey) {
        return accounts.findByUsernameKey(usernameKey)
                .map(account -> "'" + account.getUsername() + "'")
                .orElse("an unknown username");
    }

    /** A new CSRF token after every login/logout, sent right away as the XSRF-TOKEN cookie. */
    private void renewCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }
}
