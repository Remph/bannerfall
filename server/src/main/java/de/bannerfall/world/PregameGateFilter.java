package de.bannerfall.world;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.ProblemResponses;
import de.bannerfall.config.RequestPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In the pre-game there is no map and nobody has a village (M14g): every API request that is not on
 * the short list below is answered {@code 409 WORLD_NOT_OPEN}. A list of what stays open (not of
 * what is closed), so a new game endpoint is closed in the pre-game without anybody thinking of it;
 * a feature that works in the pre-game (guilds, M15a) has its path here.
 *
 * <p>Open: the account itself (login, own player, password), the world's settings, the chat, the
 * guilds (founding one is possible before the opening) and the admin API. Runs after the
 * authorization, so a visitor without login still gets {@code 401}.
 */
public class PregameGateFilter extends OncePerRequestFilter {

    private final WorldService worldService;

    public PregameGateFilter(WorldService worldService) {
        this.worldService = worldService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (worldService.phase() == WorldPhase.PREGAME && isClosed(RequestPaths.of(request))) {
            ProblemResponses.write(response, HttpStatus.CONFLICT, ErrorCode.WORLD_NOT_OPEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Whether {@code path} is a game request, closed in the pre-game. The start-up gate ({@code
     * StartupGateFilter}) holds back the same requests.
     */
    public static boolean isClosed(String path) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !(path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/chat/")
                || path.equals("/api/guilds")
                || path.startsWith("/api/guilds/")
                || path.equals("/api/ping")
                || path.equals("/api/me")
                || path.equals("/api/me/password")
                || path.equals("/api/world")
                || path.equals("/api/world/status"));
    }
}
