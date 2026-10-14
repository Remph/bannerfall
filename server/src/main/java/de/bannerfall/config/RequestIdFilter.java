package de.bannerfall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a short random id: it is in the MDC while the request runs (every log line
 * shows it as {@code req=<id>}, see logback-spring.xml) and goes back to the client as header
 * {@code X-Request-Id}. So all log lines of one request can be found together, also from an answer
 * a player reports. Runs before Spring Security, so 401 and 403 answers carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";

    /** The MDC key; logback-spring.xml prints it. */
    static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = newId();
        // Set before the chain runs: once the body is written, headers can't be added anymore.
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // The thread goes back to the pool and serves other requests next.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 8 hex digits: short enough to read out, unique enough to search the logs of a few days. Not
     * game logic, so no {@code RandomSource}.
     */
    static String newId() {
        return HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
    }
}
