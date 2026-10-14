package de.bannerfall.abuse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The host's view of the suspicion log and the invariant check (M14e, 08-host-console §2). */
@RestController
@RequestMapping("/api/admin")
class AdminAbuseController {

    private final AbuseService abuse;
    private final InvariantCheck invariants;

    AdminAbuseController(AbuseService abuse, InvariantCheck invariants) {
        this.abuse = abuse;
        this.invariants = invariants;
    }

    /** Accounts with suspicious requests in the last {@code days} days, most recent first. */
    @GetMapping("/suspicious")
    List<SuspiciousAccountDto> suspicious(
            @RequestParam(defaultValue = "30") @Min(1) @Max(30) int days) {
        return abuse.suspicious(days);
    }

    /** Runs the invariant check now (read only; nothing is repaired). */
    @PostMapping("/invariants")
    InvariantReportDto check() {
        return invariants.run();
    }
}
