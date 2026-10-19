package de.bannerfall.push;

import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.WorldService;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns live notifications into Web Push for the player's devices (1.5, 01-architecture §4.8b): the
 * kinds the player keeps switched on ({@link PushKind}), whether or not a game window is open (the
 * service worker skips the notification while one is on screen). Only after the commit, like {@code
 * NotificationPublisher}, and on threads of its own ({@code bannerfall.push.threads}, 2 by default)
 * with a bounded queue: the game never waits for a push service, and a slow one only delays other
 * pushes.
 *
 * <p>A push still waiting for a thread takes in later ones with the same topic for the same player
 * (the next attack on the same village, a second test): it reads the count and the village when it
 * is sent, so nothing is lost, an attack train buzzes once, and neither a flood of fake attacks nor
 * a player tapping "Send a test" can fill the queue and crowd out other players' attacks.
 */
@Component
class PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);

    static final int QUEUE = 1000;

    static final Duration DAY = Duration.ofDays(1);

    /**
     * An attack's push is worth nothing once the last attack on the village landed; at least a
     * minute.
     */
    static final Duration MIN_TTL = Duration.ofMinutes(1);

    private final PushSettings settings;
    private final PushSubscriptionService subscriptions;
    private final PushSender sender;
    private final WorldService worldService;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    /** Pushes waiting for a thread, by player and topic; null notification = a test. */
    private final Map<String, Waiting> waiting = new ConcurrentHashMap<>();

    private record Waiting(long playerId, GameNotification notification) {}

    PushNotifier(
            PushSettings settings,
            PushSubscriptionService subscriptions,
            PushSender sender,
            WorldService worldService,
            JdbcClient jdbc,
            JsonMapper json,
            Clock clock,
            @Value("${bannerfall.push.threads:2}") int threads) {
        this.settings = settings;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.worldService = worldService;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        AtomicInteger numbers = new AtomicInteger();
        this.executor =
                new ThreadPoolExecutor(
                        threads,
                        threads,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(QUEUE),
                        task -> {
                            Thread thread = new Thread(task, "push-" + numbers.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
    }

    @TransactionalEventListener
    void on(PlayerNotification event) {
        if (!settings.enabled() || kindOf(event.notification()) == null) {
            return;
        }
        // only when the village's build queue ran empty (decided with the design), read now: the
        // next build of the queue may finish in the event worker's very next transaction
        if (event.notification() instanceof GameNotification.BuildComplete built
                && buildOrders(built.villageId()) > 0) {
            return;
        }
        enqueue(event.playerId(), topicOf(event.notification()), event.notification());
    }

    /**
     * Sends a test to every device of the player, whatever his switches say.
     *
     * @return how many devices there are
     */
    int test(long playerId) {
        int devices = subscriptions.deviceCount(playerId);
        if (devices > 0) {
            enqueue(playerId, "test", null);
        }
        return devices;
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /**
     * Queues the push, unless one with the same topic for the player is still waiting: then that
     * one sends this notification instead (it reads the village's attacks when it is sent).
     */
    private void enqueue(long playerId, String topic, GameNotification notification) {
        String key = playerId + "/" + topic;
        if (waiting.put(key, new Waiting(playerId, notification)) != null) {
            return;
        }
        try {
            executor.execute(
                    () -> {
                        Waiting next = waiting.remove(key);
                        if (next == null) {
                            return;
                        }
                        if (next.notification() == null) {
                            sendToDevices(
                                    next.playerId(),
                                    PushMessage.selfTest(),
                                    new PushSender.Delivery(Duration.ofMinutes(5), "high", "test"));
                        } else {
                            deliver(next.playerId(), next.notification());
                        }
                    });
        } catch (RejectedExecutionException e) {
            waiting.remove(key);
            log.warn("Push queue full, a push was dropped");
        }
    }

    /** Pushes with the same topic for a player replace each other while they wait. */
    static String topicOf(GameNotification notification) {
        return switch (notification) {
            case GameNotification.IncomingAttack attack -> "attack-" + attack.targetVillageId();
            case GameNotification.VillageLost lost -> "lost-" + lost.villageId();
            case GameNotification.MessageReceived received -> "message-" + received.fromPlayerId();
            case GameNotification.GuildInvited invited -> "guild-" + invited.guildId();
            case GameNotification.BuildComplete built -> "builds-" + built.villageId();
            default -> "world";
        };
    }

    /** The switch a notification falls under; null for those never pushed. */
    static PushKind kindOf(GameNotification notification) {
        return switch (notification) {
            case GameNotification.IncomingAttack ignored -> PushKind.ATTACK;
            case GameNotification.VillageLost ignored -> PushKind.VILLAGE_LOST;
            case GameNotification.MessageReceived ignored -> PushKind.MESSAGE;
            case GameNotification.GuildInvited ignored -> PushKind.GUILD_INVITE;
            case GameNotification.WorldEnded ignored -> PushKind.WORLD_END;
            // the toast says nothing when the clock stopped without anybody holding it
            case GameNotification.VictoryClock clock ->
                    clock.leader() == null && clock.previous() == null ? null : PushKind.WORLD_END;
            case GameNotification.BuildComplete ignored -> PushKind.BUILDS_DONE;
            default -> null;
        };
    }

    private void deliver(long playerId, GameNotification notification) {
        try {
            PushKind kind = kindOf(notification);
            if (!subscriptions.wants(playerId, kind)) {
                return;
            }
            PushMessage message = PushMessage.of(notification);
            PushSender.Delivery delivery;
            switch (notification) {
                case GameNotification.IncomingAttack attack -> {
                    VillageRefDto village = village(attack.targetVillageId());
                    Incoming incoming = incoming(attack.targetVillageId());
                    if (village == null || incoming.count() == 0) {
                        return; // cancelled or landed already
                    }
                    message =
                            message.about(village).withAttacks(incoming.count(), incoming.first());
                    Duration untilLast = Duration.between(clock.instant(), incoming.last());
                    delivery =
                            new PushSender.Delivery(
                                    untilLast.compareTo(MIN_TTL) < 0 ? MIN_TTL : untilLast,
                                    "high",
                                    "attack-" + village.id());
                }
                case GameNotification.VillageLost lost -> {
                    VillageRefDto village = village(lost.villageId());
                    if (village == null) {
                        return;
                    }
                    message = message.about(village);
                    delivery = new PushSender.Delivery(DAY, "high", "lost-" + village.id());
                }
                case GameNotification.MessageReceived received ->
                        delivery =
                                new PushSender.Delivery(
                                        DAY, "normal", "message-" + received.fromPlayerId());
                case GameNotification.BuildComplete built -> {
                    VillageRefDto village = village(built.villageId());
                    if (village == null) {
                        return;
                    }
                    message = message.about(village);
                    delivery = new PushSender.Delivery(DAY, "normal", "builds-" + village.id());
                }
                case GameNotification.WorldEnded ignored ->
                        delivery = new PushSender.Delivery(DAY, "normal", "world");
                case GameNotification.VictoryClock ignored ->
                        delivery = new PushSender.Delivery(DAY, "normal", "world");
                default -> delivery = new PushSender.Delivery(DAY, "normal", null);
            }
            sendToDevices(playerId, message, delivery);
        } catch (RuntimeException e) {
            log.error("Could not push {} to player {}", notification, playerId, e);
        }
    }

    private void sendToDevices(long playerId, PushMessage message, PushSender.Delivery delivery) {
        try {
            String world = worldService.settings().name();
            for (PushSubscriptionService.Subscription device :
                    subscriptions.subscriptionsOf(playerId)) {
                byte[] body = json.writeValueAsBytes(message.forDevice(device.language(), world));
                sender.send(device, body, delivery);
            }
        } catch (RuntimeException e) {
            log.error("Could not push to player {}", playerId, e);
        }
    }

    private record Incoming(int count, Instant first, Instant last) {}

    /** Attacks of others on the way to the village (as the red counter counts them). */
    private Incoming incoming(long villageId) {
        return jdbc.sql(
                        """
                        select count(*) as count, min(m.arrive_at) as first,
                               max(m.arrive_at) as last
                        from movement m join village t on t.id = m.target_village_id
                        where t.id = :village and m.kind = 'ATTACK'
                          and (t.player_id is null or m.owner_player_id <> t.player_id)
                        """)
                .param("village", villageId)
                .query(
                        (rs, row) -> {
                            Timestamp first = rs.getTimestamp("first");
                            Timestamp last = rs.getTimestamp("last");
                            return new Incoming(
                                    rs.getInt("count"),
                                    first == null ? null : first.toInstant(),
                                    last == null ? null : last.toInstant());
                        })
                .single();
    }

    private int buildOrders(long villageId) {
        return jdbc.sql("select count(*) from build_order where village_id = :village")
                .param("village", villageId)
                .query(Integer.class)
                .single();
    }

    /** The village with its owner now; null if it is gone. */
    private VillageRefDto village(long villageId) {
        List<VillageRefDto> found =
                jdbc.sql(
                                """
                                select v.id, v.name, v.x, v.y, v.player_id, p.name as player_name
                                from village v left join player p on p.id = v.player_id
                                where v.id = :village
                                """)
                        .param("village", villageId)
                        .query(
                                (rs, row) ->
                                        new VillageRefDto(
                                                rs.getLong("id"),
                                                rs.getString("name"),
                                                rs.getInt("x"),
                                                rs.getInt("y"),
                                                rs.getObject("player_id", Long.class),
                                                rs.getString("player_name")))
                        .list();
        return found.isEmpty() ? null : found.get(0);
    }
}
