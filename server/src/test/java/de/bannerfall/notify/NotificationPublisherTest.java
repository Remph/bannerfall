package de.bannerfall.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.IntegrationTest;
import de.bannerfall.SentMessages;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notifications leave only after commit (01-architecture §4.8): a rolled-back transaction sends
 * nothing. The player ids are made up; nobody has to exist or be connected for the server to hand
 * the message to the broker, which is what {@link SentMessages} records.
 */
@IntegrationTest
class NotificationPublisherTest {

    /** Far away from the ids of registered players. */
    private static final AtomicLong nextPlayerId = new AtomicLong(1_000_000_000L);

    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SentMessages sent;

    @Test
    void aCommittedTransactionSendsItsNotificationsInOrderAfterCommit() {
        long playerId = nextPlayerId.incrementAndGet();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            publish(playerId, new GameNotification.VillageUpdated(7));
                            publish(playerId, new GameNotification.SupportArrived(8));
                            // still inside: nothing has left yet
                            assertThat(sent.toPlayer(playerId)).isEmpty();
                        });

        assertThat(sent.toPlayer(playerId))
                .containsExactly(
                        "{\"type\":\"VILLAGE_UPDATED\",\"villageId\":7}",
                        "{\"type\":\"SUPPORT_ARRIVED\",\"villageId\":8}");
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        long playerId = nextPlayerId.incrementAndGet();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            publish(playerId, new GameNotification.VillageUpdated(7));
                            status.setRollbackOnly();
                        });

        assertThat(sent.toPlayer(playerId)).isEmpty();
    }

    @Test
    void aTransactionThatFailsSendsNothing() {
        long playerId = nextPlayerId.incrementAndGet();

        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    publish(
                                                            playerId,
                                                            new GameNotification.VillageUpdated(7));
                                                    throw new IllegalStateException("boom");
                                                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sent.toPlayer(playerId)).isEmpty();
    }

    @Test
    void outsideATransactionNothingIsSent() {
        long playerId = nextPlayerId.incrementAndGet();

        publish(playerId, new GameNotification.VillageUpdated(7));

        assertThat(sent.toPlayer(playerId)).isEmpty();
    }

    private void publish(long playerId, GameNotification notification) {
        events.publishEvent(new PlayerNotification(playerId, notification));
    }
}
