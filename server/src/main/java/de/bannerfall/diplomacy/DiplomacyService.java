package de.bannerfall.diplomacy;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.guild.GuildRefDto;
import de.bannerfall.guild.GuildService;
import de.bannerfall.npc.LordVariantDto;
import de.bannerfall.npc.NpcBrain;
import de.bannerfall.npc.NpcBrainRepository;
import de.bannerfall.npc.NpcContacts;
import de.bannerfall.npc.NpcTrust;
import de.bannerfall.npc.NpcTrustRules;
import de.bannerfall.victory.Court;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The diplomacy page (2.1 S2b, 12-solo-and-allies §7): the court and its race against the human's
 * side, his allies and the other lords with their trust in him, and the invitation of a lord into
 * his guild. Shown while guilds and lord guilds are on; invitations only in a solo world.
 */
@Service
public class DiplomacyService {

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final GuildService guildService;
    private final NpcTrust trust;
    private final NpcBrainRepository brains;
    private final NpcContacts contacts;
    private final Court court;
    private final OrderService orders;
    private final Clock clock;

    DiplomacyService(
            JdbcClient jdbc,
            WorldService worldService,
            GuildService guildService,
            NpcTrust trust,
            NpcBrainRepository brains,
            NpcContacts contacts,
            Court court,
            OrderService orders,
            Clock clock) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.guildService = guildService;
        this.trust = trust;
        this.brains = brains;
        this.contacts = contacts;
        this.court = court;
        this.orders = orders;
        this.clock = clock;
    }

    /**
     * What the human sees.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED} while guilds or lord guilds are off
     */
    @Transactional(readOnly = true)
    public DiplomacyDto page(long playerId) {
        requireActive();
        Instant now = worldService.gameTime(clock.instant());
        Me me = me(playerId);
        Court.Race race = court.race(playerId);
        Map<Long, NpcTrust.Row> rows = new HashMap<>();
        trust.rowsOf(playerId).forEach(row -> rows.put(row.lordId(), row));
        Map<Long, Long> invitations = me.guildId() == null ? Map.of() : invitationsOf(me.guildId());
        Map<Long, OrderDto> given = orders.ordersOf(playerId);
        // outside a solo world no lord can join a player: no trust, no bar, no changes (2.1
        // feature review, finding 03); the page keeps the lords and their guilds
        boolean solo = worldService.settings().soloMode();
        List<LordDto> allies = new ArrayList<>();
        List<LordDto> lords = new ArrayList<>();
        for (Lord lord : lords()) {
            NpcTrust.Row row = rows.get(lord.id());
            double value = row == null ? 0 : trust.current(row, now);
            boolean ally = me.guildId() != null && me.guildId().equals(lord.guildId());
            NpcBrain brain = brains.findById(lord.id()).orElse(null);
            // present only once the human has met the lord (09 §16.7): the bar gives its
            // personality away (2.1 security review, finding 1), so it waits for the same contact
            LordVariantDto variant = contacts.variantFor(playerId, lord.id()).orElse(null);
            Integer bar =
                    !solo || ally || brain == null || variant == null
                            ? null
                            : NpcTrustRules.bar(
                                    trust.settings(), brain.getPersonality(), lord.inLordGuild());
            Instant inviteAfter =
                    row == null || row.inviteAfter() == null || !row.inviteAfter().isAfter(now)
                            ? null
                            : row.inviteAfter();
            LordDto dto =
                    new LordDto(
                            lord.id(),
                            lord.name(),
                            lord.guild(),
                            ally,
                            solo ? NpcTrustRules.shown(value) : null,
                            bar,
                            row == null || !solo
                                    ? List.of()
                                    : row.changes().stream()
                                            .map(
                                                    c ->
                                                            new TrustChangeDto(
                                                                    c.reason(),
                                                                    c.delta(),
                                                                    c.village()))
                                            .toList(),
                            lord.villages(),
                            lord.points(),
                            homeOf(lord.id()),
                            invitations.get(lord.id()),
                            inviteAfter,
                            variant,
                            ally ? given.get(lord.id()) : null);
            (ally ? allies : lords).add(dto);
        }
        allies.sort(Comparator.comparingLong(LordDto::points).reversed());
        // outside a solo world the points are the row's only figure: most points first
        lords.sort(
                (solo
                                ? Comparator.comparing(
                                        (LordDto lord) -> lord.trust() == null ? 0 : lord.trust(),
                                        Comparator.reverseOrder())
                                : Comparator.comparingLong(LordDto::points).reversed())
                        .thenComparing(LordDto::name, String.CASE_INSENSITIVE_ORDER));
        return new DiplomacyDto(
                worldService.settings().soloMode(),
                me.leader(),
                me.guildId() != null && me.members() >= worldService.settings().guildMaxMembers(),
                race.court(),
                race.mine(),
                race.allVillages(),
                allies,
                lords);
    }

    /**
     * Invites a lord into the human's guild (12 §5): an ordinary guild invitation, which the lord
     * answers in its next round by letter.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code LORD_NOT_READY} (it said no less
     *     than a day ago), and those of {@link GuildService#inviteLord}
     */
    @Transactional
    public DiplomacyDto invite(long playerId, long lordId) {
        requireActive();
        if (!worldService.settings().soloMode()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_SOLO,
                    "Lords join a player's guild only in a solo world");
        }
        Instant now = worldService.gameTime(clock.instant());
        trust.row(lordId, playerId)
                .map(NpcTrust.Row::inviteAfter)
                .filter(after -> after.isAfter(now))
                .ifPresent(
                        after -> {
                            throw new GameRuleException(
                                    ErrorCode.LORD_NOT_READY,
                                    "The lord said no a short while ago: ask again later");
                        });
        guildService.inviteLord(playerId, lordId);
        return page(playerId);
    }

    private void requireActive() {
        if (!trust.active()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched lord guilds on");
        }
    }

    private record Me(Long guildId, boolean leader, int members) {}

    private Me me(long playerId) {
        return jdbc.sql(
                        """
                        select m.guild_id, m.role = 'LEADER' as leader,
                               (select count(*) from guild_member o where o.guild_id = m.guild_id)
                                   as members
                        from guild_member m where m.player_id = :player
                        """)
                .param("player", playerId)
                .query(
                        (rs, n) ->
                                new Me(
                                        rs.getLong("guild_id"),
                                        rs.getBoolean("leader"),
                                        rs.getInt("members")))
                .optional()
                .orElse(new Me(null, false, 0));
    }

    private record Lord(
            long id,
            String name,
            int villages,
            long points,
            Long guildId,
            GuildRefDto guild,
            boolean inLordGuild) {}

    /** Every lord that owns a village, with its guild. */
    private List<Lord> lords() {
        return jdbc.sql(
                        """
                        select p.id, p.name, p.village_count, p.points, g.id as guild_id, g.name as
                               guild_name, g.tag,
                               g.id is not null and not exists (
                                   select 1 from guild_member h join player hp on hp.id = h.player_id
                                   where h.guild_id = g.id and not hp.npc) as lord_guild
                        from player p
                        left join guild_member m on m.player_id = p.id
                        left join guild g on g.id = m.guild_id
                        where p.npc and p.village_count > 0
                        order by p.id
                        """)
                .query(
                        (rs, n) -> {
                            Long guildId = rs.getObject("guild_id", Long.class);
                            return new Lord(
                                    rs.getLong("id"),
                                    rs.getString("name"),
                                    rs.getInt("village_count"),
                                    rs.getLong("points"),
                                    guildId,
                                    guildId == null
                                            ? null
                                            : new GuildRefDto(
                                                    guildId,
                                                    rs.getString("guild_name"),
                                                    rs.getString("tag")),
                                    rs.getBoolean("lord_guild"));
                        })
                .list();
    }

    /** The guild's open invitations to lords: lord id to invitation id. */
    private Map<Long, Long> invitationsOf(long guildId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.sql("select id, player_id from guild_invitation where guild_id = :guild")
                .param("guild", guildId)
                .query(
                        rs -> {
                            out.put(rs.getLong("player_id"), rs.getLong("id"));
                        });
        return out;
    }

    /** The lord's strongest village. */
    private VillageRefDto homeOf(long lordId) {
        return jdbc.sql(
                        """
                        select v.id, v.name, v.x, v.y, p.id as player_id, p.name as player_name
                        from village v join player p on p.id = v.player_id
                        where v.player_id = :lord order by v.points desc, v.id limit 1
                        """)
                .param("lord", lordId)
                .query(
                        (rs, n) ->
                                new VillageRefDto(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getInt("x"),
                                        rs.getInt("y"),
                                        rs.getLong("player_id"),
                                        rs.getString("player_name")))
                .optional()
                .orElse(null);
    }
}
