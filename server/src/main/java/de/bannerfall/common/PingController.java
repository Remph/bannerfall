package de.bannerfall.common;

import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smoke-test endpoint: proves the full stack works and gives the client the server time. */
@RestController
@RequestMapping("/api")
class PingController {

    private final Clock clock;

    PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/ping")
    PingResponse ping() {
        return new PingResponse("ok", clock.instant());
    }

    record PingResponse(String status, Instant serverTime) {}
}
