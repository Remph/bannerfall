package de.bannerfall.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.common.AppVersion;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ServerTimeFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired AppVersion version;

    @Test
    void everyResponseCarriesTheServerTime() throws Exception {
        String now = clock.instant().truncatedTo(ChronoUnit.MILLIS).toString();

        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string(ServerTimeFilter.HEADER, now));
        // also when Spring Security answers
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(ServerTimeFilter.HEADER, now));
    }

    @Test
    void everyResponseCarriesTheServerVersion() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string(ServerTimeFilter.VERSION_HEADER, version.get()));
        // a page left open across an update gets logged out by the restart: the 401 tells it too
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(ServerTimeFilter.VERSION_HEADER, version.get()));
    }
}
