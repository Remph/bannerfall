package de.bannerfall.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * The path of a request as the routing sees it, for the filters that decide by path.
 *
 * <p>{@link HttpServletRequest#getRequestURI()} is the raw path: {@code /%61pi/me} doesn't start
 * with {@code /api/}, yet the routing decodes it and hands it to the {@code /api/me} handler. A
 * filter that compared the raw path could be walked around that way (code review 1.2: a banned
 * player kept playing through {@code /%61pi/...}).
 */
public final class RequestPaths {

    private RequestPaths() {}

    /** Decoded, without the context path, {@code ;} parameters and doubled slashes. */
    public static String of(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
