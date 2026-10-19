package de.bannerfall.account;

import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging in through the account server (1.5, 01-architecture §4.9a). Spring Security's OAuth2
 * login checks the account server's answer (state, PKCE, the signed ID token); this decides what
 * the checked login means on this world:
 *
 * <ul>
 *   <li>A player of this world: logged in like after a password login.
 *   <li>Somebody new: remembered in the session until the join page has the invite code ({@link
 *       #join}).
 *   <li>A local player who asked to link his account ({@link #startLink}): from now on his account
 *       belongs to the account server's account, his password here is gone.
 * </ul>
 *
 * <p>The answer is a path of the web app to send the browser to; what went wrong waits in the
 * session for {@code GET /api/auth/external}.
 */
@Service
public class ExternalLoginService {

    private static final Logger log = LoggerFactory.getLogger(ExternalLoginService.class);

    /** The game's name rule ({@link RegisterRequest}). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{3,24}");

    static final String PENDING = ExternalLoginService.class.getName() + ".pending";
    private static final String PROBLEM = ExternalLoginService.class.getName() + ".problem";
    static final String LINKING = ExternalLoginService.class.getName() + ".linking";

    /**
     * How long a login at the account server waits for the join page, and a started link for the
     * way back from the account server: a stale one would skip the account server's checks (a ban
     * there since).
     */
    static final Duration WAITS = Duration.ofMinutes(10);

    /** Logged in at the account server, no player here yet. */
    private record Pending(String issuer, String subject, String name, Instant at)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    /** The logged-in player asked to link his account (the password checked). */
    private record Linking(long accountId, Instant at) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    private record Problem(ExternalLoginProblem problem, Instant bannedUntil, String banReason)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    private final AccountServerSettings settings;
    private final AccountServerClient client;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LoginService loginService;
    private final AchievementService achievements;
    private final Clock clock;

    ExternalLoginService(
            AccountServerSettings settings,
            AccountServerClient client,
            AccountRepository accounts,
            AccountService accountService,
            LoginService loginService,
            AchievementService achievements,
            Clock clock) {
        this.settings = settings;
        this.client = client;
        this.accounts = accounts;
        this.accountService = accountService;
        this.loginService = loginService;
        this.achievements = achievements;
        this.clock = clock;
    }

    /**
     * The account server confirmed who the browser's user is ({@code issuer} and {@code subject}
     * from the checked ID token, {@code name} its {@code preferred_username}).
     *
     * @return where the browser goes next
     */
    @Transactional
    public String loggedIn(
            String issuer,
            String subject,
            String name,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession();
        Object linking = session.getAttribute(LINKING);
        session.removeAttribute(LINKING);
        session.removeAttribute(PENDING);
        session.removeAttribute(PROBLEM);
        // Spring's login left its own token in this request's context; ours replaces it below,
        // and the session never stored it.
        SecurityContextHolder.clearContext();
        Optional<Account> existing =
                accounts.findByExternalIssuerAndExternalSubject(issuer, subject);
        if (linking instanceof Linking started) {
            return link(started, existing, issuer, subject, request, response);
        }
        if (settings.mode() == AccountLoginMode.OFF) {
            return problem(session, ExternalLoginProblem.UNAVAILABLE, "/login");
        }
        if (existing.isPresent()) {
            Account account = existing.get();
            if (account.isDisabled()) {
                return problem(session, ExternalLoginProblem.DELETED, "/login");
            }
            if (account.isBannedAt(clock.instant())) {
                log.info("Login refused: '{}' is banned", account.getUsername());
                session.setAttribute(
                        PROBLEM,
                        new Problem(
                                ExternalLoginProblem.BANNED,
                                account.getBannedUntil(),
                                account.getBanReason()));
                return "/login";
            }
            loginService.loginWithoutPassword(account, request, response);
            achievements.checkAtLogin(account.getId());
            return "/";
        }
        session.setAttribute(PENDING, new Pending(issuer, subject, name, clock.instant()));
        return "/join";
    }

    /** The account server or the check of its answer failed (or the player cancelled there). */
    public String failed(HttpServletRequest request, ExternalLoginProblem problem) {
        HttpSession session = request.getSession();
        Object linking = session.getAttribute(LINKING);
        session.removeAttribute(LINKING);
        return problem(session, problem, linking == null ? "/login" : "/password");
    }

    /** {@code GET /api/auth/external}: the pending join and the last problem, which it forgets. */
    public ExternalLoginDto state(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new ExternalLoginDto(null, null, null, null);
        }
        String name =
                session.getAttribute(PENDING) instanceof Pending pending ? pending.name() : null;
        Object stored = session.getAttribute(PROBLEM);
        session.removeAttribute(PROBLEM);
        return stored instanceof Problem problem
                ? new ExternalLoginDto(
                        name, problem.problem(), problem.bannedUntil(), problem.banReason())
                : new ExternalLoginDto(name, null, null, null);
    }

    /**
     * {@code POST /api/auth/join}: the player who logged in at the account server becomes a player
     * of this world, under his name there, and is logged in.
     */
    @Transactional
    public Account join(
            String inviteCode, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (settings.mode() == AccountLoginMode.OFF
                || session == null
                || !(session.getAttribute(PENDING) instanceof Pending pending)
                || stale(pending.at())) {
            throw new GameRuleException(
                    ErrorCode.EXTERNAL_LOGIN_MISSING, "Log in through the account server first");
        }
        if (pending.name() == null || !NAME.matcher(pending.name()).matches()) {
            throw new GameRuleException(
                    ErrorCode.EXTERNAL_NAME_INVALID,
                    "The account server's name doesn't fit this world's name rules");
        }
        // Joined meanwhile in another tab: that login is over, the next one at the account server
        // logs in directly (and is checked there again: a ban since, a deleted account).
        if (accounts.findByExternalIssuerAndExternalSubject(pending.issuer(), pending.subject())
                .isPresent()) {
            session.removeAttribute(PENDING);
            throw new GameRuleException(
                    ErrorCode.EXTERNAL_LOGIN_MISSING, "Log in through the account server again");
        }
        Account account =
                accountService.registerExternal(
                        pending.name(), pending.issuer(), pending.subject(), inviteCode);
        session.removeAttribute(PENDING);
        loginService.loginWithoutPassword(account, request, response);
        log.info("'{}' joined through the account server", account.getUsername());
        return account;
    }

    /**
     * {@code POST /api/me/link}: the logged-in player wants his account to belong to his account at
     * the account server. The browser goes there next; {@link #loggedIn} finishes the link. Like a
     * password change it needs the current password: linking ends the password for good, so a
     * borrowed or stolen session must not be enough.
     */
    @Transactional
    public void startLink(long accountId, String currentPassword, HttpServletRequest request) {
        Account account = accounts.findById(accountId).orElseThrow();
        if (settings.mode() != AccountLoginMode.LINKING || account.isLinked()) {
            throw new GameRuleException(ErrorCode.LINK_NOT_ALLOWED, "Linking isn't possible");
        }
        accountService.checkPassword(account, currentPassword, request.getRemoteAddr());
        request.getSession().setAttribute(LINKING, new Linking(accountId, clock.instant()));
    }

    /**
     * {@code POST /api/auth/backchannel-logout}: the account server says an account's logins must
     * end (banned or deleted there). Ends its sessions on this world, if it has a player here.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException if the token isn't valid
     */
    @Transactional
    public void backchannelLogout(String logoutToken) {
        String subject = client.subjectOfLogoutToken(logoutToken);
        accounts.findByExternalIssuerAndExternalSubject(settings.issuer(), subject)
                .ifPresent(
                        account -> {
                            account.endSessions();
                            accountService.sessionsEnded(account);
                            log.info(
                                    "The account server ended the sessions of '{}'",
                                    account.getUsername());
                        });
    }

    private String link(
            Linking started,
            Optional<Account> existing,
            String issuer,
            String subject,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession();
        Account account = accounts.findById(started.accountId()).orElse(null);
        // The session that asked is still logged in as before; the account guard ends it if the
        // account was deleted or banned meanwhile. A link started long ago, or by a login this
        // session no longer has, isn't finished.
        if (account == null
                || settings.mode() != AccountLoginMode.LINKING
                || stale(started.at())
                || !Long.valueOf(started.accountId()).equals(sessionAccountId(session))) {
            return problem(session, ExternalLoginProblem.LINK_NOT_ALLOWED, "/password");
        }
        if (account.isLinked()) {
            boolean same =
                    issuer.equals(account.getExternalIssuer())
                            && subject.equals(account.getExternalSubject());
            return same
                    ? "/password"
                    : problem(session, ExternalLoginProblem.LINK_NOT_ALLOWED, "/password");
        }
        if (existing.isPresent()) {
            return problem(session, ExternalLoginProblem.ALREADY_LINKED, "/password");
        }
        account.linkTo(issuer, subject);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // linked to another player at the same moment
            throw new GameRuleException(ErrorCode.LINK_NOT_ALLOWED, "Already linked");
        }
        accountService.sessionsEnded(account);
        // this browser stays logged in, with the account's new session version
        loginService.renewSession(account, request, response);
        log.info("'{}' linked the account to the account server", account.getUsername());
        return "/password";
    }

    private boolean stale(Instant at) {
        return at.plus(WAITS).isBefore(clock.instant());
    }

    /** The account this session is logged in as (the context Spring stored), or null. */
    private static Long sessionAccountId(HttpSession session) {
        return session.getAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY)
                                instanceof SecurityContext context
                        && context.getAuthentication() != null
                        && context.getAuthentication().getPrincipal()
                                instanceof AccountPrincipal principal
                ? principal.accountId()
                : null;
    }

    private static String problem(HttpSession session, ExternalLoginProblem problem, String path) {
        session.setAttribute(PROBLEM, new Problem(problem, null, null));
        return path;
    }
}
