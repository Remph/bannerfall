package de.bannerfall.admin;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.ProblemResponses;
import de.bannerfall.config.RequestPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The CLI's door (08-host-console §4): a request to {@code /api/admin/**} with {@code
 * Authorization: Bearer <token>} is ADMIN for that one request when the token is right ({@link
 * HostTokenAuthentication}; no session, no cookie, so {@code SecurityConfig} skips the CSRF check
 * for it). A bearer request never uses the session: with no token configured it is simply not
 * logged in (401), a wrong token is 401 and counts towards the limit ({@link AdminTokenLimiter},
 * then 429 {@code TOO_MANY_ADMIN_ATTEMPTS} for that address).
 *
 * <p>Not a bean: Spring Boot would register a {@code Filter} bean for every request a second time,
 * outside Spring Security. {@code SecurityConfig} creates it.
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("bannerfall.admin");
    private static final String BEARER = "Bearer ";

    private final AdminToken token;
    private final AdminTokenLimiter limiter;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    public AdminTokenFilter(AdminToken token, AdminTokenLimiter limiter) {
        this.token = token;
        this.limiter = limiter;
    }

    /** Does the request try the token door? {@code SecurityConfig} skips CSRF for these. */
    public static boolean isTokenRequest(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null
                && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                && RequestPaths.of(request).startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isTokenRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        // Whatever the session says: this request is the token's alone.
        SecurityContext context = contextHolder.createEmptyContext();
        contextHolder.setContext(context);
        if (!token.isConfigured()) {
            chain.doFilter(request, response); // anonymous: 401 from the entry point
            return;
        }
        String address = request.getRemoteAddr();
        if (limiter.isBlocked(address)) {
            ProblemResponses.write(
                    response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ADMIN_ATTEMPTS);
            return;
        }
        String given =
                request.getHeader(HttpHeaders.AUTHORIZATION).substring(BEARER.length()).strip();
        if (!token.matches(given)) {
            limiter.recordFailure(address);
            log.info(
                    "Wrong host token from {} for {} {}",
                    address,
                    request.getMethod(),
                    request.getRequestURI());
            ProblemResponses.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
            return;
        }
        limiter.recordSuccess(address);
        context.setAuthentication(new HostTokenAuthentication());
        try {
            chain.doFilter(request, response);
        } finally {
            contextHolder.clearContext();
        }
    }
}
