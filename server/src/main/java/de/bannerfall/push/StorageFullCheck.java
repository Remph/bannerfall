package de.bannerfall.push;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.Production;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * "Storage full" (1.6, 01-architecture §4.8b): resources are worked out lazily, so nothing happens
 * at the moment a warehouse fills. This recurring STORAGE_CHECK looks every {@link #INTERVAL} at
 * the villages of the players who switched the push on and have a device, and sends STORAGE_FULL
 * once when wood, clay or iron of a village reached the limit. The village is reported again only
 * after a check found nothing full there ({@code storage_full_push}).
 *
 * <p>Reads the villages without locking them: it only computes, and a request that spends the
 * resources meanwhile at worst makes the push a moment late.
 */
@Component
class StorageFullCheck implements GameEventHandler {

    static final Duration INTERVAL = Duration.ofMinutes(2);

    private final PushSettings settings;
    private final VillageRepository villages;
    private final VillageBanners villageBanners;
    private final WorldService worldService;
    private final EventScheduler scheduler;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    StorageFullCheck(
            PushSettings settings,
            VillageRepository villages,
            VillageBanners villageBanners,
            WorldService worldService,
            EventScheduler scheduler,
            JdbcClient jdbc,
            ApplicationEventPublisher events,
            Clock clock) {
        this.settings = settings;
        this.villages = villages;
        this.villageBanners = villageBanners;
        this.worldService = worldService;
        this.scheduler = scheduler;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.STORAGE_CHECK;
    }

    @Override
    public void handle(GameEvent event) {
        Instant now = clock.instant();
        if (settings.enabled()) {
            check(now);
        }
        // from now, not from the due time: after a long downtime one check is enough
        scheduler.schedule(GameEventKind.STORAGE_CHECK, null, now.plus(INTERVAL));
    }

    /** One look at every village of the players who want the push. */
    void check(Instant now) {
        Instant t = worldService.gameTime(now);
        WorldSettings world = worldService.settings();
        Map<Long, Long> reported = reported();
        Map<Long, Long> watched = new HashMap<>();
        for (Village village : villages.findAllById(watchedVillages())) {
            long playerId = village.getPlayer().getId();
            watched.put(village.getId(), playerId);
            List<ResourceType> full = full(village, t, world);
            boolean before = Long.valueOf(playerId).equals(reported.get(village.getId()));
            if (!full.isEmpty() && !before) {
                jdbc.sql(
                                """
                                insert into storage_full_push (village_id, player_id, pushed_at)
                                values (:village, :player, :at)
                                on conflict (village_id)
                                do update set player_id = excluded.player_id,
                                              pushed_at = excluded.pushed_at
                                """)
                        .param("village", village.getId())
                        .param("player", playerId)
                        .param("at", Timestamp.from(now))
                        .update();
                events.publishEvent(
                        new PlayerNotification(
                                playerId, new GameNotification.StorageFull(village.getId(), full)));
            } else if (full.isEmpty() && reported.containsKey(village.getId())) {
                forget(village.getId());
            }
        }
        // switched off, no device left, or the village changed hands: start afresh next time
        reported.forEach(
                (villageId, playerId) -> {
                    if (!playerId.equals(watched.get(villageId))) {
                        forget(villageId);
                    }
                });
    }

    /** The resources at the warehouse limit at {@code t}. */
    private List<ResourceType> full(Village village, Instant t, WorldSettings world) {
        BannerEffect banner = villageBanners.effectOf(village);
        int capacity = villageBanners.warehouseCapacity(village);
        List<ResourceType> full = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            double stored =
                    switch (type) {
                        case WOOD -> village.getWood();
                        case CLAY -> village.getClay();
                        case IRON -> village.getIron();
                    };
            double now =
                    Production.advance(
                            stored,
                            VillageStateService.productionPerHour(village, type, world, banner),
                            capacity,
                            village.getResourcesUpdatedAt(),
                            t);
            if (now >= capacity) {
                full.add(type);
            }
        }
        return full;
    }

    /**
     * The villages of players with the switch on (it starts off, so only a stored "on" counts) and
     * at least one device.
     */
    private List<Long> watchedVillages() {
        return jdbc.sql(
                        """
                        select v.id from village v
                        join push_preference p on p.player_id = v.player_id
                        where p.kind = :kind and p.enabled
                          and exists (select 1 from push_subscription s
                                      where s.player_id = v.player_id)
                        """)
                .param("kind", PushKind.STORAGE_FULL.name())
                .query(Long.class)
                .list();
    }

    /** The villages reported full, with the player they were reported to. */
    private Map<Long, Long> reported() {
        Map<Long, Long> result = new HashMap<>();
        jdbc.sql("select village_id, player_id from storage_full_push")
                .query(
                        rs -> {
                            result.put(rs.getLong("village_id"), rs.getLong("player_id"));
                        });
        return result;
    }

    private void forget(long villageId) {
        jdbc.sql("delete from storage_full_push where village_id = :village")
                .param("village", villageId)
                .update();
    }
}
