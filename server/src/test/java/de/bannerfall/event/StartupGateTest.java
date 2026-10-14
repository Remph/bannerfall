package de.bannerfall.event;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Code review 1.2, D3: after a start, game requests wait until the worker has caught up with what
 * came due during the downtime; the account, the chat and the world's settings stay open.
 */
@IntegrationTest
class StartupGateTest {

    @Autowired MockMvc mockMvc;
    @Autowired StartupGate gate;
    @Autowired EventProcessor processor;

    @AfterEach
    void open() {
        gate.open();
    }

    @Test
    void gameRequestsWaitUntilTheWorkerHasCaughtUp() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        gate.close();

        player.browser
                .get(player.villagePath())
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("SERVER_STARTING"));
        player.browser.get("/api/me").andExpect(status().isOk());
        player.browser.get("/api/world").andExpect(status().isOk());

        processor.tick();

        player.browser.get(player.villagePath()).andExpect(status().isOk());
    }

    @Test
    void aVisitorWithoutLoginStillGets401() throws Exception {
        gate.close();

        new Browser(mockMvc).get("/api/villages/1").andExpect(status().isUnauthorized());
    }
}
