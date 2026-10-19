package de.bannerfall.push;

import de.bannerfall.account.SessionsEnded;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The devices that get push for a player, and his switches (1.5). Plain SQL ({@code
 * push_subscription}, {@code push_preference}).
 *
 * <p>A device's subscription belongs to the player who logged in last on it: the endpoint is
 * unique, so the same browser logging in as somebody else moves to him. It goes when the player
 * turns push off or logs out on it (the web asks for that), when all his sessions end (password
 * change or reset, ban, deletion: a device somebody else still had open stops hearing about his
 * villages; his own devices sign up again at their next login), and when its push service says it
 * is gone.
 */
@Service
public class PushSubscriptionService {

    /** Devices per player; the oldest goes when a new one comes. */
    static final int MAX_DEVICES = 10;

    static final Set<String> LANGUAGES = Set.of("en", "de");

    /** One device. */
    record Subscription(
            long id, long playerId, String endpoint, String p256dh, String auth, String language) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final PushSettings settings;

    PushSubscriptionService(JdbcClient jdbc, Clock clock, PushSettings settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    /**
     * Saves the device for the player, or updates it (new keys, another language, another player on
     * the same browser).
     *
     * @throws ApiException 409 {@code PUSH_UNAVAILABLE} when the world has no push; 400 {@code
     *     VALIDATION} for an endpoint that is not one of the push services, keys that aren't a
     *     browser's, or a language the game doesn't have
     */
    @Transactional
    public void subscribe(
            long playerId, String endpoint, String p256dh, String auth, String language) {
        requireEnabled();
        URI uri = endpointUri(endpoint);
        if (!settings.allowed(uri)) {
            throw invalid("Not a known push service: " + uri.getHost());
        }
        try {
            VapidKeys.publicKeyOf(VapidKeys.decode(p256dh));
            if (VapidKeys.decode(auth).length != 16) {
                throw invalid("auth must be 16 bytes");
            }
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            throw invalid("Not a browser's push keys");
        }
        if (!LANGUAGES.contains(language)) {
            throw invalid("Unknown language " + language);
        }
        jdbc.sql(
                        """
                        insert into push_subscription
                            (player_id, endpoint, p256dh, auth, language, created_at)
                        values (:player, :endpoint, :p256dh, :auth, :language, :now)
                        on conflict (endpoint) do update
                           set player_id = excluded.player_id, p256dh = excluded.p256dh,
                               auth = excluded.auth, language = excluded.language,
                               created_at = case
                                   when push_subscription.player_id = excluded.player_id
                                   then push_subscription.created_at
                                   else excluded.created_at end
                        """)
                .param("player", playerId)
                .param("endpoint", endpoint)
                .param("p256dh", p256dh)
                .param("auth", auth)
                .param("language", language)
                .param("now", Timestamp.from(clock.instant()))
                .update();
        // the oldest devices beyond the limit
        jdbc.sql(
                        """
                        delete from push_subscription where id in (
                            select id from push_subscription where player_id = :player
                            order by created_at desc, id desc offset :max)
                        """)
                .param("player", playerId)
                .param("max", MAX_DEVICES)
                .update();
    }

    /** Forgets the device, if it is the player's (turned off, or logging out). */
    @Transactional
    public void unsubscribe(long playerId, String endpoint) {
        jdbc.sql("delete from push_subscription where player_id = :player and endpoint = :endpoint")
                .param("player", playerId)
                .param("endpoint", endpoint)
                .update();
    }

    /** The player's devices. */
    List<Subscription> subscriptionsOf(long playerId) {
        return jdbc.sql(
                        """
                        select id, player_id, endpoint, p256dh, auth, language
                        from push_subscription where player_id = :player order by id
                        """)
                .param("player", playerId)
                .query(Subscription.class)
                .list();
    }

    /** How many devices the player has. */
    int deviceCount(long playerId) {
        return jdbc.sql("select count(*) from push_subscription where player_id = :player")
                .param("player", playerId)
                .query(Integer.class)
                .single();
    }

    /** Devices and players with at least one, over the whole world (the host's status). */
    public int[] counts() {
        return jdbc.sql("select count(*), count(distinct player_id) from push_subscription")
                .query((rs, row) -> new int[] {rs.getInt(1), rs.getInt(2)})
                .single();
    }

    /** The push service accepted a message for the device. */
    void sent(long subscriptionId, Instant at) {
        jdbc.sql("update push_subscription set last_sent_at = :at where id = :id")
                .param("at", Timestamp.from(at))
                .param("id", subscriptionId)
                .update();
    }

    /** The push service says the device is gone (unsubscribed, app removed, expired). */
    void gone(long subscriptionId) {
        jdbc.sql("delete from push_subscription where id = :id")
                .param("id", subscriptionId)
                .update();
    }

    /** Every kind with the player's choice, or its default where he made none. */
    public Map<PushKind, Boolean> preferences(long playerId) {
        Map<PushKind, Boolean> result = new EnumMap<>(PushKind.class);
        for (PushKind kind : PushKind.values()) {
            result.put(kind, kind.defaultEnabled());
        }
        jdbc.sql("select kind, enabled from push_preference where player_id = :player")
                .param("player", playerId)
                .query(
                        rs -> {
                            try {
                                result.put(
                                        PushKind.valueOf(rs.getString("kind")),
                                        rs.getBoolean("enabled"));
                            } catch (IllegalArgumentException retired) {
                                // a kind an older version had: nothing to switch any more
                            }
                        });
        return result;
    }

    /** Whether the player wants this kind pushed. */
    boolean wants(long playerId, PushKind kind) {
        return jdbc.sql(
                        "select enabled from push_preference where player_id = :player and kind ="
                                + " :kind")
                .param("player", playerId)
                .param("kind", kind.name())
                .query(Boolean.class)
                .optional()
                .orElse(kind.defaultEnabled());
    }

    /** Stores the given switches; kinds left out keep what they were. */
    @Transactional
    public Map<PushKind, Boolean> setPreferences(long playerId, Map<PushKind, Boolean> changes) {
        changes.forEach(
                (kind, enabled) ->
                        jdbc.sql(
                                        """
                                        insert into push_preference (player_id, kind, enabled)
                                        values (:player, :kind, :enabled)
                                        on conflict (player_id, kind)
                                        do update set enabled = excluded.enabled
                                        """)
                                .param("player", playerId)
                                .param("kind", kind.name())
                                .param("enabled", enabled)
                                .update());
        return preferences(playerId);
    }

    /**
     * All of the player's devices go when his sessions end: a ban, a deletion, a password change or
     * reset. In the same transaction when there is one, so the devices go with the sessions.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    void on(SessionsEnded event) {
        jdbc.sql(
                        """
                        delete from push_subscription
                        where player_id in (select id from player where account_id = :account)
                        """)
                .param("account", event.accountId())
                .update();
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.PUSH_UNAVAILABLE, "This world sends no push");
        }
    }

    private static URI endpointUri(String endpoint) {
        try {
            return new URI(endpoint);
        } catch (URISyntaxException e) {
            throw invalid("Not an address: endpoint");
        }
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, detail);
    }
}
