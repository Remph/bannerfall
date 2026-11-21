package de.bannerfall.diplomacy;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.npc.NpcOrderKind;
import de.bannerfall.npc.NpcOrderSheet;
import de.bannerfall.npc.NpcOrderState;
import de.bannerfall.npc.NpcOrderStore;
import de.bannerfall.npc.NpcTrust;
import de.bannerfall.npc.NpcTrustRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders to allied lords (2.1 S3, 12-solo-and-allies §8): the order sheet for a target, giving an
 * order to one or more allies (each new order replaces the lord's old one) and taking one back. The
 * lords answer in their next round ({@code NpcOrders}). Only in a solo world.
 */
@Service
public class OrderService {

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final VillageRepository villages;
    private final NpcOrderStore store;
    private final NpcOrderSheet sheet;
    private final NpcTrust trust;
    private final EventScheduler scheduler;
    private final Clock clock;

    OrderService(
            JdbcClient jdbc,
            WorldService worldService,
            VillageRepository villages,
            NpcOrderStore store,
            NpcOrderSheet sheet,
            NpcTrust trust,
            EventScheduler scheduler,
            Clock clock) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.villages = villages;
        this.store = store;
        this.sheet = sheet;
        this.trust = trust;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** An NPC lord of the human's guild. */
    private record Ally(long id, String name) {}

    /**
     * The order sheet for the village {@code villageId}.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED} while guilds or lord guilds are off
     * @throws ApiException {@code WORLD_NOT_SOLO} outside a solo world
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public OrderSheetDto sheet(long playerId, long villageId) {
        requireSolo();
        Village target = village(villageId);
        Instant now = worldService.gameTime(clock.instant());
        WorldSettings world = worldService.settings();
        Long owner = ownerOf(target);
        List<Ally> allies = allies(playerId);
        Map<Long, NpcOrderStore.Order> orders =
                store.ofPlayer(playerId).stream()
                        .collect(
                                Collectors.toMap(NpcOrderStore.Order::lordId, Function.identity()));
        List<OrderAllyDto> rows = new ArrayList<>();
        for (Ally ally : allies) {
            if (owner != null && owner == ally.id()) {
                continue; // an ally isn't ordered about its own village
            }
            NpcOrderSheet.Reach reach = sheet.reach(ally.id(), target, world);
            NpcOrderStore.Order order = orders.get(ally.id());
            rows.add(
                    new OrderAllyDto(
                            ally.id(),
                            ally.name(),
                            NpcTrustRules.shown(trust.trust(ally.id(), playerId, now)),
                            reach == null ? null : reach.fields(),
                            reach == null ? null : reach.attackSeconds(),
                            reach == null ? null : reach.supportSeconds(),
                            reach == null ? null : reach.sendSeconds(),
                            order == null ? null : dto(order)));
        }
        return new OrderSheetDto(
                VillageRefDto.of(target),
                guildTagOf(owner),
                kinds(playerId, owner),
                sheet.ownAttackArrival(playerId, villageId, now),
                rows);
    }

    /**
     * Gives the order to each lord of {@code request.lordIds()}, replacing its old one (a planned
     * march of the old order is called off).
     *
     * @throws ApiException {@code WORLD_NOT_SOLO}; {@code NOT_AN_ALLY} for a lord outside the
     *     human's guild or the target's owner; {@code ORDER_TARGET_INVALID} for a kind the target
     *     doesn't take
     */
    @Transactional
    public OrderSheetDto give(long playerId, OrderRequest request) {
        requireSolo();
        Village target = village(request.villageId());
        Long owner = ownerOf(target);
        if (!kinds(playerId, owner).contains(request.kind())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_TARGET_INVALID,
                    "This village doesn't take that order");
        }
        List<Long> allyIds = allies(playerId).stream().map(Ally::id).toList();
        LinkedHashSet<Long> lords = new LinkedHashSet<>(request.lordIds());
        for (long lordId : lords) {
            if (!allyIds.contains(lordId) || (owner != null && owner == lordId)) {
                throw new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.NOT_AN_ALLY, "Not an ally: " + lordId);
            }
        }
        Instant now = worldService.gameTime(clock.instant());
        boolean landTogether = request.landTogether() && request.kind() == NpcOrderKind.ATTACK;
        for (long lordId : lords) {
            callOffMarch(lordId);
            store.save(
                    NpcOrderStore.Order.given(
                            lordId, playerId, request.kind(), target.getId(), landTogether, now));
        }
        return sheet(playerId, target.getId());
    }

    /**
     * Takes the human's order to the lord back (a planned march stays home; a support on order may
     * go home again in the lord's next round).
     *
     * @throws ApiException {@code NOT_AN_ALLY} if the human gave the lord no order
     */
    @Transactional
    public void cancel(long playerId, long lordId) {
        requireSolo();
        NpcOrderStore.Order order =
                store.find(lordId)
                        .filter(o -> o.playerId() == playerId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.CONFLICT,
                                                ErrorCode.NOT_AN_ALLY,
                                                "You gave this lord no order"));
        callOffMarch(order.lordId());
        store.delete(order.lordId());
    }

    /** The human's orders by lord, for the diplomacy page. */
    @Transactional(readOnly = true)
    public Map<Long, OrderDto> ordersOf(long playerId) {
        return store.ofPlayer(playerId).stream()
                .collect(Collectors.toMap(NpcOrderStore.Order::lordId, this::dto));
    }

    private void callOffMarch(long lordId) {
        store.find(lordId)
                .filter(o -> o.state() == NpcOrderState.PLANNED)
                .ifPresent(o -> scheduler.cancel(GameEventKind.NPC_ORDER_MARCH, lordId));
    }

    private OrderDto dto(NpcOrderStore.Order order) {
        return new OrderDto(
                order.kind(),
                villages.findById(order.targetVillageId()).map(VillageRefDto::of).orElse(null),
                order.state(),
                order.landTogether(),
                order.answer(),
                order.givenAt(),
                order.departAt(),
                order.arriveAt());
    }

    /** The kinds the target takes: by whose it is, seen from the human (12 §8). */
    private List<NpcOrderKind> kinds(long playerId, Long owner) {
        return NpcOrderSheet.kinds(owner, playerId, owner != null && sameGuild(playerId, owner));
    }

    private void requireSolo() {
        if (!trust.active()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched lord guilds on");
        }
        if (!worldService.settings().soloMode()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_SOLO,
                    "Lords take orders only in a solo world");
        }
    }

    private Village village(long villageId) {
        return villages.findById(villageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND));
    }

    private static Long ownerOf(Village village) {
        return village.getPlayer() == null ? null : village.getPlayer().getId();
    }

    /** The NPC lords of the human's guild, by name. */
    private List<Ally> allies(long playerId) {
        List<Ally> allies =
                jdbc.sql(
                                """
                                select p.id, p.name from guild_member me
                                join guild_member m on m.guild_id = me.guild_id
                                join player p on p.id = m.player_id
                                where me.player_id = :player and p.npc and p.village_count > 0
                                """)
                        .param("player", playerId)
                        .query((rs, n) -> new Ally(rs.getLong("id"), rs.getString("name")))
                        .list();
        return allies.stream()
                .sorted(Comparator.comparing(Ally::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean sameGuild(long playerId, long otherId) {
        return jdbc.sql(
                                """
                                select count(*) from guild_member a join guild_member b
                                    on b.guild_id = a.guild_id
                                where a.player_id = :a and b.player_id = :b
                                """)
                        .param("a", playerId)
                        .param("b", otherId)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    private String guildTagOf(Long owner) {
        if (owner == null) {
            return null;
        }
        return jdbc.sql(
                        """
                        select g.tag from guild g join guild_member m on m.guild_id = g.id
                        where m.player_id = :player
                        """)
                .param("player", owner)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
