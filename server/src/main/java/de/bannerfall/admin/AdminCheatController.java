package de.bannerfall.admin;

import de.bannerfall.cheat.AddResourcesRequest;
import de.bannerfall.cheat.AddUnitsRequest;
import de.bannerfall.cheat.CheatService;
import de.bannerfall.cheat.CheatTarget;
import de.bannerfall.gamedata.ResourceAmounts;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The host cheats (08-host-console §3): like the dev cheats, but on any village and only while
 * {@code bannerfall.cheats.enabled} (env {@code BANNERFALL_CHEATS_ENABLED}) is true – otherwise the
 * class doesn't exist and the paths are plain 404. Each answers {@code 204} and writes an audit
 * row; an unknown village is {@code 404 VILLAGE_NOT_FOUND}.
 */
@RestController
@ConditionalOnBooleanProperty("bannerfall.cheats.enabled")
@RequestMapping("/api/admin/cheats/villages/{villageId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
class AdminCheatController {

    private final CheatService cheats;
    private final AdminAuditService audit;

    AdminCheatController(CheatService cheats, AdminAuditService audit) {
        this.cheats = cheats;
        this.audit = audit;
    }

    @PostMapping("/resources")
    void addResources(@PathVariable long villageId, @Valid @RequestBody AddResourcesRequest body) {
        audit.inTransaction(
                () -> {
                    cheats.addResources(
                            CheatTarget.any(villageId),
                            new ResourceAmounts(body.wood(), body.clay(), body.iron()));
                    audit.record(
                            "CHEAT_RESOURCES",
                            "village",
                            villageId,
                            Map.of("wood", body.wood(), "clay", body.clay(), "iron", body.iron()));
                });
    }

    @PostMapping("/units")
    void addUnits(@PathVariable long villageId, @Valid @RequestBody AddUnitsRequest body) {
        audit.inTransaction(
                () -> {
                    cheats.addUnits(CheatTarget.any(villageId), body.units());
                    Map<String, Integer> units = new TreeMap<>();
                    body.units().forEach((unit, count) -> units.put(unit.name(), count));
                    audit.record("CHEAT_UNITS", "village", villageId, units);
                });
    }

    /**
     * The orders are finished in a transaction of their own and processed afterwards (see {@link
     * CheatService#finishOrders}), so the audit row follows in its own transaction once that
     * worked.
     */
    @PostMapping("/finish-orders")
    void finishOrders(@PathVariable long villageId) {
        cheats.finishOrders(CheatTarget.any(villageId));
        audit.inTransaction(
                () -> audit.record("CHEAT_FINISH_ORDERS", "village", villageId, Map.of()));
    }

    /** Like {@link #finishOrders}. */
    @PostMapping("/finish-movements")
    void finishMovements(@PathVariable long villageId) {
        cheats.finishMovements(CheatTarget.any(villageId));
        audit.inTransaction(
                () -> audit.record("CHEAT_FINISH_MOVEMENTS", "village", villageId, Map.of()));
    }
}
