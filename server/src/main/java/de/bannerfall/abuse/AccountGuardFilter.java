package de.bannerfall.abuse;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.account.AccountStates;
import de.bannerfall.admin.AdminTokenFilter;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.ProblemResponses;
import de.bannerfall.config.RequestPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks every request of a logged-in account before anything else of it runs (M14e,
 * 01-architecture §4.9), right after Spring Security has loaded the session's login:
 *
 * <ol>
 *   <li>A request body larger than {@value #MAX_BODY_BYTES} bytes under {@code /api/**} is refused
 *       with 413 {@code BODY_TOO_LARGE} (the API's bodies are a few hundred bytes).
 *   <li>The session ends if its account was deleted, banned, or its password changed since the
 *       login ({@link AccountStates}, cached for a few seconds, forgotten at once on a change):
 *       banned → 403 {@code ACCOUNT_BANNED} with the end and the reason; otherwise the request goes
 *       on without login (401 where a login is needed).
 *   <li>The write rate limit ({@link WriteRateLimiter}): too many POST / PUT / PATCH / DELETE under
 *       {@code /api/**} → 429 {@code TOO_MANY_REQUESTS}. Login and registration ({@code
 *       /api/auth/**}) have their own limits: per name and address ({@code LoginRateLimiter}) and
 *       per address ({@link AuthAddressLimiter}, checked here for every visitor, logged in or not).
 * </ol>
 *
 * <p>Host token requests ({@code /api/admin/**} with a bearer token) aren't an account's: they pass
 * untouched.
 */
public class AccountGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("bannerfall.abuse");

    /**
     * The login through the account server (1.5): its start, the way back from there, and the
     * account server's logout tokens. Limited per address before anything else
     * (AuthAddressLimiter).
     */
    private static final Set<String> EXTERNAL_LOGIN =
            Set.of(
                    "/oauth2/authorization/accounts",
                    "/login/oauth2/code/accounts",
                    "/api/auth/backchannel-logout");

    /** Largest request body under {@code /api/**}. */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AccountStates states;
    private final WriteRateLimiter rateLimiter;
    private final AuthAddressLimiter authLimiter;
    private final AbuseService abuse;
    private final Clock clock;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    public AccountGuardFilter(
            AccountStates states,
            WriteRateLimiter rateLimiter,
            AuthAddressLimiter authLimiter,
            AbuseService abuse,
            Clock clock) {
        this.states = states;
        this.rateLimiter = rateLimiter;
        this.authLimiter = authLimiter;
        this.abuse = abuse;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = RequestPaths.of(request);
        if (EXTERNAL_LOGIN.contains(path) && !authLimiter.tryExternal(request.getRemoteAddr())) {
            ProblemResponses.write(
                    response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        if (!path.startsWith("/api/") && !path.equals("/ws")) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            ProblemResponses.write(
                    response, HttpStatus.CONTENT_TOO_LARGE, ErrorCode.BODY_TOO_LARGE);
            return;
        }
        if (!authAttemptAllowed(request, path)) {
            // Not logged: a flood would fill the log, one line per refused request.
            ProblemResponses.write(
                    response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        HttpServletRequest limited = new LimitedBodyRequest(request);
        if (AdminTokenFilter.isTokenRequest(request)) {
            chain.doFilter(limited, response);
            return;
        }
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            chain.doFilter(limited, response);
            return;
        }

        long accountId = principal.accountId();
        AccountStates.State state = states.get(accountId);
        Instant now = clock.instant();
        boolean banned = state.isBannedAt(now);
        if (!state.exists()
                || state.disabled()
                || banned
                || state.sessionVersion() != principal.sessionVersion()) {
            endSession(request);
            if (banned && !path.startsWith("/api/auth/")) {
                ProblemResponses.write(
                        response,
                        HttpStatus.FORBIDDEN,
                        ErrorCode.ACCOUNT_BANNED,
                        Map.of(
                                "bannedUntil",
                                state.bannedUntil().toString(),
                                "reason",
                                state.banReason() == null ? "" : state.banReason()));
                return;
            }
            chain.doFilter(limited, response);
            return;
        }

        if (WRITES.contains(request.getMethod())
                && !path.startsWith("/api/auth/")
                && !rateLimiter.tryWrite(accountId)) {
            abuse.suspect(
                    accountId,
                    AbuseKind.RATE_LIMITED,
                    AbuseService.endpoint(request),
                    "write rate limit");
            ProblemResponses.write(
                    response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(limited, response);
    }

    /** Logins and registrations per address (D2); every other request passes. */
    private boolean authAttemptAllowed(HttpServletRequest request, String path) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        return switch (path) {
            case "/api/auth/login" -> authLimiter.tryLogin(request.getRemoteAddr());
            case "/api/auth/register", "/api/auth/join" ->
                    authLimiter.tryRegister(request.getRemoteAddr());
            default -> true;
        };
    }

    /** The session's login is no longer valid: the session goes, this request is anonymous. */
    private void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.info("Ended a session of account {}", accountIdOf(request));
            session.invalidate();
        }
        contextHolder.setContext(contextHolder.createEmptyContext());
    }

    private Object accountIdOf(HttpServletRequest request) {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        return authentication != null
                        && authentication.getPrincipal() instanceof AccountPrincipal principal
                ? principal.accountId()
                : "?";
    }

    /**
     * A body without a {@code Content-Length} (chunked) is cut off at {@value #MAX_BODY_BYTES}
     * bytes: reading past it fails, which the API answers like a broken body (400).
     */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        LimitedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedStream(super.getInputStream());
            }
            return stream;
        }
    }

    private static final class LimitedStream extends ServletInputStream {

        private final ServletInputStream in;
        private long read;

        LimitedStream(ServletInputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = in.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > MAX_BODY_BYTES) {
                throw new IOException("Request body larger than " + MAX_BODY_BYTES + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
