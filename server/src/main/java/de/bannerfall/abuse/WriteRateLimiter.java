package de.bannerfall.abuse;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The write rate limit per account (M14e, 01-architecture §4.9): POST / PUT / PATCH / DELETE under
 * {@code /api/**}, {@code bannerfall.abuse.write-limit.per-second} on average with bursts of {@code
 * …burst} (default 10 / 30: generous for a person tapping fast, a wall for a script). Login,
 * registration and the chat keep their own limits. Checked by {@link AccountGuardFilter}.
 */
@Component
public class WriteRateLimiter {

    private final TokenBuckets<Long> buckets;

    WriteRateLimiter(
            Clock clock,
            @Value("${bannerfall.abuse.write-limit.per-second:10}") double perSecond,
            @Value("${bannerfall.abuse.write-limit.burst:30}") int burst) {
        this.buckets = new TokenBuckets<>(clock, perSecond, burst);
    }

    /** False if the account has used up its writes for now. */
    public boolean tryWrite(long accountId) {
        return buckets.tryTake(accountId);
    }

    /** Other numbers for a test; the buckets start full again. */
    public void setLimits(double perSecond, int burst) {
        buckets.setLimits(perSecond, burst);
    }
}
