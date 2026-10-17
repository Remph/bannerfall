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
 *
 * <p>Once the world has ended (v1.3, 02-game-rules §22) it is frozen: every request that writes
 * ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}) is answered {@code 409 WORLD_ENDED},
 * except the short list in {@link #isOpenWhenEnded}. Reading stays open everywhere.
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
        WorldPhase phase = worldService.phase();
        if (phase == WorldPhase.PREGAME && isClosed(RequestPaths.of(request))) {
            ProblemResponses.write(response, HttpStatus.CONFLICT, ErrorCode.WORLD_NOT_OPEN);
            return;
        }
        if (phase == WorldPhase.ENDED
                && writes(request.getMethod())
                && !isOpenWhenEnded(RequestPaths.of(request))) {
            ProblemResponses.write(response, HttpStatus.CONFLICT, ErrorCode.WORLD_ENDED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean writes(String method) {
        return !(method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS"));
    }

    /**
     * Whether a writing request to {@code path} still works in a world that has ended: login and
     * logout, the password, the admin API, the chat (hiding a guild message too), private messages,
     * reports (reading, deleting, sharing) and the simulator, which changes nothing. Registering,
     * respawning and everything that changes villages, armies or guilds is closed.
     */
    public static boolean isOpenWhenEnded(String path) {
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/logout")
                || path.equals("/api/me/password")
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/chat/")
                || path.startsWith("/api/guilds/mine/chat/")
                || path.startsWith("/api/messages/")
                || path.startsWith("/api/reports/")
                || path.equals("/api/simulator");
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
                || path.equals("/api/world/status")
                || path.equals("/api/world/victory"));
    }
}
