package de.bannerfall.cli;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/**
 * The CLI's HTTP side: JDK {@code HttpClient} against {@code /api/admin/**} with the host token as
 * bearer header (08-host-console §4). No session, no CSRF. Every answer is JSON (or empty); an
 * error answer becomes a {@link CliException} with the server's {@code code} and detail.
 */
class AdminClient {

    private final String baseUrl;
    private final String token;
    private final JsonMapper mapper;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * @param token null when {@code BANNERFALL_ADMIN_TOKEN} isn't set: every call then fails with a
     *     message saying so
     */
    AdminClient(String baseUrl, String token, JsonMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.mapper = mapper;
    }

    JsonNode get(String path) {
        return send("GET", path, null);
    }

    JsonNode post(String path, Object body) {
        return send("POST", path, body);
    }

    JsonNode put(String path, Object body) {
        return send("PUT", path, body);
    }

    JsonNode delete(String path) {
        return send("DELETE", path, null);
    }

    private JsonNode send(String method, String path, Object body) {
        if (token == null) {
            throw new CliException(
                    "BANNERFALL_ADMIN_TOKEN is not set: the host token door is closed. Set it in"
                            + " .env (openssl rand -base64 32) and restart the server.");
        }
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json");
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .method(method, BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else {
            request.method(method, BodyPublishers.noBody());
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new CliException("Can't reach the server at " + baseUrl + " (is it running?)");
        } catch (IOException e) {
            throw new CliException("Can't talk to the server at " + baseUrl + ": " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException("Interrupted");
        }
        JsonNode json = parse(response.body());
        if (response.statusCode() >= 400) {
            throw new CliException(refusal(response.statusCode(), json));
        }
        return json;
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return mapper.readTree(body);
        } catch (JacksonException e) {
            return NullNode.getInstance();
        }
    }

    /** "409 LAST_ADMIN: The last admin can't stop being one", plus the field errors of a 400. */
    private static String refusal(int status, JsonNode problem) {
        StringBuilder text = new StringBuilder("The server refused: ").append(status);
        String code = Json.text(problem, "code");
        if (code != null) {
            text.append(' ').append(code);
        }
        String detail = Json.text(problem, "detail");
        if (detail != null) {
            text.append(": ").append(detail);
        }
        for (JsonNode error : problem.path("errors")) {
            text.append(System.lineSeparator())
                    .append("  ")
                    .append(Json.text(error, "field"))
                    .append(": ")
                    .append(Json.text(error, "message"));
        }
        if (status == 401) {
            text.append(System.lineSeparator())
                    .append("  (wrong BANNERFALL_ADMIN_TOKEN, or the server has none set)");
        }
        return text.toString();
    }
}
