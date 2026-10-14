package de.bannerfall.config;

import de.bannerfall.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a ProblemDetail with {@code code} straight into the response, for filters that answer
 * before Spring MVC (and its {@code ApiExceptionHandler}) is reached.
 */
public final class ProblemResponses {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ProblemResponses() {}

    public static void write(HttpServletResponse response, HttpStatus status, ErrorCode code)
            throws IOException {
        write(response, status, code, Map.of());
    }

    /**
     * With extra properties, e.g. {@code bannedUntil} of {@code ACCOUNT_BANNED} (M14e); null values
     * are left out.
     */
    public static void write(
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            Map<String, ? extends Object> extra)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("code", code.name());
        extra.forEach(
                (key, value) -> {
                    if (value != null) {
                        body.put(key, value);
                    }
                });
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
