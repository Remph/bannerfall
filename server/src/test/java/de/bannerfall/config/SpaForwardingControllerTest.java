package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SpaForwardingControllerTest {

    @Autowired MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/login",
                "/ranking",
                "/players/7",
                "/v/12",
                "/v/12/hq",
                "/v/12/recruit/barracks",
                "/some/deep/unknown/path/x"
            })
    void routesOfTheWebAppGetIndexHtml(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/nothing-here",
                "/api/v1/deeper/nothing",
                "/ws",
                "/actuator",
                "/actuator/health/liveness",
                "/v3/api-docs",
                "/assets/app.js",
                "/manifest.webmanifest",
                "/favicon.svg"
            })
    void serverAreasAndFilesAreNotForwarded(String path) throws Exception {
        String forwarded = mockMvc.perform(get(path)).andReturn().getResponse().getForwardedUrl();
        assertThat(forwarded).isNull();
    }

    @Test
    void unknownApiPathsStayJsonErrors() throws Exception {
        // not logged in: the API says 401, it does not serve the web app
        mockMvc.perform(get("/api/nothing-here")).andExpect(status().isUnauthorized());
    }
}
