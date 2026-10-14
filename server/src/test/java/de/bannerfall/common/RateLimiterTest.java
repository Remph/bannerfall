package de.bannerfall.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final RateLimiter limiter = new RateLimiter();

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void take(long key, Instant at) {
        limiter.take(key, at, MINUTE, () -> new IllegalStateException("too soon"));
    }

    @Test
    void oneSlotPerKeyAndInterval() {
        take(1, T0);
        assertThatThrownBy(() -> take(1, T0.plusSeconds(59))).hasMessage("too soon");
        assertThatCode(() -> take(2, T0.plusSeconds(59))).doesNotThrowAnyException();
        assertThatCode(() -> take(1, T0.plusSeconds(60))).doesNotThrowAnyException();
    }

    @Test
    void aRolledBackTransactionGivesTheSlotBack() {
        TransactionSynchronizationManager.initSynchronization();
        take(1, T0);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        assertThatCode(() -> take(1, T0.plusSeconds(1))).doesNotThrowAnyException();
    }

    @Test
    void aCommittedTransactionKeepsIt() {
        TransactionSynchronizationManager.initSynchronization();
        take(1, T0);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();

        assertThatThrownBy(() -> take(1, T0.plusSeconds(1))).hasMessage("too soon");
    }
}
