package de.bannerfall.village;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VillageControllerTest {

    private static final double EPSILON = 1e-9;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private Browser browser;
    private long villageId;
    private String path;

    @BeforeEach
    void registerPlayer() throws Exception {
        browser = Browser.open(mockMvc);
        String body =
                browser.register(Browser.uniqueUsername())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        villageId = ((Number) JsonPath.read(body, "$.villages[0].id")).longValue();
        path = "/api/villages/" + villageId;
    }

    @Test
    void aNewVillage() throws Exception {
        browser.get(path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(villageId))
                .andExpect(jsonPath("$.points").value(39))
                .andExpect(jsonPath("$.loyalty").value(100))
                .andExpect(jsonPath("$.resources.wood", closeTo(500, EPSILON)))
                .andExpect(jsonPath("$.resources.at").value(clock.instant().toString()))
                .andExpect(jsonPath("$.production.wood", closeTo(30, EPSILON)))
                .andExpect(jsonPath("$.production.iron", closeTo(30, EPSILON)))
                .andExpect(jsonPath("$.storageCapacity").value(1000))
                .andExpect(jsonPath("$.hidingCapacity").value(0))
                // Town Hall 5 + woodcutter 5 + clay pit 10 + iron mine 10 (02-game-rules §4.1)
                .andExpect(jsonPath("$.population.used").value(30))
                .andExpect(jsonPath("$.population.capacity").value(240))
                .andExpect(jsonPath("$.buildings.length()").value(16))
                .andExpect(jsonPath("$.buildings.HEADQUARTERS").value(1))
                .andExpect(jsonPath("$.buildings.BARRACKS").value(0))
                .andExpect(jsonPath("$.buildQueue").isEmpty())
                .andExpect(jsonPath("$.unitsHome").isEmpty());
    }

    @Test
    void resourcesAreCaughtUpToNow() throws Exception {
        clock.advance(Duration.ofHours(2));

        browser.get(path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources.wood", closeTo(560, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(560, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(560, EPSILON)))
                .andExpect(jsonPath("$.resources.at").value(clock.instant().toString()));
    }

    @Test
    void productionStopsAtTheCapacityButNeverReducesStock() throws Exception {
        jdbc.sql("update village set wood = ?, clay = ? where id = ?")
                .params(990.0, 1500.0, villageId)
                .update();
        clock.advance(Duration.ofHours(2));

        browser.get(path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources.wood", closeTo(1000, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(1500, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(560, EPSILON)));
    }

    @Test
    void foreignAndUnknownVillagesAreNotFound() throws Exception {
        Browser other = Browser.open(mockMvc);
        other.register(Browser.uniqueUsername());

        other.get(path)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.patch(path, "{\"name\":\"Mine now\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        browser.get("/api/villages/" + Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    @Test
    void villagesNeedLogin() throws Exception {
        Browser.open(mockMvc).get(path).andExpect(status().isUnauthorized());
    }

    @Test
    void renameTrimsTheName() throws Exception {
        browser.patch(path, "{\"name\":\"  Bannerburg  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bannerburg"));

        browser.get(path).andExpect(jsonPath("$.name").value("Bannerburg"));
        browser.get("/api/me").andExpect(jsonPath("$.villages[0].name").value("Bannerburg"));
    }

    @Test
    void renameAcceptsThirtyTwoCharacters() throws Exception {
        String name = "abcdefghijklmnopqrstuvwxyzABCDEF";

        browser.patch(path, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "abcdefghijklmnopqrstuvwxyzABCDEFG", // 33
                "Tab\\there" // JSON escape: a real tab character
            })
    void renameRejectsInvalidNames(String name) throws Exception {
        browser.patch(path, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void renameWithoutNameIsRejected() throws Exception {
        browser.patch(path, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
}
