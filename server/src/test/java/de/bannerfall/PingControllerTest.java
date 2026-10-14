package de.bannerfall;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PingControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void pingIsPublicAndReturnsServerTime() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.serverTime").isString());
    }

    @Test
    void otherApiPathsNeedLogin() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
