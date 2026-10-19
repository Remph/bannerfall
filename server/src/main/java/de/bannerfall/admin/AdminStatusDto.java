package de.bannerfall.admin;

import de.bannerfall.abuse.InvariantReportDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * {@code GET /api/admin/status} (08-host-console §2): what the host looks at first.
 *
 * @param version the app version from the build ({@code unknown} without build info)
 * @param uptimeSeconds since this server process started
 * @param databaseOk the database answered a {@code select 1}; when it didn't, the counts are 0
 * @param humanPlayers human players that aren't deleted
 * @param barbarianVillages villages without an owner
 * @param npcLords NPC lords that still own a village
 * @param events the event queue
 * @param cheatsEnabled {@code bannerfall.cheats.enabled}: the host cheats exist (08 §3)
 * @param abuse the abuse protection (M14e)
 */
public record AdminStatusDto(
        String version,
        Instant serverTime,
        long uptimeSeconds,
        boolean databaseOk,
        long humanPlayers,
        long villages,
        long barbarianVillages,
        long npcLords,
        EventQueueDto events,
        boolean cheatsEnabled,
        AbuseStatusDto abuse,
        PushStatusDto push) {

    /**
     * Push notifications (1.5).
     *
     * @param enabled whether the world has push keys
     * @param devices devices that get push
     * @param players players with at least one of them
     */
    public record PushStatusDto(boolean enabled, long devices, long players) {}

    /**
     * @param bannedPlayers accounts banned right now
     * @param suspiciousAccounts accounts with suspicious requests in the last 24 hours
     * @param autoLocks automatic locks in the last 24 hours
     * @param invariants the last invariant check since the server started; null before the first
     */
    public record AbuseStatusDto(
            long bannedPlayers,
            long suspiciousAccounts,
            long autoLocks,
            @Schema(nullable = true) InvariantReportDto invariants) {}

    /**
     * @param pending events waiting for the worker
     * @param nextDueAt the earliest pending event; null when none is pending
     * @param lagSeconds how long the oldest overdue pending event has been waiting (0 = the worker
     *     keeps up)
     * @param failed events the worker gave up on (list: {@code GET /api/admin/events/failed})
     */
    public record EventQueueDto(
            long pending,
            @Schema(nullable = true) Instant nextDueAt,
            long lagSeconds,
            long failed) {}
}
