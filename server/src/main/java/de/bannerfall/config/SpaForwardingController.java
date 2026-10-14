package de.bannerfall.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app for its own routes (01-architecture §8): a reload or a shared link such as
 * {@code /v/12/hq} or {@code /players/7} is forwarded to {@code /index.html}, and React Router
 * takes over. Only in the production image does {@code static/} contain the web build; in
 * development Vite serves the app and this controller leads nowhere.
 *
 * <p>Paths with a dot in a segment (files like {@code /assets/app.js} or {@code
 * /manifest.webmanifest}) are left to the static resource handler, and the first segment must not
 * be one of the server's own areas, so an unknown {@code /api/...} stays a JSON error and not the
 * web app. The patterns cover up to five segments (the deepest route is {@code
 * /v/:villageId/recruit/:building}).
 */
@Controller
class SpaForwardingController {

    private static final String SEGMENT = "[^.]+";
    // Not the first segment: the API, the WebSocket, the actuator and the API docs.
    private static final String FIRST = "(?!api$|ws$|actuator$|v3$|swagger-ui$)" + SEGMENT;

    @GetMapping({
        "/",
        "/{a:" + FIRST + "}",
        "/{a:" + FIRST + "}/{b:" + SEGMENT + "}",
        "/{a:" + FIRST + "}/{b:" + SEGMENT + "}/{c:" + SEGMENT + "}",
        "/{a:" + FIRST + "}/{b:" + SEGMENT + "}/{c:" + SEGMENT + "}/{d:" + SEGMENT + "}",
        "/{a:" + FIRST + "}/{b:" + SEGMENT + "}/{c:" + SEGMENT + "}/{d:" + SEGMENT + "}/{e:"
                + SEGMENT + "}"
    })
    String forward() {
        return "forward:/index.html";
    }
}
