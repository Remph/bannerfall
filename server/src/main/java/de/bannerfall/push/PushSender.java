package de.bannerfall.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hands one encrypted message to a device's push service (RFC 8030), which delivers it when the
 * device is online. Only to the services in {@link PushSettings}, never following a redirect, with
 * short timeouts. Runs on {@link PushNotifier}'s threads, never inside a game transaction.
 */
@Component
class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    /** How long one attempt may take; a second one follows only for a busy or failing service. */
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Before the one retry. */
    static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    /**
     * How the push service treats the message.
     *
     * @param ttl how long it keeps the message for a device that is offline
     * @param urgency {@code high} wakes a phone that saves battery, {@code normal} may wait
     * @param topic a later message with the same topic replaces one still waiting; null for none
     *     (letters, digits, {@code -} and {@code _}, at most 32)
     */
    record Delivery(Duration ttl, String urgency, String topic) {}

    /** What became of one message. */
    enum Outcome {
        SENT,
        /** The device is gone (404, 410): its subscription was deleted. */
        GONE,
        FAILED
    }

    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private final PushSettings settings;
    private final PushSubscriptionService subscriptions;
    private final Clock clock;

    PushSender(PushSettings settings, PushSubscriptionService subscriptions, Clock clock) {
        this.settings = settings;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    Outcome send(PushSubscriptionService.Subscription device, byte[] json, Delivery delivery) {
        URI endpoint = URI.create(device.endpoint());
        // checked when it was saved; checked again in case the list got shorter since
        if (!settings.allowed(endpoint)) {
            log.warn(
                    "Push to {} is not allowed any more, device {} removed",
                    endpoint.getHost(),
                    device.id());
            subscriptions.gone(device.id());
            return Outcome.GONE;
        }
        byte[] body =
                WebPushCrypto.encrypt(
                        json, VapidKeys.decode(device.p256dh()), VapidKeys.decode(device.auth()));
        for (int attempt = 1; ; attempt++) {
            int status;
            try {
                status = post(endpoint, body, delivery);
            } catch (IOException e) {
                status = -1;
                log.info("Push to {} failed: {}", endpoint.getHost(), e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Outcome.FAILED;
            }
            if (status >= 200 && status < 300) {
                subscriptions.sent(device.id(), clock.instant());
                return Outcome.SENT;
            }
            if (status == 404 || status == 410) {
                log.info("Push device {} is gone ({}), removed", device.id(), status);
                subscriptions.gone(device.id());
                return Outcome.GONE;
            }
            boolean retry = status == -1 || status == 429 || status >= 500;
            if (!retry || attempt == 2) {
                if (status != -1) {
                    log.warn("Push to {} refused with {}", endpoint.getHost(), status);
                }
                return Outcome.FAILED;
            }
            try {
                Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Outcome.FAILED;
            }
        }
    }

    private int post(URI endpoint, byte[] body, Delivery delivery)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(TIMEOUT)
                        .header(
                                "Authorization",
                                WebPushCrypto.vapidAuthorization(
                                        endpoint,
                                        settings.keys(),
                                        settings.subject(),
                                        clock.instant()))
                        .header("Content-Encoding", "aes128gcm")
                        .header("Content-Type", "application/octet-stream")
                        .header("TTL", Long.toString(Math.max(0, delivery.ttl().toSeconds())))
                        .header("Urgency", delivery.urgency())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (delivery.topic() != null) {
            request.header("Topic", delivery.topic());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
