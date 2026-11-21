package de.bannerfall.solo;

import de.bannerfall.admin.AdminAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The host's speed control of a solo world (2.1, 12-solo-and-allies §3, 04-api *Admin*): pause,
 * play and fast forward. Admin only, like every {@code /api/admin} path.
 */
@RestController
@RequestMapping("/api/admin/world-clock")
class WorldClockController {

    private final WorldSleep sleep;
    private final AdminAuditService audit;

    WorldClockController(WorldSleep sleep, AdminAuditService audit) {
        this.sleep = sleep;
        this.audit = audit;
    }

    @GetMapping
    WorldClockDto clock() {
        return sleep.state();
    }

    /**
     * Pauses ({@code paused: true}) or plays ({@code false}) and sets the fast forward ({@code
     * speed} 1, 2 or 4); a missing value stays as it is.
     */
    @PutMapping
    WorldClockDto change(@RequestBody WorldClockRequest body) {
        WorldClockDto before = sleep.state();
        if (body.speed() != null) {
            sleep.setSpeed(body.speed());
        }
        if (Boolean.TRUE.equals(body.paused())) {
            sleep.pause();
        } else if (Boolean.FALSE.equals(body.paused())) {
            sleep.play();
        }
        WorldClockDto after = sleep.state();
        audit.inTransaction(
                () ->
                        audit.record(
                                "WORLD_CLOCK",
                                "world",
                                null,
                                AdminAuditService.changes(before, after)));
        return after;
    }
}
