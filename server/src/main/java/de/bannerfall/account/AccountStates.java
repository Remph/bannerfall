package de.bannerfall.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What a logged-in request needs to know about its account (M14e, 01-architecture §4.9): deleted,
 * banned, and which sessions are still valid. Read for every request of a logged-in player, so it
 * is cached for {@link #MAX_AGE}; every change made through the app ({@link #changed}) throws the
 * entry away after the commit, so a ban counts at once. The cache only matters for a change made by
 * hand in SQL.
 */
@Component
public class AccountStates {

    /** How long a read state is trusted. */
    static final Duration MAX_AGE = Duration.ofSeconds(5);

    /** Clean up when the map gets this big. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    /**
     * @param exists false for an account that isn't there (any more): its sessions end
     * @param bannedUntil null when not banned
     */
    public record State(
            boolean exists,
            boolean disabled,
            Instant bannedUntil,
            String banReason,
            int sessionVersion) {

        static final State MISSING = new State(false, false, null, null, 0);

        public boolean isBannedAt(Instant now) {
            return bannedUntil != null && bannedUntil.isAfter(now);
        }
    }

    private record Cached(State state, Instant readAt) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    AccountStates(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public State get(long accountId) {
        Instant now = clock.instant();
        Cached cached = cache.get(accountId);
        if (cached != null && now.isBefore(cached.readAt().plus(MAX_AGE))) {
            return cached.state();
        }
        State state = read(accountId);
        if (cache.size() > CLEANUP_THRESHOLD) {
            cache.clear();
        }
        cache.put(accountId, new Cached(state, now));
        return state;
    }

    /**
     * The account was changed (ban, unban, delete, password): forget its state once the change has
     * committed (or right away outside a transaction). Forgetting it earlier would let a request in
     * between cache the old state again.
     */
    public void changed(long accountId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            cache.remove(accountId);
                        }
                    });
        } else {
            cache.remove(accountId);
        }
    }

    private State read(long accountId) {
        return jdbc.sql(
                        """
                        select disabled, banned_until, ban_reason, session_version
                        from account where id = ?
                        """)
                .param(accountId)
                .query(
                        (rs, row) ->
                                new State(
                                        true,
                                        rs.getBoolean("disabled"),
                                        rs.getTimestamp("banned_until") == null
                                                ? null
                                                : rs.getTimestamp("banned_until").toInstant(),
                                        rs.getString("ban_reason"),
                                        rs.getInt("session_version")))
                .optional()
                .orElse(State.MISSING);
    }
}
