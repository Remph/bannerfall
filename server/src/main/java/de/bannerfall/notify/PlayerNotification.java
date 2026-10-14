package de.bannerfall.notify;

import de.bannerfall.player.Player;
import de.bannerfall.village.Village;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The Spring application event behind every {@link GameNotification}: services publish it inside
 * their transaction ({@link ApplicationEventPublisher#publishEvent}), {@link NotificationPublisher}
 * sends it once that transaction has committed.
 *
 * @param playerId the recipient; NPC lords have no connection, their notifications go nowhere
 */
public record PlayerNotification(long playerId, GameNotification notification) {

    /**
     * Publishes the notification for the owner of the village; nothing for a barbarian village.
     * Inside a transaction (the owner is loaded if it isn't yet).
     */
    public static void toOwnerOf(
            Village village, GameNotification notification, ApplicationEventPublisher events) {
        Player owner = village.getPlayer();
        if (owner != null) {
            events.publishEvent(new PlayerNotification(owner.getId(), notification));
        }
    }
}
