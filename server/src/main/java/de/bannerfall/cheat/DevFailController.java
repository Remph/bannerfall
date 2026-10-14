package de.bannerfall.cheat;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev only: a request that fails with an unexpected error, to check the log files by hand (M4d,
 * 01-architecture §7). Logged in, open {@code http://localhost:5173/api/dev/fail} in the browser:
 * the answer is 500 {@code INTERNAL_ERROR}, and server.log and server.err get the error with its
 * stack trace and the request id from the {@code X-Request-Id} header.
 */
@RestController
@Profile("dev")
class DevFailController {

    @GetMapping("/api/dev/fail")
    void fail() {
        throw new IllegalStateException("Test error from GET /api/dev/fail (dev only)");
    }
}
