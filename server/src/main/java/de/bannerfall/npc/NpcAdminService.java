package de.bannerfall.npc;

import de.bannerfall.banner.Banner;
import de.bannerfall.banner.BannerRepository;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildRefDto;
import de.bannerfall.guild.GuildRepository;
import de.bannerfall.guild.GuildService;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.research.UnitLevelService;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the admin does with NPC lords (04-api *Admin*, M12d): the list, spawning one more, the
 * difficulty and the CUSTOM difficulty's values, and placing lords in lord guilds (09 §13.7).
 */
@Service
public class NpcAdminService {

    private final PlayerRepository players;
    private final NpcBrainRepository brains;
    private final NpcLordService lordService;
    private final NpcBalanceSettings settings;
    private final WorldService worldService;
    private final JdbcClient jdbc;
    private final GuildService guildService;
    private final GuildRepository guilds;
    private final NpcGuilds npcGuilds;
    private final BannerRepository banners;
    private final VillageRepository villages;
    private final UnitLevelService unitLevels;
    private final NpcLessons lessons;
    private final NpcTemplates templates;
    private final Clock clock;

    NpcAdminService(
            PlayerRepository players,
            NpcBrainRepository brains,
            NpcLordService lordService,
            NpcBalanceSettings settings,
            WorldService worldService,
            JdbcClient jdbc,
            GuildService guildService,
            GuildRepository guilds,
            NpcGuilds npcGuilds,
            BannerRepository banners,
            VillageRepository villages,
            UnitLevelService unitLevels,
            NpcLessons lessons,
            NpcTemplates templates,
            Clock clock) {
        this.players = players;
        this.brains = brains;
        this.lordService = lordService;
        this.settings = settings;
        this.worldService = worldService;
        this.jdbc = jdbc;
        this.guildService = guildService;
        this.guilds = guilds;
        this.npcGuilds = npcGuilds;
        this.banners = banners;
        this.villages = villages;
        this.unitLevels = unitLevels;
        this.lessons = lessons;
        this.templates = templates;
        this.clock = clock;
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
        Instant now = clock.instant();
        NpcLearning.Wins wins =
                worldService.settings().npcLearningEnabled() ? lessons.wins(now) : null;
        return players.findNpcLords().stream()
                .filter(p -> brainById.containsKey(p.getId()))
                .map(p -> dto(p, brainById.get(p.getId()), next.get(p.getId()), wins))
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
        return dto(lord, brain, nextThinkAt(lord.getId()));
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

    /**
     * Places a lord (09 §13.7): into another lord guild, out of its guild, into a new lord guild of
     * its own, or back to choosing on its own. A guild left empty ends.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED} while lord guilds are off, {@code
     *     GUILD_FULL}
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for no NPC lord, {@code GUILD_NOT_FOUND}
     *     for no guild of lords
     * @throws ApiException 400 {@code VALIDATION} for JOIN without a guild
     */
    @Transactional
    public NpcLordDto setGuild(long lordId, NpcGuildRequest request) {
        if (!npcGuilds.active()) {
            throw new GameRuleException(ErrorCode.GUILDS_DISABLED, "Lord guilds are off");
        }
        NpcBrain brain =
                brains.findById(lordId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        Long current = guilds.findRefOf(lordId).map(GuildRefDto::id).orElse(null);
        switch (request.action()) {
            case JOIN -> {
                Long target = request.guildId();
                if (target == null) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "JOIN needs a guildId");
                }
                if (!guildService.isLordGuild(target)) {
                    throw new NotFoundException(ErrorCode.GUILD_NOT_FOUND);
                }
                if (!target.equals(current)) {
                    guildService.removeLord(lordId);
                    guildService.addLord(target, lordId);
                }
                brain.setGuildByAdmin(true);
            }
            case LEAVE -> {
                guildService.removeLord(lordId);
                brain.setGuildByAdmin(true);
            }
            case FOUND -> {
                guildService.removeLord(lordId);
                NpcGuildForming.Name name = npcGuilds.nextName();
                guildService.foundForLords(lordId, lordId, name.name(), name.tag());
                brain.setGuildByAdmin(true);
            }
            case AUTO -> brain.setGuildByAdmin(false);
        }
        brains.save(brain);
        Player lord = players.findById(lordId).orElseThrow();
        return dto(lord, brain, nextThinkAt(lordId));
    }

    /**
     * Ends every lord guild and clears the admin's placements: the host switched lord guilds off
     * (09 §13). Returns how many guilds ended.
     */
    @Transactional
    public int disbandLordGuilds() {
        int ended = guildService.disbandLordGuilds();
        jdbc.sql("update npc_brain set guild_by_admin = false where guild_by_admin").update();
        return ended;
    }

    private Instant nextThinkAt(long lordId) {
        return jdbc.sql(
                        "select min(due_at) from game_event where kind = 'NPC_THINK' and"
                                + " status = 'PENDING' and ref_id = ?")
                .param(lordId)
                .query(java.sql.Timestamp.class)
                .optional()
                .map(java.sql.Timestamp::toInstant)
                .orElse(null);
    }

    private NpcLordDto dto(Player lord, NpcBrain brain, Instant nextThinkAt) {
        NpcLearning.Wins wins =
                worldService.settings().npcLearningEnabled() ? lessons.wins(clock.instant()) : null;
        return dto(lord, brain, nextThinkAt, wins);
    }

    /**
     * @param wins the human wins of the world (09 §15.2), read once for the whole list; null while
     *     learning is off
     */
    private NpcLordDto dto(
            Player lord, NpcBrain brain, Instant nextThinkAt, NpcLearning.Wins wins) {
        NpcMemory.Stance stance = brain.getMemory().stance();
        int paladinLevel = unitLevels.levelsOf(lord, clock.instant()).get(UnitType.PALADIN);
        GuildRefDto guild = guilds.findRefOf(lord.getId()).orElse(null);
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
                lord.getProductionFactor(),
                guild == null ? null : guild.id(),
                guild == null ? null : guild.name(),
                guild == null ? null : guild.tag(),
                brain.isGuildByAdmin(),
                paladinLevel,
                bannerVillages(lord, paladinLevel),
                stash(lord.getId()),
                wins == null ? null : learning(lord, brain, wins));
    }

    /** What the lord learned (09 §15.5); null if nothing (no lesson, no wall goal). */
    private NpcLordDto.Learning learning(Player lord, NpcBrain brain, NpcLearning.Wins wins) {
        Instant now = clock.instant();
        NpcLearning.Lesson lesson = lessons.of(brain, npcGuilds.mates(lord.getId()), wins, now);
        List<Village> own = villages.findByPlayerIdOrderByIdAsc(lord.getId());
        List<NpcLordDto.WallGoal> walls = new ArrayList<>();
        for (Village village : own) {
            Integer goal = NpcLearning.wallGoal(brain.getMemory().lessons(), village.getId(), now);
            if (goal != null) {
                walls.add(
                        new NpcLordDto.WallGoal(
                                village.getId(),
                                village.getName(),
                                goal,
                                brain.getMemory().lessons().walls().get(village.getId()).until()));
            }
        }
        if (!lesson.active() && walls.isEmpty()) {
            return null;
        }
        List<NpcLordDto.MixChange> changes = List.of();
        if (lesson.active() && !own.isEmpty()) {
            Village biggest = own.stream().max(Comparator.comparingInt(Village::getPoints)).get();
            NpcTemplates.Template template = templates.of(brain.getPersonality());
            Map<UnitType, Integer> mix = template.mixOf(template.stageFor(biggest.getPoints()));
            changes = NpcLearning.changes(mix, NpcLearning.learnedMix(mix, lesson));
        }
        return new NpcLordDto.Learning(
                lesson.defenceWeight(), lesson.attackWeight(), changes, walls);
    }

    /** The lord's villages with something equipped (09 §14.8). */
    private List<NpcLordDto.BannerVillage> bannerVillages(Player lord, int paladinLevel) {
        Map<Long, List<Banner>> equipped = new HashMap<>();
        for (Banner banner : banners.findByOwnerIdOrderByIdAsc(lord.getId())) {
            if (banner.isEquipped()) {
                equipped.computeIfAbsent(banner.getEquippedVillageId(), id -> new ArrayList<>())
                        .add(banner);
            }
        }
        if (equipped.isEmpty()) {
            return List.of();
        }
        boolean works = worldService.settings().lordBanners();
        List<NpcLordDto.BannerVillage> rows = new ArrayList<>();
        for (Village village : villages.findByPlayerIdOrderByIdAsc(lord.getId())) {
            List<Banner> here = equipped.get(village.getId());
            if (here == null) {
                continue;
            }
            int hall = village.getBuildings().get(BuildingType.BANNER_HALL);
            NpcLordDto.Equipped flying = null;
            NpcLordDto.Equipped standard = null;
            for (Banner banner : here) {
                int effective =
                        !works
                                ? 0
                                : banner.isStandard()
                                        ? BannerRules.standardTier(
                                                banner.getTier(), paladinLevel, hall)
                                        : BannerRules.effectiveTier(banner.getTier(), hall);
                var row = new NpcLordDto.Equipped(banner.getType(), banner.getTier(), effective);
                if (banner.isStandard()) {
                    standard = row;
                } else {
                    flying = row;
                }
            }
            rows.add(
                    new NpcLordDto.BannerVillage(
                            village.getId(), village.getName(), hall, flying, standard));
        }
        return rows;
    }

    /** The stash counted by type and tier: banners before standards, then by tier and type. */
    private List<NpcLordDto.StashCount> stash(long lordId) {
        Map<String, NpcLordDto.StashCount> counts = new TreeMap<>();
        for (Banner banner : banners.findByOwnerIdOrderByIdAsc(lordId)) {
            if (banner.isEquipped()) {
                continue;
            }
            String key = banner.getType() + ":" + banner.getTier();
            NpcLordDto.StashCount before = counts.get(key);
            counts.put(
                    key,
                    new NpcLordDto.StashCount(
                            banner.getType(),
                            banner.getTier(),
                            before == null ? 1 : before.count() + 1));
        }
        return counts.values().stream()
                .sorted(
                        Comparator.comparing((NpcLordDto.StashCount c) -> c.type().isStandard())
                                .thenComparingInt(NpcLordDto.StashCount::tier)
                                .thenComparing(NpcLordDto.StashCount::type))
                .toList();
    }
}
