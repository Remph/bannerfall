package de.bannerfall;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A push service for the tests of push notifications (1.5): it takes every POST to {@code
 * /push/...} and keeps it, answering 201 or what a test set for that address. The test world sends
 * pushes only here ({@link TestPushConfiguration}).
 */
public final class FakePushService {

    /** One push as it arrived: the address, the headers the sender set, the encrypted body. */
    public record Received(String path, Map<String, String> headers, byte[] body) {}

    private static FakePushService instance;

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> holds = new ConcurrentHashMap<>();

    private FakePushService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/push/", this::push);
        server.start();
    }

    public static synchronized FakePushService get() {
        if (instance == null) {
            try {
                instance = new FakePushService();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    /** The origin the test world may send to: {@code http://localhost:<port>}. */
    public String origin() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** A new device address. */
    public String newEndpoint() {
        return origin() + "/push/" + UUID.randomUUID();
    }

    /** Answers this status for the address from now on (404, 410, 500 ...). */
    public void answer(String endpoint, int status) {
        statuses.put(path(endpoint), status);
    }

    /**
     * Keeps the sender of the next pushes to the address waiting for the answer until {@link
     * #release} (at most 10 s): the world's push thread is busy meanwhile.
     */
    public void hold(String endpoint) {
        holds.put(path(endpoint), new CountDownLatch(1));
    }

    /** Answers the pushes {@link #hold} keeps waiting, and the next ones at once. */
    public void release(String endpoint) {
        CountDownLatch latch = holds.remove(path(endpoint));
        if (latch != null) {
            latch.countDown();
        }
    }

    /** Everything sent to the address so far. */
    public List<Received> to(String endpoint) {
        String path = path(endpoint);
        return received.stream().filter(push -> push.path().equals(path)).toList();
    }

    /** Waits up to 10 s until the address has at least {@code count} pushes, and returns them. */
    public List<Received> await(String endpoint, int count) throws InterruptedException {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (to(endpoint).size() < count && System.nanoTime() < until) {
            Thread.sleep(20);
        }
        List<Received> pushes = to(endpoint);
        if (pushes.size() < count) {
            throw new AssertionError(
                    "Expected " + count + " pushes to " + endpoint + ", got " + pushes.size());
        }
        return pushes;
    }

    private void push(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> headers = new ConcurrentHashMap<>();
            exchange.getRequestHeaders()
                    .forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            received.add(new Received(path, headers, exchange.getRequestBody().readAllBytes()));
            CountDownLatch hold = holds.get(path);
            if (hold != null) {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 201), -1);
        }
    }

    private static String path(String endpoint) {
        return java.net.URI.create(endpoint).getPath();
    }
}
