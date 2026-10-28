package de.bannerfall.guild;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.guild.EnemiesDto.EnemyGuildDto;
import de.bannerfall.guild.EnemiesDto.EnemyPlayerDto;
import de.bannerfall.guild.EnemiesDto.EnemyVillageDto;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The enemies of a guild (1.7, 02-game-rules §16.3): the leader marks guilds, players (also NPC
 * lords) and single villages, every member reads the list. A mark changes no rule; it only shows.
 * Plain SQL ({@code guild_enemy}); a mark counts while its target exists and is not a guild mate,
 * so the list is read through joins and nothing has to clean up after a delete, a conquest or a
 * player joining the guild.
 */
@Service
public class GuildEnemyService {

    /** How many marks a guild may have. */
    static final int ENEMY_MAX = 30;

    private final JdbcClient jdbc;
    private final GuildRepository guilds;
    private final GuildMemberRepository members;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    GuildEnemyService(
            JdbcClient jdbc,
            GuildRepository guilds,
            GuildMemberRepository members,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.guilds = guilds;
        this.members = members;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The enemies of the player's guild.
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}
     */
    @Transactional(readOnly = true)
    public EnemiesDto list(long playerId) {
        return listOf(guildIdOf(playerId));
    }

    /**
     * Marks a guild, player or village as an enemy (leader). Marking it again changes nothing.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code NOT_IN_GUILD}, {@code
     *     ENEMY_NOT_ALLOWED} (the own guild, a guild mate, yourself, a barbarian village), {@code
     *     ENEMY_LIMIT}
     * @throws ApiException 403 {@code NOT_GUILD_LEADER}
     * @throws NotFoundException {@code GUILD_NOT_FOUND}, {@code PLAYER_NOT_FOUND}, {@code
     *     VILLAGE_NOT_FOUND}
     */
    @Transactional
    public EnemiesDto add(long leaderId, EnemyKind kind, long targetId) {
        if (!worldService.settings().guildsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched guilds on");
        }
        long guildId = lockLeaderGuild(leaderId);
        requireTarget(guildId, kind, targetId);
        if (!isMarked(guildId, kind, targetId) && listOf(guildId).size() >= ENEMY_MAX) {
            throw new GameRuleException(
                    ErrorCode.ENEMY_LIMIT, "A guild can mark " + ENEMY_MAX + " enemies at most");
        }
        jdbc.sql(
                        """
                        insert into guild_enemy (guild_id, kind, target_id, created_at)
                        values (:guild, :kind, :target, :now)
                        on conflict do nothing
                        """)
                .param("guild", guildId)
                .param("kind", kind.name())
                .param("target", targetId)
                .param("now", java.sql.Timestamp.from(clock.instant()))
                .update();
        notifyMembers(guildId);
        return listOf(guildId);
    }

    /** Takes a mark away (leader); a mark that isn't there is fine. */
    @Transactional
    public EnemiesDto remove(long leaderId, EnemyKind kind, long targetId) {
        long guildId = lockLeaderGuild(leaderId);
        jdbc.sql(
                        "delete from guild_enemy where guild_id = :guild and kind = :kind and"
                                + " target_id = :target")
                .param("guild", guildId)
                .param("kind", kind.name())
                .param("target", targetId)
                .update();
        notifyMembers(guildId);
        return listOf(guildId);
    }

    // ---- helpers ----

    private long guildIdOf(long playerId) {
        return members.findGuildIdOf(playerId)
                .orElseThrow(() -> new GameRuleException(ErrorCode.NOT_IN_GUILD, "Not in a guild"));
    }

    /** The leader's guild, locked like every change of a guild, so two marks can't pass the cap. */
    private long lockLeaderGuild(long playerId) {
        long guildId = guildIdOf(playerId);
        guilds.findForUpdate(guildId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GUILD_NOT_FOUND));
        boolean leader =
                members.findById(playerId)
                        .filter(m -> m.getGuildId() == guildId && m.getRole() == GuildRole.LEADER)
                        .isPresent();
        if (!leader) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.NOT_GUILD_LEADER,
                    "Only the leader may do that");
        }
        return guildId;
    }

    private void requireTarget(long guildId, EnemyKind kind, long targetId) {
        switch (kind) {
            case GUILD -> {
                if (!guilds.existsById(targetId)) {
                    throw new NotFoundException(ErrorCode.GUILD_NOT_FOUND);
                }
                if (targetId == guildId) {
                    throw notAllowed("Your own guild can't be an enemy");
                }
            }
            case PLAYER -> {
                if (!exists("select exists (select 1 from player where id = :id)", targetId)) {
                    throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
                }
                requireNoMate(guildId, targetId);
            }
            case VILLAGE -> {
                // player_id is null for a barbarian village: read the row, not just the value
                // (an optional of a null column is empty)
                Optional<Optional<Long>> village =
                        jdbc.sql("select player_id from village where id = :id")
                                .param("id", targetId)
                                .query((rs, n) -> Optional.ofNullable((Long) rs.getObject(1)))
                                .optional();
                if (village.isEmpty()) {
                    throw new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND);
                }
                if (village.get().isEmpty()) {
                    throw notAllowed("A barbarian village can't be an enemy");
                }
                long owner = village.get().get();
                requireNoMate(guildId, owner);
            }
        }
    }

    private void requireNoMate(long guildId, long playerId) {
        if (members.findGuildIdOf(playerId).filter(g -> g == guildId).isPresent()) {
            throw notAllowed("A guild mate can't be an enemy");
        }
    }

    private static GameRuleException notAllowed(String message) {
        return new GameRuleException(ErrorCode.ENEMY_NOT_ALLOWED, message);
    }

    private boolean exists(String sql, long id) {
        return jdbc.sql(sql).param("id", id).query(Boolean.class).single();
    }

    private boolean isMarked(long guildId, EnemyKind kind, long targetId) {
        return jdbc.sql(
                        "select exists (select 1 from guild_enemy where guild_id = :guild and kind"
                                + " = :kind and target_id = :target)")
                .param("guild", guildId)
                .param("kind", kind.name())
                .param("target", targetId)
                .query(Boolean.class)
                .single();
    }

    private void notifyMembers(long guildId) {
        for (long id : members.playerIdsOf(guildId)) {
            events.publishEvent(new PlayerNotification(id, new GameNotification.GuildChanged()));
        }
    }

    /** The marks that count, oldest first. */
    private EnemiesDto listOf(long guildId) {
        List<EnemyGuildDto> enemyGuilds =
                jdbc.sql(
                                """
                                select g.id, g.name, g.tag
                                from guild_enemy e join guild g on g.id = e.target_id
                                where e.guild_id = :guild and e.kind = 'GUILD' and g.id <> :guild
                                order by e.created_at, g.id
                                """)
                        .param("guild", guildId)
                        .query(
                                (rs, n) ->
                                        new EnemyGuildDto(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getString("tag")))
                        .list();
        List<EnemyPlayerDto> enemyPlayers =
                jdbc.sql(
                                """
                                select p.id, p.name, p.npc, gm.guild_id, g.tag
                                from guild_enemy e
                                join player p on p.id = e.target_id
                                left join guild_member gm on gm.player_id = p.id
                                left join guild g on g.id = gm.guild_id
                                where e.guild_id = :guild and e.kind = 'PLAYER'
                                  and (gm.guild_id is null or gm.guild_id <> :guild)
                                  and (p.npc or exists (
                                       select 1 from account a
                                       where a.id = p.account_id and not a.disabled))
                                order by e.created_at, p.id
                                """)
                        .param("guild", guildId)
                        .query(
                                (rs, n) ->
                                        new EnemyPlayerDto(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getBoolean("npc"),
                                                (Long) rs.getObject("guild_id"),
                                                rs.getString("tag")))
                        .list();
        List<EnemyVillageDto> enemyVillages =
                jdbc.sql(
                                """
                                select v.id, v.name, v.x, v.y, p.id as owner_id,
                                       p.name as owner_name, p.npc
                                from guild_enemy e
                                join village v on v.id = e.target_id
                                join player p on p.id = v.player_id
                                left join guild_member gm on gm.player_id = p.id
                                where e.guild_id = :guild and e.kind = 'VILLAGE'
                                  and (gm.guild_id is null or gm.guild_id <> :guild)
                                order by e.created_at, v.id
                                """)
                        .param("guild", guildId)
                        .query(
                                (rs, n) ->
                                        new EnemyVillageDto(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getInt("x"),
                                                rs.getInt("y"),
                                                rs.getLong("owner_id"),
                                                rs.getString("owner_name"),
                                                rs.getBoolean("npc")))
                        .list();
        return new EnemiesDto(enemyGuilds, enemyPlayers, enemyVillages, ENEMY_MAX);
    }
}
