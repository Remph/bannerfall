package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/world/operator}: who runs the world, for the privacy page that guests open from
 * the login and register pages, so it needs no login. The test world's host is set in {@link
 * IntegrationTest}.
 */
@IntegrationTest
class WorldOperatorTest {

    @Autowired MockMvc mockMvc;

    @Test
    void guestsSeeTheHostFromTheSettings() throws Exception {
        mockMvc.perform(get("/api/world/operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(IntegrationTest.OPERATOR_NAME))
                .andExpect(jsonPath("$.contact").value(IntegrationTest.OPERATOR_CONTACT));
    }

    @Test
    void blankSettingsCountAsNotSetAndValuesAreTrimmed() {
        assertThat(new WorldOperator("", "   ").get()).isEqualTo(new OperatorDto(null, null));
        assertThat(new WorldOperator("  A Host  ", "host@example.com\n").get())
                .isEqualTo(new OperatorDto("A Host", "host@example.com"));
    }
}
