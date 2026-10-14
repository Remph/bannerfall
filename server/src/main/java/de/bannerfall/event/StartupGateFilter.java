package de.bannerfall.event;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.ProblemResponses;
import de.bannerfall.config.RequestPaths;
import de.bannerfall.world.PregameGateFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * While the {@link StartupGate} is closed, game requests are answered {@code 503 SERVER_STARTING}
 * with {@code Retry-After} (the web app's queries try again by themselves). Open are the same paths
 * as in the pre-game ({@link PregameGateFilter#isClosed}): login, the own account, the world's
 * settings, the chat, the guilds and the admin API, none of which catches a village up. Runs after
 * the authorization, so a visitor without login still gets {@code 401}.
 */
public class StartupGateFilter extends OncePerRequestFilter {

    /** Seconds the client is asked to wait; catching up usually takes a few. */
    static final String RETRY_AFTER_SECONDS = "5";

    private final StartupGate gate;

    public StartupGateFilter(StartupGate gate) {
        this.gate = gate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!gate.isOpen() && PregameGateFilter.isClosed(RequestPaths.of(request))) {
            response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            ProblemResponses.write(
                    response, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVER_STARTING);
            return;
        }
        chain.doFilter(request, response);
    }
}
