package de.bannerfall.admin;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.world.AdminWorldSettingsDto;
import de.bannerfall.world.WorldService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The guided world setup (1.6, 04-api *Admin*): the one-time code for the first admin, claiming it,
 * and the end of the setup wizard. The wizard itself saves through the usual admin endpoints.
 */
@RestController
class WorldSetupController {

    private final SetupCodeService codes;
    private final WorldService world;
    private final AdminAuditService audit;

    WorldSetupController(SetupCodeService codes, WorldService world, AdminAuditService audit) {
        this.codes = codes;
        this.world = world;
        this.audit = audit;
    }

    /** A new setup code (an older one stops working); {@code bannerfall-admin setup} prints it. */
    @PostMapping("/api/admin/setup-code")
    SetupCodeDto setupCode() {
        return audit.inTransaction(
                () -> {
                    SetupCodeDto code = codes.newCode();
                    // never the code itself: the audit log is readable on the admin page
                    audit.record(
                            "SETUP_CODE", "world", null, Map.of("expiresAt", code.expiresAt()));
                    return code;
                });
    }

    /** The logged-in account becomes an admin with the code (pre-game only). */
    @PostMapping("/api/me/setup-claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void claim(
            @AuthenticationPrincipal AccountPrincipal me,
            @Valid @RequestBody SetupClaimRequest body) {
        codes.claim(me.accountId(), body.code());
    }

    /** The host finished or skipped the wizard: the pre-game page stops offering it. */
    @PostMapping("/api/admin/world-setup/done")
    AdminWorldSettingsDto setupDone(@RequestBody WorldSetupDoneRequest body) {
        return audit.inTransaction(
                () -> {
                    world.markSetupDone();
                    audit.record("WORLD_SETUP", "world", null, Map.of("finished", body.finished()));
                    return world.adminSettings();
                });
    }
}
