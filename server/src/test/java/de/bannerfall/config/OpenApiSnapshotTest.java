package de.bannerfall.config;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The web generates its API types ({@code web/src/api/schema.d.ts}) from {@code
 * web/src/api/openapi.json}, a copy of the server's OpenAPI description. This test fails when the
 * copy no longer matches the server, and CI's web job ({@code pnpm check:api}) fails when the types
 * no longer match the copy, so the types can't drift from the server unnoticed.
 *
 * <p>After an API change: {@code ./gradlew test --tests '*OpenApiSnapshotTest' -PupdateOpenApi}
 * writes the copy, then {@code cd ../web && pnpm gen:api} regenerates the types.
 *
 * <p>With the profile {@code dev}, like the server {@code pnpm gen:api} used to read: the web has
 * types for the dev cheats too. It shares its context with {@code DevCheatControllerTest}.
 */
@IntegrationTest
@ActiveProfiles("dev")
class OpenApiSnapshotTest {

    /** Relative to {@code server/}, the working directory of the tests. */
    static final Path SNAPSHOT = Path.of("../web/src/api/openapi.json");

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Autowired MockMvc mockMvc;

    @Test
    void theWebsCopyOfTheApiDescriptionIsCurrent() throws Exception {
        JsonNode current = JSON.readTree(body(Browser.open(mockMvc).get("/v3/api-docs")));
        // the server URL is the test's http://localhost; the types don't use it
        ((ObjectNode) current).remove("servers");

        if (Boolean.getBoolean("bannerfall.updateOpenApi")) {
            Files.writeString(
                    SNAPSHOT, JSON.writeValueAsString(current) + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(SNAPSHOT).exists();
        JsonNode copy = JSON.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        assertThat(firstDifference(copy, current, ""))
                .as(
                        "The API changed: run ./gradlew test --tests '*OpenApiSnapshotTest'"
                            + " -PupdateOpenApi, then pnpm gen:api in web/. First difference at")
                .isNull();
    }

    /**
     * The JSON pointer of the first place where the two differ, null if they are equal; key order
     * doesn't count. Keeps the failure short instead of printing the whole description twice.
     */
    static String firstDifference(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject() && actual.isObject()) {
            for (String name : expected.propertyNames()) {
                String inner = path + "/" + name;
                if (!actual.has(name)) {
                    return inner + " (gone)";
                }
                String difference = firstDifference(expected.get(name), actual.get(name), inner);
                if (difference != null) {
                    return difference;
                }
            }
            for (String name : actual.propertyNames()) {
                if (!expected.has(name)) {
                    return path + "/" + name + " (new)";
                }
            }
            return null;
        }
        if (expected.isArray() && actual.isArray() && expected.size() == actual.size()) {
            for (int i = 0; i < expected.size(); i++) {
                String difference = firstDifference(expected.get(i), actual.get(i), path + "/" + i);
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        return expected.equals(actual) ? null : (path.isEmpty() ? "/" : path);
    }
}
