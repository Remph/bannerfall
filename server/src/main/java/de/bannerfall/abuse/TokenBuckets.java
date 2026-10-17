package de.bannerfall.abuse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One token bucket per key, in memory: a bucket holds up to {@code burst} tokens, refills at {@code
 * perSecond} tokens per second, and every request takes one. Like the other limiters, a restart
 * forgets everything and it counts per server instance (fine for one container).
 */
class TokenBuckets<K> {

    /** Clean up when the map gets this big (buckets that are full again are dropped). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Bucket(double tokens, Instant at) {}

    private final Clock clock;
    private volatile double perSecond;
    private volatile int burst;
    private final Map<K, Bucket> buckets = new ConcurrentHashMap<>();

    TokenBuckets(Clock clock, double perSecond, int burst) {
        if (perSecond <= 0 || burst < 1) {
            throw new IllegalArgumentException("perSecond > 0 and burst >= 1 needed");
        }
        this.clock = clock;
        this.perSecond = perSecond;
        this.burst = burst;
    }

    /** Takes a token for {@code key}; false if the bucket is empty (nothing is taken then). */
    boolean tryTake(K key) {
        Instant now = clock.instant();
        boolean[] taken = {false};
        buckets.compute(
                key,
                (k, bucket) -> {
                    double tokens = bucket == null ? burst : refilled(bucket, now);
                    if (tokens >= 1) {
                        taken[0] = true;
                        tokens -= 1;
                    }
                    return new Bucket(tokens, now);
                });
        if (buckets.size() > CLEANUP_THRESHOLD) {
            forgetFull();
        }
        return taken[0];
    }

    /**
     * Drops the buckets that are full again: their keys have not asked for a while and would start
     * with a full bucket anyway. Keeps the keys (client addresses for the login limits) in memory
     * only as long as they matter.
     */
    void forgetFull() {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> refilled(bucket, now) >= burst);
    }

    /** How many keys are held (tests). */
    int size() {
        return buckets.size();
    }

    /** Other numbers (tests); every bucket starts full again. */
    void setLimits(double perSecond, int burst) {
        this.perSecond = perSecond;
        this.burst = burst;
        buckets.clear();
    }

    private double refilled(Bucket bucket, Instant now) {
        double seconds = Math.max(0, Duration.between(bucket.at(), now).toNanos() / 1e9);
        return Math.min(burst, bucket.tokens() + seconds * perSecond);
    }
}
