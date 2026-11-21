package de.bannerfall.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The tests run as a solo world on a PC ({@code bannerfall.host-check.domain} = {@code
 * http://localhost:8461} in {@link IntegrationTest}), so every other test goes through the check
 * too (MockMvc and the WebSocket tests use localhost).
 */
@IntegrationTest
class HostCheckFlowTest {

    @Autowired MockMvc mockMvc;

    @Test
    void theWorldAnswersAtItsOwnAddress() throws Exception {
        mockMvc.perform(get("/api/ping").header(HttpHeaders.HOST, "localhost:8461"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header(HttpHeaders.HOST, "127.0.0.1:8461"))
                .andExpect(status().isOk());
    }

    @Test
    void aRebindingNameIsRefusedBeforeAnythingElse() throws Exception {
        mockMvc.perform(get("/api/ping").header(HttpHeaders.HOST, "evil.example:8461"))
                .andExpect(status().is(421));
        // before Spring Security and CSRF: no 401 or 403, the request never gets that far
        mockMvc.perform(
                        post("/api/auth/register")
                                .header(HttpHeaders.HOST, "evil.example:8461")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().is(421));
        mockMvc.perform(get("/").header(HttpHeaders.HOST, "evil.example:8461"))
                .andExpect(status().is(421));
    }
}
