package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M19: {@code GET /api/world/status} for the landing page bannerfall.de: the player counts and the
 * one cross-origin door ({@code StatusCors}). The online count with real connections is in {@code
 * ConnectionsTest}.
 */
@IntegrationTest
class PublicStatusTest {

    private static final String LANDING_PAGE = "https://bannerfall.de";

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;

    @Test
    void theStatusCountsRegisteredPlayersAndNothingElse() throws Exception {
        int before = registered();

        Browser.open(mockMvc).register(Browser.uniqueUsername());

        assertThat(registered()).isEqualTo(before + 1);
        mockMvc.perform(get("/api/world/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlinePlayers").isNumber())
                // the release of the build (M28), also for the login page: no secret in a version
                .andExpect(jsonPath("$.version").isString())
                // counts only: no names, no ids
                .andExpect(jsonPath("$.players").doesNotExist())
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
    }

    @Test
    void theRegisteredCountComesFromMemoryForAFewSecondsSoThePublicPathCostsNoQueries()
            throws Exception {
        int before = registered();

        Browser.open(mockMvc).register(Browser.uniqueUsername());

        // anybody may ask as often as they like: the database is asked once per MAX_AGE
        clock.advance(RegisteredPlayersCount.MAX_AGE.dividedBy(2));
        assertThat(currentRegistered()).isEqualTo(before);
        clock.advance(RegisteredPlayersCount.MAX_AGE);
        assertThat(currentRegistered()).isEqualTo(before + 1);
    }

    @Test
    void theLandingPageMayReadTheStatusFromItsOrigin() throws Exception {
        mockMvc.perform(get("/api/world/status").header("Origin", LANDING_PAGE))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", LANDING_PAGE))
                // no cookies cross over
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void thePreflightAllowsGetOnly() throws Exception {
        mockMvc.perform(
                        options("/api/world/status")
                                .header("Origin", LANDING_PAGE)
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"));
        mockMvc.perform(
                        options("/api/world/status")
                                .header("Origin", LANDING_PAGE)
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherSiteGetsNoAnswerForTheBrowser() throws Exception {
        mockMvc.perform(get("/api/world/status").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void everyOtherPathStaysSameOrigin() throws Exception {
        mockMvc.perform(get("/api/ping").header("Origin", LANDING_PAGE))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(
                        options("/api/auth/login")
                                .header("Origin", LANDING_PAGE)
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    /** The count after the cache has expired, i.e. what the database says now. */
    private int registered() throws Exception {
        clock.advance(RegisteredPlayersCount.MAX_AGE);
        return currentRegistered();
    }

    private int currentRegistered() throws Exception {
        String body =
                mockMvc.perform(get("/api/world/status"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.<Number>read(body, "$.registeredPlayers").intValue();
    }
}
