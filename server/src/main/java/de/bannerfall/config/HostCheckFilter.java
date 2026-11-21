package de.bannerfall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses requests for a foreign host on a world without Caddy (2.1 security review, finding 2):
 * the solo world on a PC (compose.solo.yaml) is reached at {@code http://localhost:8461}, and
 * without this check a website could rebind its own name to 127.0.0.1 and talk to the game as its
 * own origin. On when {@code bannerfall.host-check.domain} (env {@code BANNERFALL_DOMAIN}) carries
 * a scheme: then only that host and the loopback names are answered, on any port (the health check
 * and the host console talk to port 8080 inside the container), everything else gets 421. A domain
 * without a scheme is a world behind Caddy, which only routes that domain: no check. A request
 * without a {@code Host} header (HTTP/1.0, the image's health check) passes: a browser always sends
 * one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HostCheckFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");

    /** The host names answered, lower case; null = no check. */
    private final Set<String> allowed;

    HostCheckFilter(@Value("${bannerfall.host-check.domain:}") String domain) {
        this.allowed = allowedHosts(domain);
    }

    /**
     * The host names a world with this address answers: null (no check) for an address without a
     * scheme or none at all, else its host plus the loopback names. An address with a scheme but no
     * host (a typo) answers the loopback names only.
     */
    static Set<String> allowedHosts(String domain) {
        String address = domain == null ? "" : domain.strip().toLowerCase(Locale.ROOT);
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            return null;
        }
        String host;
        try {
            host = URI.create(address).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null || host.isEmpty()) {
            return LOOPBACK;
        }
        Set<String> hosts = new HashSet<>(LOOPBACK);
        hosts.add(host);
        return Set.copyOf(hosts);
    }

    /**
     * The host name of a {@code Host} header without its port, lower case: {@code localhost:8461} →
     * {@code localhost}, {@code [::1]:8461} → {@code [::1]}.
     */
    static String hostName(String header) {
        String value = header.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end < 0 ? value : value.substring(0, end + 1);
        }
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(0, colon);
    }

    /** Whether a request with this {@code Host} header (null = none) is answered. */
    static boolean accepts(Set<String> allowed, String header) {
        return allowed == null || header == null || allowed.contains(hostName(header));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (accepts(allowed, request.getHeader(HttpHeaders.HOST))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(421); // Misdirected Request
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write("This world only answers at its own address.\n");
    }
}
