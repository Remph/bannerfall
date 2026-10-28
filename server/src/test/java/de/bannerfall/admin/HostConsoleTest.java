package de.bannerfall.admin;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The host console's server side (08-host-console §9): the host token, status, players, role
 * change, password reset, broadcast, the audit log, the cheat switch (off here; on in {@code
 * DevCheatControllerTest}) and the player's own password change.
 */
@IntegrationTest
class HostConsoleTest {

    private static final String TOKEN = IntegrationTest.ADMIN_TOKEN;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired AdminTokenLimiter limiter;
    @Autowired MutableClock clock;
    @Autowired SentMessages sent;

    @AfterEach
    void unblockTheToken() {
        limiter.clear();
    }

    private ResultActions host(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + TOKEN));
    }

    private ResultActions hostPost(String path, String json) throws Exception {
        return host(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String lastAuditActor(String action) {
        return jdbc.sql("select actor from admin_action where action = ? order by id desc limit 1")
                .param(action)
                .query(String.class)
                .single();
    }

    // ---- the host token ----

    @Test
    void theRightTokenIsAdminWithoutSessionOrCsrf() throws Exception {
        host(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseOk").value(true))
                .andExpect(jsonPath("$.cheatsEnabled").value(false));
        // a POST with neither cookie nor CSRF header
        hostPost("/api/admin/broadcast", "{\"text\":\"Restart in 5 minutes\"}")
                .andExpect(status().isCreated());
        assertThat(lastAuditActor("BROADCAST")).isEqualTo(AdminActor.HOST_CLI);
    }

    @Test
    void aWrongOrMissingTokenIs401AndOnlyForAdminPaths() throws Exception {
        mockMvc.perform(get("/api/admin/status").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/admin/status")).andExpect(status().isUnauthorized());
        // the token is no key to the game's own endpoints
        host(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSixthWrongTokenWithinAMinuteIs429EvenForTheRightOne() throws Exception {
        for (int i = 0; i < AdminTokenLimiter.MAX_FAILURES; i++) {
            mockMvc.perform(get("/api/admin/status").header("Authorization", "Bearer wrong" + i))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/admin/status").header("Authorization", "Bearer wrong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ADMIN_ATTEMPTS"));
        host(get("/api/admin/status")).andExpect(status().isTooManyRequests());

        // another address (the CLI on localhost behind a proxy that is being guessed at) still
        // works
        host(get("/api/admin/status").with(fromAddress("10.0.0.7"))).andExpect(status().isOk());

        clock.advance(AdminTokenLimiter.BLOCK_DURATION.plus(Duration.ofSeconds(1)));
        host(get("/api/admin/status")).andExpect(status().isOk());
    }

    private static RequestPostProcessor fromAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @Test
    void aBearerHeaderNeverUsesTheSessionOfAnAdmin() throws Exception {
        Browser admin = newAdmin();
        // the session alone works ...
        admin.get("/api/admin/status").andExpect(status().isOk());
        // ... but a wrong token next to it doesn't ride on it
        mockMvc.perform(
                        get("/api/admin/status")
                                .session(admin.session())
                                .header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPlayerSessionIsForbiddenOnTheNewEndpoints() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);

        player.browser.get("/api/admin/status").andExpect(status().isForbidden());
        player.browser.get("/api/admin/players").andExpect(status().isForbidden());
        player.browser.get("/api/admin/audit").andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/players/" + player.playerId + "/role", "{\"role\":\"ADMIN\"}")
                .andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/players/" + player.playerId + "/reset-password", "{}")
                .andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/broadcast", "{\"text\":\"x\"}")
                .andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/chat", "{\"text\":\"x\"}")
                .andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/barbarians", "{\"x\":1,\"y\":1}")
                .andExpect(status().isForbidden());
    }

    // ---- status ----

    @Test
    void theStatusCountsWhatIsThere() throws Exception {
        TestPlayer.register(mockMvc);
        long humans =
                jdbc.sql(
                                "select count(*) from player p join account a on a.id ="
                                        + " p.account_id where not a.disabled")
                        .query(Long.class)
                        .single();
        long villages = jdbc.sql("select count(*) from village").query(Long.class).single();

        host(get("/api/admin/status"))
                .andExpect(jsonPath("$.humanPlayers").value(humans))
                .andExpect(jsonPath("$.villages").value(villages))
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.uptimeSeconds").isNumber())
                .andExpect(jsonPath("$.events.pending").isNumber())
                .andExpect(jsonPath("$.events.lagSeconds").isNumber());
    }

    // ---- players and roles ----

    @Test
    void thePlayersListShowsRoleLastLoginAndVillages() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        String name = name(player);

        String path = "$[?(@.playerId == " + player.playerId + ")]";
        host(get("/api/admin/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".name").value(name))
                .andExpect(jsonPath(path + ".role").value("PLAYER"))
                .andExpect(jsonPath(path + ".deleted").value(false))
                // registering logs in, and counts as one
                .andExpect(jsonPath(path + ".lastLoginAt").value(clock.instant().toString()))
                .andExpect(jsonPath(path + ".villages[0].id").value((int) player.villageId));
    }

    @Test
    void aPromotedPlayerIsAdminAtOnceAndADemotedOneIsNotAnyMore() throws Exception {
        newAdmin(); // so he isn't the last admin when he is demoted
        TestPlayer player = TestPlayer.register(mockMvc);
        player.browser.get("/api/admin/status").andExpect(status().isForbidden());

        hostPost("/api/admin/players/" + player.playerId + "/role", "{\"role\":\"ADMIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // same session, no new login: the role is read per admin request
        player.browser.get("/api/admin/status").andExpect(status().isOk());
        player.browser.get("/api/me").andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(lastAuditActor("CHANGE_ROLE")).isEqualTo(AdminActor.HOST_CLI);

        hostPost("/api/admin/players/" + player.playerId + "/role", "{\"role\":\"PLAYER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"));
        player.browser.get("/api/admin/status").andExpect(status().isForbidden());
    }

    @Test
    void theLastAdminCannotStopBeingOne() throws Exception {
        List<Long> otherAdmins =
                jdbc.sql("select id from account where role = 'ADMIN'").query(Long.class).list();
        jdbc.sql("update account set role = 'PLAYER' where role = 'ADMIN'").update();
        try {
            TestPlayer only = TestPlayer.register(mockMvc);
            hostPost("/api/admin/players/" + only.playerId + "/role", "{\"role\":\"ADMIN\"}")
                    .andExpect(status().isOk());

            hostPost("/api/admin/players/" + only.playerId + "/role", "{\"role\":\"PLAYER\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
            // the admin himself can't either
            only.browser
                    .post("/api/admin/players/" + only.playerId + "/role", "{\"role\":\"PLAYER\"}")
                    .andExpect(status().isConflict());
            // nor can the host delete him (M14d review)
            hostPost("/api/admin/players/" + only.playerId + "/delete", "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

            // with a second admin it works
            TestPlayer second = TestPlayer.register(mockMvc);
            hostPost("/api/admin/players/" + second.playerId + "/role", "{\"role\":\"ADMIN\"}")
                    .andExpect(status().isOk());
            hostPost("/api/admin/players/" + only.playerId + "/role", "{\"role\":\"PLAYER\"}")
                    .andExpect(status().isOk());
        } finally {
            for (long id : otherAdmins) {
                jdbc.sql("update account set role = 'ADMIN' where id = ?").param(id).update();
            }
        }
    }

    @Test
    void rolesAndPasswordsOfNpcLordsAndUnknownPlayersAre404() throws Exception {
        long npc =
                jdbc.sql(
                                "insert into player (name, npc, points, village_count,"
                                        + " protected_until, created_at, version) values (?, true,"
                                        + " 0, 0, now(), now(), 0) returning id")
                        .param("Npc" + System.nanoTime() % 100_000_000)
                        .query(Long.class)
                        .single();
        try {
            hostPost("/api/admin/players/" + npc + "/role", "{\"role\":\"ADMIN\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
            hostPost("/api/admin/players/" + npc + "/reset-password", "{}")
                    .andExpect(status().isNotFound());
        } finally {
            // the NPC tests count the lords
            jdbc.sql("delete from player where id = ?").param(npc).update();
        }
        hostPost("/api/admin/players/999999999/reset-password", "{}")
                .andExpect(status().isNotFound());
        hostPost("/api/admin/players/1/role", "{\"role\":\"KING\"}")
                .andExpect(status().isBadRequest());
    }

    // ---- password reset ----

    @Test
    void aGeneratedPasswordIsShownOnceAndLogsIn() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        String name = name(player);

        String answer =
                body(
                        hostPost("/api/admin/players/" + player.playerId + "/reset-password", "")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(name)));
        String generated = JsonPath.read(answer, "$.generatedPassword");
        assertThat(generated)
                .hasSize(AdminPlayerService.GENERATED_PASSWORD_LENGTH)
                .matches("[a-zA-Z2-9]+");

        Browser browser = Browser.open(mockMvc);
        browser.login(name, Browser.PASSWORD).andExpect(status().isUnauthorized());
        browser.login(name, generated).andExpect(status().isOk());
        String details =
                jdbc.sql(
                                "select details::text from admin_action where action ="
                                        + " 'RESET_PASSWORD' order by id desc limit 1")
                        .query(String.class)
                        .single();
        assertThat(details).doesNotContain(generated).contains("\"generated\": true");
    }

    @Test
    void theHostCanGiveThePasswordAndItLiftsALoginBlock() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        String name = name(player);
        Browser friend = Browser.open(mockMvc);
        for (int i = 0; i < 5; i++) {
            friend.login(name, "forgotten " + i).andExpect(status().isUnauthorized());
        }
        friend.login(name, Browser.PASSWORD).andExpect(status().isTooManyRequests());

        hostPost(
                        "/api/admin/players/" + player.playerId + "/reset-password",
                        "{\"password\":\"summer holidays\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedPassword").doesNotExist());

        friend.login(name, "summer holidays").andExpect(status().isOk());
    }

    @Test
    void aGivenPasswordFollowsTheRegistrationRules() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        hostPost(
                        "/api/admin/players/" + player.playerId + "/reset-password",
                        "{\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void theHostMayGiveACommonPasswordAndTheAccountLogsInWithIt() throws Exception {
        // the common-password rule is for passwords players choose; an account that already has a
        // weak one (set before the rule, or by the host) must keep logging in
        TestPlayer player = TestPlayer.register(mockMvc);

        hostPost(
                        "/api/admin/players/" + player.playerId + "/reset-password",
                        "{\"password\":\"password123\"}")
                .andExpect(status().isOk());

        Browser.open(mockMvc).login(name(player), "password123").andExpect(status().isOk());
    }

    // ---- the player's own password ----

    @Test
    void aPlayerChangesHisOwnPassword() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        String name = name(player);

        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\"wrong one\",\"newPassword\":\"brand new pw\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\"" + Browser.PASSWORD + "\",\"newPassword\":\"x\"}")
                .andExpect(status().isBadRequest());
        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\""
                                + Browser.PASSWORD
                                + "\",\"newPassword\":\"brand new pw\"}")
                .andExpect(status().isNoContent());

        Browser other = Browser.open(mockMvc);
        other.login(name, Browser.PASSWORD).andExpect(status().isUnauthorized());
        other.login(name, "brand new pw").andExpect(status().isOk());
    }

    @Test
    void guessingTheCurrentPasswordIsLimitedLikeTheLogin() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        for (int i = 0; i < 5; i++) {
            player.browser
                    .post(
                            "/api/me/password",
                            "{\"currentPassword\":\"guess "
                                    + i
                                    + "\",\"newPassword\":\"newpassword\"}")
                    .andExpect(status().isConflict());
        }
        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\""
                                + Browser.PASSWORD
                                + "\",\"newPassword\":\"newpassword\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    // ---- broadcast and barbarians ----

    @Test
    void aBroadcastIsAPopupAndNotInTheChat() throws Exception {
        int chatBefore = sent.chat().size();
        int popupsBefore = sent.broadcasts().size();
        String answer =
                body(
                        hostPost("/api/admin/broadcast", "{\"text\":\"  Restart at 22:00 \"}")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.text").value("Restart at 22:00")));
        long id = JsonPath.<Number>read(answer, "$.id").longValue();

        assertThat(sent.chat()).hasSize(chatBefore);
        assertThat(sent.broadcasts().subList(popupsBefore, sent.broadcasts().size()))
                .singleElement()
                .satisfies(
                        json -> {
                            assertThat(JsonPath.<Number>read(json, "$.id").longValue())
                                    .isEqualTo(id);
                            assertThat(JsonPath.<String>read(json, "$.text"))
                                    .isEqualTo("Restart at 22:00");
                        });
        TestPlayer player = TestPlayer.register(mockMvc);
        player.browser
                .get("/api/chat/messages")
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.text == 'Restart at 22:00')]").isEmpty());
        // a player who connects later still gets it
        player.browser
                .get("/api/broadcasts")
                .andExpect(jsonPath("$[-1:].text").value("Restart at 22:00"));
        assertThat(lastAuditActor("BROADCAST")).isEqualTo(AdminActor.HOST_CLI);
        hostPost("/api/admin/broadcast", "{\"text\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void chatSayIsAChatMessageWithoutAuthor() throws Exception {
        int chatBefore = sent.chat().size();
        int popupsBefore = sent.broadcasts().size();
        String answer =
                body(
                        hostPost("/api/admin/chat", "{\"text\":\"  Welcome to the world \"}")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.text").value("Welcome to the world"))
                                .andExpect(jsonPath("$.playerId").isEmpty()));
        int id = JsonPath.read(answer, "$.id");

        assertThat(sent.broadcasts()).hasSize(popupsBefore);
        assertThat(sent.chat().subList(chatBefore, sent.chat().size()))
                .singleElement()
                .satisfies(
                        json -> assertThat(JsonPath.<Object>read(json, "$.playerName")).isNull());
        TestPlayer.register(mockMvc)
                .browser
                .get("/api/chat/messages")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].text").value("Welcome to the world"));
        assertThat(lastAuditActor("CHAT_SAY")).isEqualTo(AdminActor.HOST_CLI);
        hostPost("/api/admin/chat", "{\"text\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void theHostSpawnsABarbarianVillage() throws Exception {
        // on the bottom edge, away from the corners the map and barbarian tests use (01 §5); it
        // stays (its home stack keeps it from being deleted)
        hostPost(
                        "/api/admin/barbarians",
                        "{\"x\":50,\"y\":" + (TestWorldConfiguration.MAP_SIZE - 1) + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").isEmpty());
        hostPost(
                        "/api/admin/barbarians",
                        "{\"x\":50,\"y\":" + (TestWorldConfiguration.MAP_SIZE - 1) + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_NOT_FREE"));
    }

    // ---- cheats ----

    @Test
    void theHostCheatsAre404WhileTheSwitchIsOff() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        hostPost(
                        "/api/admin/cheats/villages/" + player.villageId + "/resources",
                        "{\"wood\":1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        hostPost("/api/admin/cheats/villages/" + player.villageId + "/finish-orders", "")
                .andExpect(status().isNotFound());
    }

    // ---- audit ----

    @Test
    void everyAdminActionIsAuditedWithItsActor() throws Exception {
        Browser admin = newAdmin();
        String adminName = JsonPath.read(body(admin.get("/api/me")), "$.username");
        TestPlayer victim = TestPlayer.register(mockMvc);

        admin.post("/api/admin/broadcast", "{\"text\":\"from the page\"}")
                .andExpect(status().isCreated());
        assertThat(lastAuditActor("BROADCAST")).isEqualTo(adminName);

        hostPost("/api/admin/players/" + victim.playerId + "/delete", "")
                .andExpect(status().isOk());
        assertThat(lastAuditActor("DELETE_PLAYER")).isEqualTo(AdminActor.HOST_CLI);

        host(get("/api/admin/audit?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("DELETE_PLAYER"))
                .andExpect(jsonPath("$[0].actor").value(AdminActor.HOST_CLI))
                .andExpect(jsonPath("$[0].targetType").value("player"))
                .andExpect(jsonPath("$[0].targetId").value((int) victim.playerId))
                .andExpect(jsonPath("$[0].details.villagesConverted").value(1))
                .andExpect(jsonPath("$[1].action").value("BROADCAST"))
                .andExpect(jsonPath("$[1].actor").value(adminName));
        host(get("/api/admin/audit?limit=0")).andExpect(status().isBadRequest());
        host(get("/api/admin/audit?limit=501")).andExpect(status().isBadRequest());
    }

    @Test
    void aSettingsChangeIsAuditedWithWhatChanged() throws Exception {
        String settings = body(host(get("/api/admin/world-settings")));
        String name = JsonPath.read(settings, "$.name");
        String changed = settings.replace("\"name\":\"" + name + "\"", "\"name\":\"Audited\"");
        try {
            host(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                    "/api/admin/world-settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changed))
                    .andExpect(status().isOk());
            host(get("/api/admin/audit?limit=1"))
                    .andExpect(jsonPath("$[0].action").value("WORLD_SETTINGS"))
                    .andExpect(jsonPath("$[0].details.name[0]").value(name))
                    .andExpect(jsonPath("$[0].details.name[1]").value("Audited"))
                    .andExpect(jsonPath("$[0].details.length()").value(1));
        } finally {
            host(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                    "/api/admin/world-settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(settings));
        }
    }

    @Test
    void theAuditListIsNewestFirst() throws Exception {
        hostPost("/api/admin/broadcast", "{\"text\":\"one\"}");
        hostPost("/api/admin/broadcast", "{\"text\":\"two\"}");
        host(get("/api/admin/audit"))
                .andExpect(jsonPath("$[0].details.text").value("two"))
                .andExpect(jsonPath("$[1].details.text").value("one"))
                .andExpect(jsonPath("$[*].action", hasItem("BROADCAST")));
    }

    // ---- helpers ----

    private Browser newAdmin() throws Exception {
        Browser admin = Browser.open(mockMvc);
        String name = Browser.uniqueUsername();
        admin.register(name);
        jdbc.sql("update account set role = 'ADMIN' where username = ?").param(name).update();
        return admin;
    }

    private String name(TestPlayer player) {
        return jdbc.sql("select name from player where id = ?")
                .param(player.playerId)
                .query(String.class)
                .single();
    }
}
