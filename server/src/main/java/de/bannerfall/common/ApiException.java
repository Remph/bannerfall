package de.bannerfall.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An expected error with an HTTP status and an {@link ErrorCode}. {@link ApiExceptionHandler} turns
 * it into a ProblemDetail response. Use the subclasses for the common cases.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, ErrorCode code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    /**
     * Adds a property to the answer next to {@code code} (e.g. which order of a "recruit all"
     * failed). Returns this exception, so it can be rethrown in one line.
     */
    public ApiException withProperty(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    /** The extra properties of the answer, in the order they were added. */
    public Map<String, Object> properties() {
        return Map.copyOf(properties);
    }
}
