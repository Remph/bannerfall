package de.bannerfall.npc;

import de.bannerfall.admin.AdminAuditService;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin endpoints for NPC lords (04-api *Admin*); {@code SecurityConfig} requires ADMIN.
 * Changes write an audit row (M14d).
 */
@RestController
@RequestMapping("/api/admin")
class NpcAdminController {

    private final NpcAdminService admin;
    private final AdminAuditService audit;

    NpcAdminController(NpcAdminService admin, AdminAuditService audit) {
        this.admin = admin;
        this.audit = audit;
    }

    @GetMapping("/npc-lords")
    List<NpcLordDto> lords() {
        return admin.lords();
    }

    @PostMapping("/npc-lords")
    @ResponseStatus(HttpStatus.CREATED)
    NpcLordDto spawn(@Valid @RequestBody SpawnNpcLordRequest body) {
        return audit.inTransaction(
                () -> {
                    NpcLordDto lord = admin.spawn(body.personality());
                    audit.record(
                            "SPAWN_NPC_LORD",
                            "player",
                            lord.playerId(),
                            Map.of("name", lord.name(), "personality", lord.personality()));
                    return lord;
                });
    }

    /** Places a lord in the lord guilds (09 §13.7). */
    @PutMapping("/npc-lords/{playerId}/guild")
    NpcLordDto setGuild(@PathVariable long playerId, @Valid @RequestBody NpcGuildRequest body) {
        return audit.inTransaction(
                () -> {
                    NpcLordDto before =
                            admin.lords().stream()
                                    .filter(l -> l.playerId() == playerId)
                                    .findFirst()
                                    .orElse(null);
                    NpcLordDto lord = admin.setGuild(playerId, body);
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("action", body.action());
                    details.put("from", before == null ? null : before.guildName());
                    details.put("to", lord.guildName());
                    audit.record("NPC_GUILD", "player", playerId, details);
                    return lord;
                });
    }

    @GetMapping("/npc-settings")
    NpcSettingsDto settings() {
        return new NpcSettingsDto(admin.difficulty());
    }

    @PutMapping("/npc-settings")
    NpcSettingsDto setSettings(@Valid @RequestBody NpcSettingsDto body) {
        NpcDifficulty before = admin.difficulty();
        audit.inTransaction(
                () -> {
                    admin.setDifficulty(body.difficulty());
                    audit.record(
                            "NPC_DIFFICULTY",
                            "world",
                            null,
                            Map.of("from", before, "to", body.difficulty()));
                });
        return new NpcSettingsDto(admin.difficulty());
    }

    @GetMapping("/npc-balance/custom")
    Difficulty custom() {
        return admin.custom();
    }

    @PutMapping("/npc-balance/custom")
    Difficulty setCustom(@RequestBody Difficulty body) {
        return audit.inTransaction(
                () -> {
                    Difficulty before = admin.custom();
                    Difficulty after = admin.setCustom(body);
                    audit.record(
                            "NPC_CUSTOM", "world", null, AdminAuditService.changes(before, after));
                    return after;
                });
    }
}
