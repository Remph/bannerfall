package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.TestWorldConfiguration.TestWorld;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class WorldServiceTest {

    @Autowired WorldService worldService;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired TestWorld testWorld;

    @Test
    void theMigrationSeedsTheRulebookDefaults() {
        worldService.reload();

        // TestWorldConfiguration changed some values after the migration and remembers the seed
        WorldSettings defaults = WorldSettings.defaults();
        assertThat(worldService.settings())
                .usingRecursiveComparison()
                .ignoringFields("barbariansPerNewPlayer", "npcLordCount", "mapSize")
                .isEqualTo(defaults);
        assertThat(testWorld.seededBarbariansPerNewPlayer())
                .isEqualTo(defaults.barbariansPerNewPlayer());
        assertThat(worldService.settings().npcLordCount())
                .isZero(); // set by TestWorldConfiguration
        assertThat(worldService.settings().mapSize())
                .isEqualTo(TestWorldConfiguration.MAP_SIZE); // set by TestWorldConfiguration
        assertThat(worldService.startedAt()).isNotNull();
    }

    @Test
    void worldEndpointShowsThePublicSettings() throws Exception {
        Browser browser = Browser.open(mockMvc);
        browser.register(Browser.uniqueUsername());

        browser.get("/api/world")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bannerfall 1"))
                .andExpect(jsonPath("$.speed").value(1.0))
                .andExpect(jsonPath("$.unitSpeed").value(1.0))
                .andExpect(jsonPath("$.mapSize").value(TestWorldConfiguration.MAP_SIZE))
                .andExpect(jsonPath("$.buildQueueSize").value(5))
                .andExpect(jsonPath("$.buildQueueMax").value(10))
                .andExpect(jsonPath("$.recruitQueueSize").value(5))
                .andExpect(jsonPath("$.startedAt").isString())
                // the release of the build (M28): the tag next to the server time
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
    }

    @Test
    void worldEndpointNeedsLogin() throws Exception {
        Browser.open(mockMvc).get("/api/world").andExpect(status().isUnauthorized());
    }

    @Test
    void registrationUsesTheInviteCodeFromTheTable() throws Exception {
        setInviteCode("friends-only");
        try {
            Browser.open(mockMvc)
                    .register(Browser.uniqueUsername(), Browser.PASSWORD, Browser.INVITE_CODE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
            Browser.open(mockMvc)
                    .register(Browser.uniqueUsername(), Browser.PASSWORD, "friends-only")
                    .andExpect(status().isCreated());
        } finally {
            setInviteCode(Browser.INVITE_CODE); // the context (and database) is shared
        }
    }

    private void setInviteCode(String code) {
        jdbc.sql("update world_settings set invite_code = ? where id = 1").param(code).update();
        worldService.reload();
    }
}
