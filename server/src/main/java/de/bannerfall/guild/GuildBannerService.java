package de.bannerfall.guild;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.guild.GuildBannerDto.DonorDto;
import de.bannerfall.guild.GuildBannerDto.EffectDto;
import de.bannerfall.guild.GuildBannerDto.Kind;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The guild banner (1.7, 02-game-rules §16.4): members donate resources into a fund, any member
 * raises the level when the fund covers the price (per-member price × the members now), and no
 * level can be raised for 24 hours after a member left or was removed. Plain SQL ({@code
 * guild_banner}, {@code guild_banner_donation}). Every change takes the guild's row lock first,
 * then a village, like no other code does it the other way round. The effects themselves are read
 * by {@link GuildBannerLevels}.
 */
@Service
public class GuildBannerService {

    private final JdbcClient jdbc;
    private final GuildRepository guilds;
    private final GuildMemberRepository members;
    private final WorldService worldService;
    private final GuildBannerCatalog catalog;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    GuildBannerService(
            JdbcClient jdbc,
            GuildRepository guilds,
            GuildMemberRepository members,
            WorldService worldService,
            GuildBannerCatalog catalog,
            VillageLocker locker,
            VillageStateService stateService,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.guilds = guilds;
        this.members = members;
        this.worldService = worldService;
        this.catalog = catalog;
        this.locker = locker;
        this.stateService = stateService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The banner of the player's guild.
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}
     */
    @Transactional(readOnly = true)
    public GuildBannerDto get(long playerId) {
        return dtoOf(guildIdOf(playerId));
    }

    /**
     * Gives resources of one of the player's villages to the guild fund. A donation can't be larger
     * than what the next level still lacks, so nothing is wasted; it isn't paid back.
     *
     * @throws GameRuleException {@code GUILD_BANNERS_DISABLED}, {@code NOT_IN_GUILD}, {@code
     *     BANNER_MAX_LEVEL}, {@code DONATION_TOO_LARGE}, {@code NOT_ENOUGH_RESOURCES}
     * @throws ApiException 400 {@code VALIDATION} for a donation of nothing
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} (not the player's village)
     */
    @Transactional
    public GuildBannerDto donate(long playerId, long villageId, ResourceAmounts gift) {
        requireOn();
        long total = (long) gift.wood() + gift.clay() + gift.iron();
        if (gift.wood() < 0 || gift.clay() < 0 || gift.iron() < 0 || total == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Give at least one resource");
        }
        long guildId = lockGuildOf(playerId);
        Row row = row(guildId);
        if (row.level() >= GuildBannerCatalog.LEVELS) {
            throw new GameRuleException(
                    ErrorCode.BANNER_MAX_LEVEL, "The guild banner has its last level");
        }
        long missing = Math.max(0, price(guildId, row.level()) - row.fund());
        if (total > missing) {
            throw new GameRuleException(
                    ErrorCode.DONATION_TOO_LARGE,
                    "The next level lacks " + missing + " resources, not " + total);
        }
        Village village = locker.lockOwned(villageId, playerId);
        stateService.catchUp(village, clock.instant());
        if (!gift.fitsInto(village.floorResources())) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_RESOURCES, "Not enough resources in the village");
        }
        village.spend(gift);
        jdbc.sql("update guild_banner set fund = fund + :total where guild_id = :guild")
                .param("total", total)
                .param("guild", guildId)
                .update();
        jdbc.sql(
                        """
                        insert into guild_banner_donation (guild_id, player_id, amount)
                        values (:guild, :player, :total)
                        on conflict (guild_id, player_id)
                        do update set amount = guild_banner_donation.amount + :total
                        """)
                .param("guild", guildId)
                .param("player", playerId)
                .param("total", total)
                .update();
        notifyMembers(guildId, null);
        return dtoOf(guildId);
    }

    /**
     * Raises the banner by one level, paying the price from the fund (any member). The price is the
     * one of this moment.
     *
     * @throws GameRuleException {@code GUILD_BANNERS_DISABLED}, {@code NOT_IN_GUILD}, {@code
     *     BANNER_MAX_LEVEL}, {@code GUILD_BANNER_LOCKED}, {@code BANNER_FUND_LOW}
     */
    @Transactional
    public GuildBannerDto raise(long playerId) {
        requireOn();
        long guildId = lockGuildOf(playerId);
        Row row = row(guildId);
        Instant now = clock.instant();
        if (row.level() >= GuildBannerCatalog.LEVELS) {
            throw new GameRuleException(
                    ErrorCode.BANNER_MAX_LEVEL, "The guild banner has its last level");
        }
        if (row.lockedUntil() != null && row.lockedUntil().isAfter(now)) {
            throw new GameRuleException(
                    ErrorCode.GUILD_BANNER_LOCKED,
                    "No level can be raised before " + row.lockedUntil() + ": a member left");
        }
        long price = price(guildId, row.level());
        if (row.fund() < price) {
            throw new GameRuleException(
                    ErrorCode.BANNER_FUND_LOW, "The fund holds " + row.fund() + " of " + price);
        }
        if (row.level() == 0) {
            // level 1 raises production: bring the members' villages up to date at the old rate
            catchUpVillagesOf(guildId, now);
        }
        int level = row.level() + 1;
        jdbc.sql("update guild_banner set level = :level, fund = fund - :price where guild_id = :g")
                .param("level", level)
                .param("price", price)
                .param("g", guildId)
                .update();
        notifyMembers(guildId, level);
        return dtoOf(guildId);
    }

    /**
     * A member has left, was kicked or was removed: no level can be raised for the lock time (the
     * guild keeps existing; the caller doesn't call it when the last member goes). Makes the row if
     * the guild has none yet.
     */
    void memberLeft(long guildId) {
        Instant until = clock.instant().plus(Duration.ofHours(catalog.lockHours()));
        jdbc.sql(
                        """
                        insert into guild_banner (guild_id, locked_until) values (:guild, :until)
                        on conflict (guild_id) do update set locked_until = :until
                        """)
                .param("guild", guildId)
                .param("until", Timestamp.from(until))
                .update();
    }

    /** The level the public pages show: 0 while the world has guild banners off. */
    int levelOf(long guildId) {
        if (!GuildBannerLevels.on(worldService.settings())) {
            return 0;
        }
        return jdbc.sql("select level from guild_banner where guild_id = :guild")
                .param("guild", guildId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    // ---- helpers ----

    private void requireOn() {
        if (!GuildBannerLevels.on(worldService.settings())) {
            throw new GameRuleException(
                    ErrorCode.GUILD_BANNERS_DISABLED, "The host has not switched guild banners on");
        }
    }

    private long guildIdOf(long playerId) {
        return members.findGuildIdOf(playerId)
                .orElseThrow(() -> new GameRuleException(ErrorCode.NOT_IN_GUILD, "Not in a guild"));
    }

    /**
     * The player's guild, locked like every change of a guild; makes the banner row if needed. The
     * membership is read again under the lock: a member who left or was kicked while this request
     * waited for it is no member any more.
     */
    private long lockGuildOf(long playerId) {
        long guildId = guildIdOf(playerId);
        guilds.findForUpdate(guildId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GUILD_NOT_FOUND));
        if (guildIdOf(playerId) != guildId) {
            throw new GameRuleException(ErrorCode.NOT_IN_GUILD, "Not in a guild");
        }
        jdbc.sql("insert into guild_banner (guild_id) values (:guild) on conflict do nothing")
                .param("guild", guildId)
                .update();
        return guildId;
    }

    private record Row(int level, long fund, Instant lockedUntil) {}

    private Row row(long guildId) {
        return jdbc.sql("select level, fund, locked_until from guild_banner where guild_id = :g")
                .param("g", guildId)
                .query(
                        (rs, n) -> {
                            Timestamp until = rs.getTimestamp("locked_until");
                            return new Row(
                                    rs.getInt("level"),
                                    rs.getLong("fund"),
                                    until == null ? null : until.toInstant());
                        })
                .optional()
                .orElse(new Row(0, 0, null));
    }

    /** The price of the level after {@code level} for the guild as it is now. */
    private long price(long guildId, int level) {
        return catalog.price(level + 1, members.countByGuildId(guildId));
    }

    private void catchUpVillagesOf(long guildId, Instant now) {
        List<Long> ids =
                jdbc.sql(
                                """
                                select v.id from village v
                                join guild_member m on m.player_id = v.player_id
                                where m.guild_id = :guild
                                """)
                        .param("guild", guildId)
                        .query(Long.class)
                        .list();
        for (Village village : locker.lock(ids.toArray(Long[]::new))) {
            stateService.catchUp(village, now);
        }
    }

    /** Every member reloads; a raised level (not null) also gets a toast. */
    private void notifyMembers(long guildId, Integer raisedTo) {
        for (long id : members.playerIdsOf(guildId)) {
            events.publishEvent(new PlayerNotification(id, new GameNotification.GuildChanged()));
            if (raisedTo != null) {
                events.publishEvent(
                        new PlayerNotification(
                                id, new GameNotification.GuildBannerRaised(raisedTo)));
            }
        }
    }

    private GuildBannerDto dtoOf(long guildId) {
        Row row = row(guildId);
        int membersNow = (int) members.countByGuildId(guildId);
        boolean top = row.level() >= GuildBannerCatalog.LEVELS;
        long price = top ? 0 : price(guildId, row.level());
        Instant now = clock.instant();
        List<EffectDto> effects =
                List.of(
                        new EffectDto(1, Kind.PRODUCTION, catalog.production()),
                        new EffectDto(2, Kind.SUPPORT, catalog.supportHaste()),
                        new EffectDto(3, Kind.DEFENCE, catalog.defence()));
        List<DonorDto> donors =
                jdbc.sql(
                                """
                                select p.id, p.name, d.amount
                                from guild_banner_donation d
                                join guild_member m on m.player_id = d.player_id
                                         and m.guild_id = d.guild_id
                                join player p on p.id = d.player_id
                                where d.guild_id = :guild
                                order by d.amount desc, p.name
                                """)
                        .param("guild", guildId)
                        .query(
                                (rs, n) ->
                                        new DonorDto(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getLong("amount")))
                        .list();
        return new GuildBannerDto(
                GuildBannerLevels.on(worldService.settings()),
                row.level(),
                GuildBannerCatalog.LEVELS,
                row.fund(),
                price,
                top ? 0 : Math.max(0, price - row.fund()),
                membersNow,
                catalog.memberPrices(),
                row.lockedUntil() != null && row.lockedUntil().isAfter(now)
                        ? row.lockedUntil()
                        : null,
                effects,
                donors);
    }
}
