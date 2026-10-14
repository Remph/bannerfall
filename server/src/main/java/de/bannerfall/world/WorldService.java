package de.bannerfall.world;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.npc.NpcBalanceSettings;
import de.bannerfall.npc.NpcDifficulty;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The world settings, loaded once from the {@code world_settings} row and cached. Changing the row
 * (admin, M14) must be followed by {@link #reload()}.
 */
@Service
public class WorldService {

    private static final short ROW_ID = 1;

    private final WorldSettingsRepository repository;

    /** Loaded on first use; replaced as a whole on reload, so readers never see half an update. */
    private volatile Snapshot snapshot;

    WorldService(WorldSettingsRepository repository) {
        this.repository = repository;
    }

    public WorldSettings settings() {
        return snapshot().settings();
    }

    /** When the world was created. */
    public Instant startedAt() {
        return snapshot().startedAt();
    }

    /**
     * When the world's content was created, i.e. when it opened (M29: barbarian walls count their
     * delay from here); null in the pre-game.
     */
    public Instant initializedAt() {
        return snapshot().initializedAt();
    }

    /** {@link WorldPhase#PREGAME} until the world opens (M14g); cached like the settings. */
    public WorldPhase phase() {
        return snapshot().phase();
    }

    /** When the pre-game ends, null while the host has not set a time or once the world is open. */
    public Instant opensAt() {
        return snapshot().opensAt();
    }

    /** Reads the row again. Call after changing it. */
    public void reload() {
        snapshot = load();
    }

    /**
     * Locks the settings row until the end of the current transaction. Everything that places new
     * villages on the map calls this first, so two spawns at the same moment can't pick the same or
     * neighbouring fields (the spacing rule is checked against the villages that exist).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockMapForPlacement() {
        repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
    }

    /**
     * For what only makes sense on the map (spawning a lord or a barbarian village by hand).
     *
     * @throws ApiException {@code 409 WORLD_NOT_OPEN} in the pre-game
     */
    public void requireOpen() {
        if (phase() == WorldPhase.PREGAME) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_OPEN,
                    "The world has not opened yet; there is no map");
        }
    }

    /**
     * The phase as stored, read under the row lock: a registration uses it to wait for an opening
     * that is just creating the world's content, so a player is never left without a village
     * between the last placement and the commit (M14g).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public WorldPhase lockedPhase() {
        return phaseOf(repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow));
    }

    /**
     * Claims the one-time creation of the world's content (initial barbarians; NPC lords from M12):
     * locks the settings row and marks the world initialized, unless it already is. Only the first
     * caller ever gets {@code true}; if its transaction rolls back, the mark goes with it and the
     * next start tries again. The mark also ends the pre-game (M14g).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claimInitialization(Instant now) {
        WorldSettingsEntity row =
                repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
        if (row.getInitializedAt() != null) {
            return false;
        }
        row.markInitialized(now);
        reloadAfterCommit();
        return true;
    }

    /**
     * Puts a world whose content does not exist yet into the pre-game (first start of a fresh
     * production server, M14g). Does nothing for a world that is open already.
     *
     * @return whether the world is in the pre-game now
     */
    @Transactional
    public boolean enterPregame() {
        WorldSettingsEntity row =
                repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
        if (row.getInitializedAt() != null) {
            return false;
        }
        if (!row.isPregame()) {
            row.setPregame(true);
            reloadAfterCommit();
        }
        return true;
    }

    /**
     * Sets or clears (null) the time the pre-game ends. Only the row changes here; {@code
     * WorldOpeningService} keeps the WORLD_OPEN event in step.
     *
     * @throws ApiException {@code 409 WORLD_ALREADY_OPEN} once the world is open
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setOpensAt(Instant opensAt) {
        WorldSettingsEntity row = lockedPregameRow();
        row.setOpensAt(opensAt);
        reloadAfterCommit();
    }

    private WorldSettingsEntity lockedPregameRow() {
        WorldSettingsEntity row =
                repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
        if (phaseOf(row) != WorldPhase.PREGAME) {
            throw alreadyOpen();
        }
        return row;
    }

    private static ApiException alreadyOpen() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.WORLD_ALREADY_OPEN,
                "The world is open already; this only works before it opens");
    }

    private static WorldPhase phaseOf(WorldSettingsEntity row) {
        return row.getInitializedAt() == null && row.isPregame()
                ? WorldPhase.PREGAME
                : WorldPhase.OPEN;
    }

    private void reloadAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        reload();
                    }
                });
    }

    /**
     * Changes the NPC difficulty and, when given, the CUSTOM difficulty's values (admin, M12d), and
     * reloads the cached settings once the transaction has committed. {@code custom == null} keeps
     * the stored values.
     */
    @Transactional
    public void updateNpcSettings(NpcDifficulty difficulty, NpcBalanceSettings.Difficulty custom) {
        WorldSettingsEntity row =
                repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
        row.setNpcDifficulty(difficulty);
        if (custom != null) {
            row.setNpcCustom(custom);
        }
        reloadAfterCommit();
    }

    /**
     * Changes the settings the admin may change (M14a) and reloads the cached settings once the
     * transaction has committed. Everything that reads the settings uses the new values from the
     * next call on; running events keep their times.
     *
     * @return the settings as stored
     * @throws ApiException {@code 400 VALIDATION} if {@code buildQueueMax < buildQueueSize}
     */
    @Transactional
    public AdminWorldSettingsDto updateSettings(WorldSettingsUpdateRequest update) {
        if (update.buildQueueMax() < update.buildQueueSize()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "buildQueueMax must not be below buildQueueSize");
        }
        WorldSettingsEntity row =
                repository.findWithLockById(ROW_ID).orElseThrow(WorldService::missingRow);
        // Map size, barbarians and lords are fixed once the content exists (M14g): the same value
        // is fine (a client sends the whole form), a different one is refused.
        if (phaseOf(row) != WorldPhase.PREGAME
                && (changes(update.mapSize(), row.toSettings().mapSize())
                        || changes(
                                update.barbarianInitialCount(),
                                row.toSettings().barbarianInitialCount())
                        || changes(update.npcLordCount(), row.toSettings().npcLordCount()))) {
            throw alreadyOpen();
        }
        row.apply(update);
        if (phaseOf(row) == WorldPhase.PREGAME) {
            row.applySetup(update.mapSize(), update.barbarianInitialCount(), update.npcLordCount());
        }
        reloadAfterCommit();
        return adminDto(row);
    }

    /** All settings for the admin page. */
    @Transactional(readOnly = true)
    public AdminWorldSettingsDto adminSettings() {
        return adminDto(repository.findById(ROW_ID).orElseThrow(WorldService::missingRow));
    }

    private static AdminWorldSettingsDto adminDto(WorldSettingsEntity row) {
        return AdminWorldSettingsDto.of(row.toSettings(), phaseOf(row), row.getOpensAt());
    }

    private static boolean changes(Integer given, int stored) {
        return given != null && given != stored;
    }

    /** The CUSTOM difficulty's stored values, null until the admin first picked CUSTOM. */
    @Transactional(readOnly = true)
    public NpcBalanceSettings.Difficulty storedNpcCustom() {
        return repository.findById(ROW_ID).orElseThrow(WorldService::missingRow).getNpcCustom();
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            // Two threads may both load here; harmless, they read the same row.
            current = load();
            snapshot = current;
        }
        return current;
    }

    private Snapshot load() {
        WorldSettingsEntity row = repository.findById(ROW_ID).orElseThrow(WorldService::missingRow);
        return new Snapshot(
                row.toSettings(),
                row.getStartedAt(),
                row.getInitializedAt(),
                phaseOf(row),
                phaseOf(row) == WorldPhase.PREGAME ? row.getOpensAt() : null);
    }

    private static IllegalStateException missingRow() {
        return new IllegalStateException("world_settings row is missing (seeded by migration V2)");
    }

    private record Snapshot(
            WorldSettings settings,
            Instant startedAt,
            Instant initializedAt,
            WorldPhase phase,
            Instant opensAt) {}
}
