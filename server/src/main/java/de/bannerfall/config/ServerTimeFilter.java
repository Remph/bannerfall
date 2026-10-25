package de.bannerfall.config;

import de.bannerfall.common.AppVersion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@code X-Server-Time} (ISO-8601 UTC, milliseconds) to every response; the web client
 * computes its clock offset from it (web/src/game/serverTime.ts). Also adds {@code
 * X-Server-Version} ({@link AppVersion}): a page that was open across an update sees the new
 * version on its next request: after a patch it shows a "reload" strip, after a minor or major
 * update it reloads on its own, instead of running the old code against the new server's data
 * (web/src/game/version.ts). Runs before Spring Security, so 401 and 403 answers carry both.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ServerTimeFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Server-Time";

    static final String VERSION_HEADER = "X-Server-Version";

    private final Clock clock;

    private final AppVersion version;

    ServerTimeFilter(Clock clock, AppVersion version) {
        this.clock = clock;
        this.version = version;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Set before the chain runs: once the body is written, headers can't be added anymore.
        response.setHeader(HEADER, clock.instant().truncatedTo(ChronoUnit.MILLIS).toString());
        response.setHeader(VERSION_HEADER, version.get());
        chain.doFilter(request, response);
    }
}
