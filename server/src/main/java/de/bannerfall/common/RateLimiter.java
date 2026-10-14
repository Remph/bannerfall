package de.bannerfall.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One action per key (a player) and interval, in memory: a restart forgets it, which costs at most
 * one extra action. Used by the chat, private messages and guild invitations.
 *
 * <p>Taken inside a transaction, the slot counts only if that transaction commits: on a rollback it
 * is given back, so a failed message or invitation doesn't block the next try.
 */
public final class RateLimiter {

    /** Above this many keys the ones whose interval has passed are swept out. */
    private static final int SWEEP_ABOVE = 10_000;

    private final Map<Long, Instant> last = new ConcurrentHashMap<>();

    /**
     * Takes the key's slot at {@code now}.
     *
     * @param tooSoon the exception to throw if the last slot is less than {@code interval} old
     */
    public void take(
            long key,
            Instant now,
            Duration interval,
            Supplier<? extends RuntimeException> tooSoon) {
        // compute() is atomic per key: two actions at the same moment can't both pass. Throwing
        // inside leaves the old time in place.
        last.compute(
                key,
                (k, previous) -> {
                    if (previous != null && now.isBefore(previous.plus(interval))) {
                        throw tooSoon.get();
                    }
                    return now;
                });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                last.remove(key, now);
                            }
                        }
                    });
        }
        if (last.size() > SWEEP_ABOVE) {
            last.values().removeIf(previous -> !now.isBefore(previous.plus(interval)));
        }
    }

    /** Forgets every slot (tests). */
    public void clear() {
        last.clear();
    }
}
