package de.bannerfall.solo;

import de.bannerfall.admin.HostTokenAuthentication;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.ProblemResponses;
import de.bannerfall.config.RequestPaths;
import de.bannerfall.world.PregameGateFilter;
import de.bannerfall.world.WorldService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A game request or the opening of a game connection by a logged-in human wakes a sleeping solo
 * world before anything reads the game (2.1, 12-solo-and-allies §3; {@link WorldSleep}). Not
 * counted: the admin API (the host's token or his speed control), login and the public status.
 *
 * <p>While the host has paused the world, every request that writes is answered {@code 409
 * WORLD_PAUSED}, except what stays open in an ended world ({@link
 * PregameGateFilter#isOpenWhenEnded}): the chat, messages, reports, the account and the admin API.
 * Runs after the authorization and the start-up gate.
 */
public class WorldSleepFilter extends OncePerRequestFilter {

    private final WorldSleep sleep;
    private final WorldService worldService;

    public WorldSleepFilter(WorldSleep sleep, WorldService worldService) {
        this.sleep = sleep;
        this.worldService = worldService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = RequestPaths.of(request);
        if (isGame(path) && isHuman(SecurityContextHolder.getContext().getAuthentication())) {
            sleep.activity();
            if (worldService.paused()
                    && writes(request.getMethod())
                    && !PregameGateFilter.isOpenWhenEnded(path)) {
                ProblemResponses.write(response, HttpStatus.CONFLICT, ErrorCode.WORLD_PAUSED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The game's API and its WebSocket; not the admin API, login or the public status. */
    static boolean isGame(String path) {
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            return true;
        }
        return path.startsWith("/api/")
                && !path.startsWith("/api/admin/")
                && !path.startsWith("/api/auth/")
                && !path.equals("/api/ping")
                && !path.equals("/api/world/status")
                && !path.equals("/api/world/operator");
    }

    private static boolean isHuman(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !(auth instanceof HostTokenAuthentication);
    }

    private static boolean writes(String method) {
        return !(method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS"));
    }
}
