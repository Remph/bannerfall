package de.bannerfall.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guided world setup (1.6, 08-host-console §5): the one-time setup code that makes the first
 * admin, and the end of the wizard. The shared test world is put into the pre-game only by flag
 * (nothing here opens it) and back afterwards.
 */
@IntegrationTest
class WorldSetupFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;

    private Host host;
    private Instant clockBefore;
    private Timestamp initializedBefore;

    @BeforeEach
    void enterPregame() {
        host = new Host(mockMvc);
        clockBefore = clock.instant();
        // the phase is PREGAME only while the content doesn't exist (02-game-rules §1b)
        initializedBefore =
                jdbc.sql("select initialized_at from world_settings")
                        .query((rs, row) -> Optional.ofNullable(rs.getTimestamp(1)))
                        .single()
                        .orElse(null);
        jdbc.sql(
                        "update world_settings set pregame = true, setup_done = false,"
                                + " initialized_at = null")
                .update();
        worldService.reload();
    }

    @AfterEach
    void backToNormal() {
        reopen();
        jdbc.sql("update world_settings set setup_done = false").update();
        clock.set(clockBefore);
    }

    private void reopen() {
        jdbc.sql("update world_settings set pregame = false, initialized_at = ?")
                .param(initializedBefore)
                .update();
        worldService.reload();
    }

    @Test
    void theSetupCodeMakesTheLoggedInAccountAnAdminOnce() throws Exception {
        String code = newCode();
        assertThat(code).hasSize(SetupCodeService.CODE_LENGTH);
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername()).andExpect(status().isCreated());
        player.get("/api/admin/status").andExpect(status().isForbidden());

        player.post("/api/me/setup-claim", "{\"code\":\"wrong-code\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETUP_CODE_INVALID"));
        // case and spaces don't matter: the host may copy it from a terminal
        player.post("/api/me/setup-claim", "{\"code\":\" " + code.toUpperCase() + " \"}")
                .andExpect(status().isNoContent());

        player.get("/api/admin/status").andExpect(status().isOk());
        player.get("/api/me").andExpect(jsonPath("$.role").value("ADMIN"));
        host.get("/api/admin/audit?limit=1")
                .andExpect(jsonPath("$[0].action").value("SETUP_CLAIM"))
                .andExpect(jsonPath("$[0].details.to").value("ADMIN"));

        // used up
        Browser other = Browser.open(mockMvc);
        other.register(Browser.uniqueUsername()).andExpect(status().isCreated());
        other.post("/api/me/setup-claim", "{\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETUP_CODE_INVALID"));
        demote(player);
    }

    @Test
    void aSetupCodeExpiresAndANewOneReplacesTheOldOne() throws Exception {
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername()).andExpect(status().isCreated());

        String first = newCode();
        String second = newCode();
        player.post("/api/me/setup-claim", "{\"code\":\"" + first + "\"}")
                .andExpect(status().isBadRequest());

        clock.advance(SetupCodeService.LIFETIME);
        player.post("/api/me/setup-claim", "{\"code\":\"" + second + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETUP_CODE_INVALID"));
        player.get("/api/me").andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void theAuditLogNeverShowsTheCode() throws Exception {
        String code = newCode();
        host.get("/api/admin/audit?limit=1")
                .andExpect(jsonPath("$[0].action").value("SETUP_CODE"))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .doesNotContain(code));
    }

    @Test
    void finishingOrSkippingTheWizardIsStored() throws Exception {
        host.get("/api/admin/world-settings").andExpect(jsonPath("$.setupDone").value(false));
        host.post("/api/admin/world-setup/done", "{\"finished\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupDone").value(true));
        host.get("/api/admin/world-settings").andExpect(jsonPath("$.setupDone").value(true));
        host.get("/api/admin/audit?limit=1")
                .andExpect(jsonPath("$[0].action").value("WORLD_SETUP"))
                .andExpect(jsonPath("$[0].details.finished").value(false));
    }

    @Test
    void anOpenWorldHasNoSetup() throws Exception {
        reopen();

        host.post("/api/admin/setup-code", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ALREADY_OPEN"));
        host.post("/api/admin/world-setup/done", "{\"finished\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ALREADY_OPEN"));
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername()).andExpect(status().isCreated());
        player.post("/api/me/setup-claim", "{\"code\":\"abcdefghjk\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ALREADY_OPEN"));
    }

    private String newCode() throws Exception {
        String body =
                host.post("/api/admin/setup-code", "")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.expiresAt").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private void demote(Browser player) throws Exception {
        String name =
                JsonPath.read(
                        player.get("/api/me").andReturn().getResponse().getContentAsString(),
                        "$.username");
        jdbc.sql("update account set role = 'PLAYER' where username = ?").param(name).update();
    }
}
