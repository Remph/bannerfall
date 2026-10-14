package de.bannerfall.npc;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the admin does with NPC lords (04-api *Admin*, M12d): the list, spawning one more, the
 * difficulty and the CUSTOM difficulty's values.
 */
@Service
public class NpcAdminService {

    private final PlayerRepository players;
    private final NpcBrainRepository brains;
    private final NpcLordService lordService;
    private final NpcBalanceSettings settings;
    private final WorldService worldService;
    private final JdbcClient jdbc;

    NpcAdminService(
            PlayerRepository players,
            NpcBrainRepository brains,
            NpcLordService lordService,
            NpcBalanceSettings settings,
            WorldService worldService,
            JdbcClient jdbc) {
        this.players = players;
        this.brains = brains;
        this.lordService = lordService;
        this.settings = settings;
        this.worldService = worldService;
        this.jdbc = jdbc;
    }

    /** All lords, newest first. */
    @Transactional(readOnly = true)
    public List<NpcLordDto> lords() {
        Map<Long, Instant> next = new HashMap<>();
        jdbc.sql(
                        "select ref_id, min(due_at) as due from game_event where kind = 'NPC_THINK'"
                                + " and status = 'PENDING' group by ref_id")
                .query(
                        (rs, row) -> {
                            next.put(rs.getLong("ref_id"), rs.getTimestamp("due").toInstant());
                            return null;
                        })
                .list();
        Map<Long, NpcBrain> brainById = new HashMap<>();
        brains.findAll().forEach(b -> brainById.put(b.getPlayerId(), b));
        return players.findNpcLords().stream()
                .filter(p -> brainById.containsKey(p.getId()))
                .map(p -> dto(p, brainById.get(p.getId()), next.get(p.getId())))
                .toList();
    }

    /**
     * Spawns a lord like at world start (09 §2) and returns its row.
     *
     * @throws GameRuleException {@code WORLD_FULL} if the map has no room
     * @throws de.bannerfall.common.ApiException {@code WORLD_NOT_OPEN} in the pre-game
     */
    @Transactional
    public NpcLordDto spawn(NpcPersonality personality) {
        worldService.requireOpen();
        Player lord =
                lordService
                        .spawn(personality)
                        .orElseThrow(
                                () ->
                                        new GameRuleException(
                                                ErrorCode.WORLD_FULL,
                                                "No free spot for another NPC lord"));
        NpcBrain brain = brains.findById(lord.getId()).orElseThrow();
        Instant next =
                jdbc.sql(
                                "select min(due_at) from game_event where kind = 'NPC_THINK' and"
                                        + " status = 'PENDING' and ref_id = ?")
                        .param(lord.getId())
                        .query(java.sql.Timestamp.class)
                        .optional()
                        .map(java.sql.Timestamp::toInstant)
                        .orElse(null);
        return dto(lord, brain, next);
    }

    public NpcDifficulty difficulty() {
        return worldService.settings().npcDifficulty();
    }

    /**
     * Sets the difficulty. CUSTOM with no stored values yet stores a copy of NORMAL (09 §8). Takes
     * effect at each lord's next round.
     */
    public void setDifficulty(NpcDifficulty difficulty) {
        Difficulty custom = null;
        if (difficulty == NpcDifficulty.CUSTOM && worldService.storedNpcCustom() == null) {
            custom = settings.balance().difficulty(NpcDifficulty.NORMAL, null);
        }
        worldService.updateNpcSettings(difficulty, custom);
    }

    /** The CUSTOM values; a copy of NORMAL while none is stored. */
    public Difficulty custom() {
        Difficulty stored = worldService.storedNpcCustom();
        return stored != null ? stored : settings.balance().difficulty(NpcDifficulty.NORMAL, null);
    }

    /**
     * Stores the CUSTOM values.
     *
     * @throws ApiException {@code 400 VALIDATION} listing what is out of range (09 §8)
     */
    public Difficulty setCustom(Difficulty custom) {
        if (custom == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "No values");
        }
        List<String> problems = custom.problems("custom");
        if (!problems.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, String.join("; ", problems));
        }
        worldService.updateNpcSettings(difficulty(), custom);
        return custom;
    }

    private NpcLordDto dto(Player lord, NpcBrain brain, Instant nextThinkAt) {
        NpcMemory.Stance stance = brain.getMemory().stance();
        return new NpcLordDto(
                lord.getId(),
                lord.getName(),
                brain.getPersonality(),
                brain.getTemplate(),
                lord.getPoints(),
                lord.getVillageCount(),
                brain.getLastThinkAt(),
                nextThinkAt,
                stance.braked(),
                stance.boosted(),
                lord.getProductionFactor());
    }
}
