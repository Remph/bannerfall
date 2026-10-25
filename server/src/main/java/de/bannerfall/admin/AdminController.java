package de.bannerfall.admin;

import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.chat.ChatMessageDto;
import de.bannerfall.chat.ChatService;
import de.bannerfall.cheat.SpawnBarbarianRequest;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventAdminService;
import de.bannerfall.event.FailedEventDto;
import de.bannerfall.npc.NpcAdminService;
import de.bannerfall.victory.VictoryService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.AdminWorldSettingsDto;
import de.bannerfall.world.WorldEndRequest;
import de.bannerfall.world.WorldOpeningRequest;
import de.bannerfall.world.WorldOpeningService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettingsUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin endpoints (04-api *Admin*, 08-host-console §2): status, world settings, hiding a chat
 * message and the broadcast, failed events, spawning a barbarian village, the audit log. {@code
 * SecurityConfig} lets only the host token and admins in. Players are in {@code
 * AdminPlayersController}, cheats in {@code AdminCheatController}, NPC lords in {@code
 * NpcAdminController}. Every change writes an audit row.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final AdminStatusService status;
    private final WorldService world;
    private final WorldOpeningService opening;
    private final VictoryService victory;
    private final ChatService chat;
    private final EventAdminService eventAdmin;
    private final BarbarianService barbarians;
    private final AdminAuditService audit;
    private final NpcAdminService npcAdmin;
    private final DebugLogging debugLogging;

    AdminController(
            AdminStatusService status,
            WorldService world,
            WorldOpeningService opening,
            VictoryService victory,
            ChatService chat,
            EventAdminService eventAdmin,
            BarbarianService barbarians,
            AdminAuditService audit,
            NpcAdminService npcAdmin,
            DebugLogging debugLogging) {
        this.status = status;
        this.world = world;
        this.opening = opening;
        this.victory = victory;
        this.chat = chat;
        this.eventAdmin = eventAdmin;
        this.barbarians = barbarians;
        this.audit = audit;
        this.npcAdmin = npcAdmin;
        this.debugLogging = debugLogging;
    }

    @GetMapping("/status")
    AdminStatusDto status() {
        return status.status();
    }

    /**
     * Switches the game's debug logs on or off while the server runs (1.6.3, 08-host-console §2a).
     * Not stored: a restart is back at INFO. The audit row says who switched it.
     */
    @PutMapping("/debug-logging")
    DebugLoggingDto setDebugLogging(@Valid @RequestBody DebugLoggingRequest body) {
        return audit.inTransaction(
                () -> {
                    boolean enabled = Boolean.TRUE.equals(body.enabled());
                    debugLogging.set(enabled);
                    audit.record("DEBUG_LOGGING", "server", null, Map.of("enabled", enabled));
                    return new DebugLoggingDto(enabled);
                });
    }

    @GetMapping("/world-settings")
    AdminWorldSettingsDto worldSettings() {
        return world.adminSettings();
    }

    @PutMapping("/world-settings")
    AdminWorldSettingsDto setWorldSettings(@Valid @RequestBody WorldSettingsUpdateRequest body) {
        return audit.inTransaction(
                () -> {
                    AdminWorldSettingsDto before = world.adminSettings();
                    AdminWorldSettingsDto after = world.updateSettings(body);
                    if (before.npcGuildsEnabled() && !after.npcGuildsEnabled()) {
                        npcAdmin.disbandLordGuilds(); // 09-npc-lords §13: off ends them
                    }
                    audit.record(
                            "WORLD_SETTINGS",
                            "world",
                            null,
                            AdminAuditService.changes(before, after));
                    return after;
                });
    }

    /**
     * Pre-game only (M14g): sets the time the world opens, or clears it ({@code opensAt: null}; the
     * world then waits for a time). A time in the past opens it at once.
     */
    @PutMapping("/world-opening")
    AdminWorldSettingsDto setWorldOpening(@Valid @RequestBody WorldOpeningRequest body) {
        return audit.inTransaction(
                () -> {
                    AdminWorldSettingsDto before = world.adminSettings();
                    opening.schedule(body.opensAt());
                    AdminWorldSettingsDto after = world.adminSettings();
                    audit.record(
                            "WORLD_OPENING",
                            "world",
                            null,
                            AdminAuditService.changes(before, after));
                    return after;
                });
    }

    /** Pre-game only (M14g): opens the world now. */
    @PostMapping("/world-opening/now")
    AdminWorldSettingsDto openWorldNow() {
        return audit.inTransaction(
                () -> {
                    AdminWorldSettingsDto before = world.adminSettings();
                    opening.openNow();
                    AdminWorldSettingsDto after = world.adminSettings();
                    audit.record(
                            "WORLD_OPENING",
                            "world",
                            null,
                            AdminAuditService.changes(before, after));
                    return after;
                });
    }

    /**
     * Sets, moves or clears ({@code endsAt: null}) the end date (v1.3, 02-game-rules §22). A time
     * in the past ends the world at once.
     */
    @PutMapping("/world-end")
    AdminWorldSettingsDto setWorldEnd(@Valid @RequestBody WorldEndRequest body) {
        return worldEndChange(() -> victory.setEndsAt(body.endsAt()));
    }

    /** Ends the world now (§22): the side with the most points wins. */
    @PostMapping("/world-end/now")
    AdminWorldSettingsDto endWorldNow() {
        return worldEndChange(victory::endNow);
    }

    /** Takes the end of the world back (§22): it runs again, without an end date. */
    @PostMapping("/world-end/reopen")
    AdminWorldSettingsDto reopenWorld() {
        return worldEndChange(victory::reopen);
    }

    private AdminWorldSettingsDto worldEndChange(Runnable change) {
        return audit.inTransaction(
                () -> {
                    AdminWorldSettingsDto before = world.adminSettings();
                    change.run();
                    AdminWorldSettingsDto after = world.adminSettings();
                    audit.record(
                            "WORLD_END", "world", null, AdminAuditService.changes(before, after));
                    return after;
                });
    }

    @DeleteMapping("/chat/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hideChatMessage(@PathVariable long messageId) {
        audit.inTransaction(
                () -> {
                    chat.hide(messageId);
                    audit.record("HIDE_CHAT_MESSAGE", "chat", messageId, Map.of());
                });
    }

    /** A message from the host to everybody, in the chat without an author (M14d). */
    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessageDto broadcast(@RequestBody BroadcastRequest body) {
        return audit.inTransaction(
                () -> {
                    ChatMessageDto message = chat.broadcast(body.text());
                    audit.record("BROADCAST", "chat", message.id(), Map.of("text", message.text()));
                    return message;
                });
    }

    @GetMapping("/events/failed")
    List<FailedEventDto> failedEvents() {
        return eventAdmin.failed();
    }

    @PostMapping("/events/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retryEvent(@PathVariable long id) {
        audit.inTransaction(
                () -> {
                    eventAdmin.retry(id);
                    audit.record("RETRY_EVENT", "event", id, Map.of());
                });
    }

    /**
     * A barbarian village on exactly this field (like the dev cheat, 04-api): {@code 201} with its
     * ref, {@code 409 FIELD_NOT_FREE}. Not a cheat: it only adds a target, so it doesn't need the
     * cheat switch.
     */
    @PostMapping("/barbarians")
    @ResponseStatus(HttpStatus.CREATED)
    VillageRefDto spawnBarbarian(@Valid @RequestBody SpawnBarbarianRequest body) {
        world.requireOpen();
        return audit.inTransaction(
                () -> {
                    Village village = barbarians.spawnAt(new Coordinates(body.x(), body.y()));
                    audit.record(
                            "SPAWN_BARBARIAN",
                            "village",
                            village.getId(),
                            Map.of("x", body.x(), "y", body.y()));
                    return VillageRefDto.of(village);
                });
    }

    /** The newest admin actions, newest first (08 §6). */
    @GetMapping("/audit")
    List<AdminActionDto> audit(
            @RequestParam(defaultValue = "50") @Min(1) @Max(AdminAuditService.MAX_LIMIT) int limit) {
        return audit.newest(limit);
    }
}
