package de.bannerfall.admin;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.chat.ChatService;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.world.AdminWorldSettingsDto;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettingsUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin endpoints of M14a (04-api *Admin*) and the ranking and profile endpoints: who may call
 * them, world settings, deleting a player, hiding a chat message, failed events.
 */
@IntegrationTest
class AdminFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired ChatService chatService;
    @Autowired SentMessages sent;

    private Browser admin;
    private AdminWorldSettingsDto originalSettings;

    @BeforeEach
    void setUp() throws Exception {
        originalSettings = worldService.adminSettings();
        admin = Browser.open(mockMvc);
        String name = Browser.uniqueUsername();
        admin.register(name);
        jdbc.sql("update account set role = 'ADMIN' where username = ?").param(name).update();
        admin.login(name, Browser.PASSWORD).andExpect(status().isOk());
    }

    @AfterEach
    void restoreSettings() {
        worldService.updateSettings(request(originalSettings));
        jdbc.sql("delete from game_event where kind = 'TEST_UNHANDLED' and status = 'FAILED'")
                .update();
    }

    // ---- access ----

    @Test
    void aPlayerGetsForbiddenOnEveryAdminEndpoint() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);

        player.browser.get("/api/admin/world-settings").andExpect(status().isForbidden());
        player.browser
                .put("/api/admin/world-settings", settingsJson("Hacked", 1, 5, 10, "x"))
                .andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/players/" + player.playerId + "/delete", "")
                .andExpect(status().isForbidden());
        player.browser.delete("/api/admin/chat/1").andExpect(status().isForbidden());
        player.browser.get("/api/admin/events/failed").andExpect(status().isForbidden());
        player.browser.post("/api/admin/events/1/retry", "").andExpect(status().isForbidden());
    }

    // ---- world settings ----

    @Test
    void worldSettingsAreReadChangedAndUsedAtOnce() throws Exception {
        admin.get("/api/admin/world-settings")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value(originalSettings.inviteCode()))
                .andExpect(jsonPath("$.mapSize").value(originalSettings.mapSize()))
                .andExpect(jsonPath("$.npcDifficulty").value("NORMAL"));

        admin.put("/api/admin/world-settings", settingsJson("Testland", 2.5, 4, 8, "new-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Testland"))
                .andExpect(jsonPath("$.speed").value(2.5))
                .andExpect(jsonPath("$.buildQueueSize").value(4))
                .andExpect(jsonPath("$.buildQueueMax").value(8));

        // the cached settings were reloaded: the public endpoint and the invite code follow
        admin.get("/api/world")
                .andExpect(jsonPath("$.name").value("Testland"))
                .andExpect(jsonPath("$.speed").value(2.5))
                .andExpect(jsonPath("$.buildQueueSize").value(4))
                .andExpect(jsonPath("$.buildQueueMax").value(8));
        Browser newcomer = Browser.open(mockMvc);
        newcomer.register(Browser.uniqueUsername(), Browser.PASSWORD, originalSettings.inviteCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
        newcomer.register(Browser.uniqueUsername(), Browser.PASSWORD, "new-code")
                .andExpect(status().isCreated());
        // what the request can't change stays
        assertThat(worldService.adminSettings().mapSize()).isEqualTo(originalSettings.mapSize());
    }

    @Test
    void worldSettingsOutOfRangeAreRefusedAndNothingChanges() throws Exception {
        // luckRange 2 is out of [0, 1] (the parked M8a item: it would make every battle fail)
        admin.put(
                        "/api/admin/world-settings",
                        settingsJson("X", 1, 5, 10, "c").replace("0.25", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("luckRange"));
        admin.put("/api/admin/world-settings", settingsJson("X", 0, 5, 10, "c"))
                .andExpect(status().isBadRequest());
        admin.put("/api/admin/world-settings", settingsJson("X", 1, 6, 5, "c"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        assertThat(worldService.settings().name()).isEqualTo(originalSettings.name());
    }

    @Test
    void villagesGetNamesInTheWorldsLanguageAndASwitchRenamesTheDefaultNames() throws Exception {
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.villageNameLanguage").value("EN"));
        TestPlayer ben = TestPlayer.register(mockMvc);
        String benName = playerName(ben);
        assertThat(villageName(ben.villageId)).isEqualTo(benName + "'s village");
        // a name the player gave stays whatever the language
        TestPlayer clara = TestPlayer.register(mockMvc);
        clara.browser
                .patch(clara.villagePath(), "{\"name\":\"Burg Clara\"}")
                .andExpect(status().isOk());
        // also one in the shape of a default name, but not around a player's name
        TestPlayer emil = TestPlayer.register(mockMvc);
        emil.browser
                .patch(emil.villagePath(), "{\"name\":\"Grandma's village\"}")
                .andExpect(status().isOk());

        // the invite code stays, so new players can still register
        admin.put("/api/admin/world-settings", keepingInvite(",\"villageNameLanguage\":\"DE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villageNameLanguage").value("DE"));

        assertThat(villageName(ben.villageId)).isEqualTo("Dorf von " + benName);
        assertThat(villageName(clara.villageId)).isEqualTo("Burg Clara");
        assertThat(villageName(emil.villageId)).isEqualTo("Grandma's village");
        TestPlayer dora = TestPlayer.register(mockMvc);
        assertThat(villageName(dora.villageId)).isEqualTo("Dorf von " + playerName(dora));
        // too long for the English pattern: left as it is
        String longName = "Dorf von " + "A".repeat(23);
        jdbc.sql("update village set name = ? where id = ?")
                .params(longName, dora.villageId)
                .update();

        // an older client that leaves it out keeps it; switching back renames back
        admin.put("/api/admin/world-settings", keepingInvite("}"))
                .andExpect(jsonPath("$.villageNameLanguage").value("DE"));
        assertThat(villageName(ben.villageId)).isEqualTo("Dorf von " + benName);
        admin.put("/api/admin/world-settings", keepingInvite(",\"villageNameLanguage\":\"EN\"}"))
                .andExpect(status().isOk());

        assertThat(villageName(ben.villageId)).isEqualTo(benName + "'s village");
        assertThat(villageName(clara.villageId)).isEqualTo("Burg Clara");
        assertThat(villageName(dora.villageId)).isEqualTo(longName);
    }

    @Test
    void unitLevelsAreOffByDefaultAndSwitchedOnAndOff() throws Exception {
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.unitLevelsEnabled").value(false));

        admin.put("/api/admin/world-settings", withExtra(",\"unitLevelsEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitLevelsEnabled").value(true));
        // the players see it, and an older client that leaves it out keeps it
        admin.get("/api/world").andExpect(jsonPath("$.unitLevelsEnabled").value(true));
        admin.put("/api/admin/world-settings", settingsJson("Testland", 1, 5, 10, "c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitLevelsEnabled").value(true));
        assertThat(worldService.settings().unitLevelsEnabled()).isTrue();

        admin.put("/api/admin/world-settings", withExtra(",\"unitLevelsEnabled\":false}"))
                .andExpect(jsonPath("$.unitLevelsEnabled").value(false));
        assertThat(worldService.settings().unitLevelsEnabled()).isFalse();
    }

    @Test
    void theBannerSettingsAreChangedKeptWhenLeftOutAndRangeChecked() throws Exception {
        // the defaults: banners work, drops at the normal chance
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.bannersEnabled").value(true))
                .andExpect(jsonPath("$.bannerDropFactor").value(1.0));

        admin.put(
                        "/api/admin/world-settings",
                        withExtra(",\"bannersEnabled\":false,\"bannerDropFactor\":0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannersEnabled").value(false))
                .andExpect(jsonPath("$.bannerDropFactor").value(0.5));
        // the players see that banners are off, not the drop factor's detail
        admin.get("/api/world").andExpect(jsonPath("$.bannersEnabled").value(false));
        assertThat(worldService.settings().bannersEnabled()).isFalse();
        // an older client that leaves them out keeps them
        admin.put("/api/admin/world-settings", settingsJson("Testland", 1, 5, 10, "c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannersEnabled").value(false))
                .andExpect(jsonPath("$.bannerDropFactor").value(0.5));

        // 0 switches drops off, 3 is the most
        admin.put("/api/admin/world-settings", withExtra(",\"bannerDropFactor\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerDropFactor").value(0.0));
        admin.put("/api/admin/world-settings", withExtra(",\"bannerDropFactor\":3.5}"))
                .andExpect(status().isBadRequest());
        admin.put("/api/admin/world-settings", withExtra(",\"bannerDropFactor\":-1}"))
                .andExpect(status().isBadRequest());
        assertThat(worldService.settings().bannerDropFactor()).isZero();
    }

    @Test
    void theMarketExchangeRatioIsChangedKeptWhenLeftOutAndRangeChecked() throws Exception {
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.marketExchangeRatio").value(1.5));

        admin.put("/api/admin/world-settings", withExtra(",\"marketExchangeRatio\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketExchangeRatio").value(2.0));
        // an older client that leaves it out keeps it
        admin.put("/api/admin/world-settings", settingsJson("Testland", 1, 5, 10, "c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketExchangeRatio").value(2.0));

        // 0 switches trading with the market off, 1 is the cheapest, 10 the dearest
        admin.put("/api/admin/world-settings", withExtra(",\"marketExchangeRatio\":0}"))
                .andExpect(status().isOk());
        admin.put("/api/admin/world-settings", withExtra(",\"marketExchangeRatio\":1}"))
                .andExpect(status().isOk());
        admin.put("/api/admin/world-settings", withExtra(",\"marketExchangeRatio\":0.5}"))
                .andExpect(status().isBadRequest());
        admin.put("/api/admin/world-settings", withExtra(",\"marketExchangeRatio\":10.5}"))
                .andExpect(status().isBadRequest());
        assertThat(worldService.settings().marketExchangeRatio()).isEqualTo(1.0);
    }

    @Test
    void thePaladinSettingsAreChangedKeptWhenLeftOutAndRangeChecked() throws Exception {
        // the defaults: a slot per 4 villages, 5 slots at most
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.paladinVillagesPerSlot").value(4))
                .andExpect(jsonPath("$.paladinMaxSlots").value(5));

        admin.put(
                        "/api/admin/world-settings",
                        withExtra(",\"paladinVillagesPerSlot\":6,\"paladinMaxSlots\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paladinVillagesPerSlot").value(6))
                .andExpect(jsonPath("$.paladinMaxSlots").value(3));
        // the players see them, and a client that leaves them out keeps them
        admin.get("/api/world")
                .andExpect(jsonPath("$.paladinVillagesPerSlot").value(6))
                .andExpect(jsonPath("$.paladinMaxSlots").value(3));
        admin.put("/api/admin/world-settings", settingsJson("Testland", 1, 5, 10, "c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paladinMaxSlots").value(3));
        assertThat(worldService.settings().paladinVillagesPerSlot()).isEqualTo(6);
        assertThat(worldService.settings().paladinMaxSlots()).isEqualTo(3);

        // 0 slots = no paladin in this world; out of range is refused and changes nothing
        admin.put("/api/admin/world-settings", withExtra(",\"paladinMaxSlots\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paladinMaxSlots").value(0));
        admin.put("/api/admin/world-settings", withExtra(",\"paladinVillagesPerSlot\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paladinVillagesPerSlot"));
        admin.put("/api/admin/world-settings", withExtra(",\"paladinMaxSlots\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paladinMaxSlots"));
        assertThat(worldService.settings().paladinVillagesPerSlot()).isEqualTo(6);
        assertThat(worldService.settings().paladinMaxSlots()).isZero();
    }

    @Test
    void theBarbarianWallSettingsAreChangedKeptWhenLeftOutAndRangeChecked() throws Exception {
        // a world keeps its behaviour: off, 3 days, level 5, no rebuild
        admin.get("/api/admin/world-settings")
                .andExpect(jsonPath("$.barbarianWallsEnabled").value(false))
                .andExpect(jsonPath("$.barbarianWallStartDays").value(3))
                .andExpect(jsonPath("$.barbarianWallMaxLevel").value(5))
                .andExpect(jsonPath("$.barbarianWallsRebuild").value(false));
        String walls =
                ",\"barbarianWallsEnabled\":true,\"barbarianWallStartDays\":7,"
                        + "\"barbarianWallMaxLevel\":10,\"barbarianWallsRebuild\":true}";

        admin.put("/api/admin/world-settings", withExtra(walls))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbarianWallsEnabled").value(true))
                .andExpect(jsonPath("$.barbarianWallStartDays").value(7))
                .andExpect(jsonPath("$.barbarianWallMaxLevel").value(10))
                .andExpect(jsonPath("$.barbarianWallsRebuild").value(true));
        // the players see them too (the world info of the pre-game page and the settings menu)
        admin.get("/api/world")
                .andExpect(jsonPath("$.barbarianWallsEnabled").value(true))
                .andExpect(jsonPath("$.barbarianWallStartDays").value(7))
                .andExpect(jsonPath("$.barbarianWallMaxLevel").value(10))
                .andExpect(jsonPath("$.barbarianWallsRebuild").value(true));
        assertThat(worldService.settings().barbarianWallsEnabled()).isTrue();

        // an older client that leaves them out keeps them
        admin.put("/api/admin/world-settings", settingsJson("Testland", 1, 5, 10, "c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbarianWallStartDays").value(7))
                .andExpect(jsonPath("$.barbarianWallsEnabled").value(true));

        // out of range: 0..365 days, level 0..20
        admin.put("/api/admin/world-settings", withExtra(",\"barbarianWallStartDays\":366}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("barbarianWallStartDays"));
        admin.put("/api/admin/world-settings", withExtra(",\"barbarianWallMaxLevel\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("barbarianWallMaxLevel"));
        assertThat(worldService.settings().barbarianWallMaxLevel()).isEqualTo(10);
    }

    // ---- ranking and profile ----

    @Test
    void theRankingIsSortedEqualPointsShareARankAndPagesWork() throws Exception {
        TestPlayer first = TestPlayer.register(mockMvc);
        TestPlayer secondA = TestPlayer.register(mockMvc);
        TestPlayer secondB = TestPlayer.register(mockMvc);
        int[] before = {points(first), points(secondA), points(secondB)};
        setPoints(first, 900_000);
        setPoints(secondA, 899_999);
        setPoints(secondB, 899_999);
        try {
            first.browser
                    .get("/api/ranking?size=3")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(3))
                    .andExpect(jsonPath("$.entries.length()").value(3))
                    .andExpect(jsonPath("$.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.entries[0].playerId").value(first.playerId))
                    .andExpect(jsonPath("$.entries[0].points").value(900_000))
                    .andExpect(jsonPath("$.entries[0].npc").value(false))
                    .andExpect(jsonPath("$.entries[0].villageCount").value(1))
                    .andExpect(jsonPath("$.entries[1].rank").value(2))
                    .andExpect(jsonPath("$.entries[2].rank").value(2))
                    .andExpect(jsonPath("$.entries[1].playerId").value(secondA.playerId))
                    .andExpect(jsonPath("$.entries[2].playerId").value(secondB.playerId));
            first.browser
                    .get("/api/ranking?size=1&page=1")
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.entries.length()").value(1))
                    .andExpect(jsonPath("$.entries[0].playerId").value(secondA.playerId));
            first.browser.get("/api/ranking?size=0").andExpect(status().isBadRequest());
            first.browser.get("/api/ranking?size=101").andExpect(status().isBadRequest());
        } finally {
            setPoints(first, before[0]);
            setPoints(secondA, before[1]);
            setPoints(secondB, before[2]);
        }
    }

    @Test
    void aProfileListsTheVillagesAndTheRankAndAnUnknownPlayerIs404() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        TestPlayer other = TestPlayer.register(mockMvc);

        other.browser
                .get("/api/players/" + player.playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.playerId))
                .andExpect(jsonPath("$.npc").value(false))
                .andExpect(jsonPath("$.rank").isNumber())
                .andExpect(jsonPath("$.villages.length()").value(1))
                .andExpect(jsonPath("$.villages[0].id").value(player.villageId))
                .andExpect(jsonPath("$.villages[0].incomingAttacks").doesNotExist());
        other.browser
                .get("/api/players/99999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    // ---- deleting a player ----

    @Test
    void deletingAPlayerTurnsHisVillagesBarbarianAndHeCannotComeBack() throws Exception {
        TestPlayer victim = TestPlayer.register(mockMvc);
        TestPlayer bystander = TestPlayer.register(mockMvc);

        admin.post("/api/admin/players/" + victim.playerId + "/delete", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(victim.playerId))
                .andExpect(jsonPath("$.villagesConverted").value(1));

        assertThat(
                        jdbc.sql("select player_id from village where id = ?")
                                .param(victim.villageId)
                                .query(Long.class)
                                .optional())
                .isEmpty(); // player_id is null: a barbarian village
        // gone from the ranking and the profiles
        bystander
                .browser
                .get("/api/players/" + victim.playerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        String ranking = body(bystander.browser.get("/api/ranking?size=100"));
        assertThat(JsonPath.<java.util.List<Number>>read(ranking, "$.entries[*].playerId"))
                .doesNotContain((Number) (int) victim.playerId);
        // his open session ends at its next request (M14e), and a new login is refused like a
        // wrong one
        victim.browser
                .post("/api/me/respawn", "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        victim.browser.get("/api/me").andExpect(status().isUnauthorized());
        String username =
                jdbc.sql(
                                "select username from account a join player p on p.account_id"
                                        + " = a.id where p.id = ?")
                        .param(victim.playerId)
                        .query(String.class)
                        .single();
        Browser again = Browser.open(mockMvc);
        again.login(username, Browser.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        // his open WebSocket can't chat any more either
        assertThatThrownBy(() -> chatService.send(victim.playerId, "still here"))
                .isInstanceOf(NotFoundException.class);
        // the same name can't be registered again either: the account row stays
        again.register(username, Browser.PASSWORD).andExpect(status().isConflict());
        // deleting twice, or someone who doesn't exist
        admin.post("/api/admin/players/" + victim.playerId + "/delete", "")
                .andExpect(status().isNotFound());
        admin.post("/api/admin/players/99999999/delete", "").andExpect(status().isNotFound());
    }

    @Test
    void anAdminCannotDeleteHimself() throws Exception {
        long adminPlayerId =
                Long.parseLong(JsonPath.read(body(admin.get("/api/me")), "$.playerId").toString());

        admin.post("/api/admin/players/" + adminPlayerId + "/delete", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
        admin.get("/api/me").andExpect(status().isOk());
    }

    // ---- chat ----

    @Test
    void anAdminHidesAChatMessageFromTheHistory() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        long id = chatService.send(player.playerId, "Spam spam").id();
        assertThat(historyIds(player)).contains((int) id);

        admin.delete("/api/admin/chat/" + id).andExpect(status().isNoContent());
        admin.delete("/api/admin/chat/" + id).andExpect(status().isNoContent()); // idempotent

        // open chats hear of it once, on the chat topic
        assertThat(sent.chat()).containsOnlyOnce("{\"hiddenId\":" + id + "}");

        assertThat(historyIds(player)).doesNotContain((int) id);
        admin.delete("/api/admin/chat/99999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
    }

    private java.util.List<Integer> historyIds(TestPlayer player) throws Exception {
        return JsonPath.read(body(player.browser.get("/api/chat/messages")), "$[*].id");
    }

    // ---- failed events ----

    @Test
    void failedEventsAreListedAndRetryPutsOneBackToPending() throws Exception {
        long failed = insertEvent("FAILED", 3, "boom");
        long pending = insertEvent("PENDING", 0, null);

        String list = body(admin.get("/api/admin/events/failed").andExpect(status().isOk()));
        assertThat(JsonPath.<java.util.List<Integer>>read(list, "$[*].id"))
                .contains((int) failed)
                .doesNotContain((int) pending);
        admin.get("/api/admin/events/failed")
                .andExpect(jsonPath("$[?(@.id == " + failed + ")].lastError").value("boom"))
                .andExpect(jsonPath("$[?(@.id == " + failed + ")].attempts").value(3))
                .andExpect(jsonPath("$[?(@.id == " + failed + ")].kind").value("TEST_UNHANDLED"));

        admin.post("/api/admin/events/" + failed + "/retry", "").andExpect(status().isNoContent());

        assertThat(
                        jdbc.sql("select status || ':' || attempts from game_event where id = ?")
                                .param(failed)
                                .query(String.class)
                                .single())
                .isEqualTo("PENDING:0");
        // a second retry, a pending one, an unknown one
        admin.post("/api/admin/events/" + failed + "/retry", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FAILED"));
        admin.post("/api/admin/events/" + pending + "/retry", "").andExpect(status().isConflict());
        admin.post("/api/admin/events/99999999/retry", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        jdbc.sql("delete from game_event where id in (?, ?)").param(failed).param(pending).update();
    }

    @Test
    void aRecurringEventIsNotRetriedWhileItsNextRoundIsWaiting() throws Exception {
        // the restart after a fix scheduled a fresh growth round; the failed one stays failed
        long failed = insertEvent("BARBARIAN_GROWTH", "FAILED", 3, "boom", "now()");
        long next = insertEvent("BARBARIAN_GROWTH", "PENDING", 0, null, "'9999-01-01'");
        try {
            admin.post("/api/admin/events/" + failed + "/retry", "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVENT_NOT_FAILED"));
            jdbc.sql("delete from game_event where id = ?").param(next).update();
            admin.post("/api/admin/events/" + failed + "/retry", "")
                    .andExpect(status().isNoContent());
        } finally {
            jdbc.sql("delete from game_event where id in (?, ?)")
                    .param(failed)
                    .param(next)
                    .update();
        }
    }

    // ---- helpers ----

    private long insertEvent(String status, int attempts, String error) {
        // TEST_UNHANDLED never has a handler, so the worker can't touch it (and it isn't even on)
        return insertEvent("TEST_UNHANDLED", status, attempts, error, "now()");
    }

    private long insertEvent(String kind, String status, int attempts, String error, String due) {
        return jdbc.sql(
                        "insert into game_event (kind, due_at, status, attempts, last_error,"
                                + " created_at, processed_at) values (?, "
                                + due
                                + ", ?, ?, ?, now(), case when ? = 'FAILED' then now() end)"
                                + " returning id")
                .param(kind)
                .param(status)
                .param(attempts)
                .param(error)
                .param(status)
                .query(Long.class)
                .single();
    }

    private int points(TestPlayer player) {
        return jdbc.sql("select points from player where id = ?")
                .param(player.playerId)
                .query(Integer.class)
                .single();
    }

    private void setPoints(TestPlayer player, int points) {
        jdbc.sql("update player set points = ? where id = ?")
                .param(points)
                .param(player.playerId)
                .update();
    }

    private String villageName(long villageId) {
        return jdbc.sql("select name from village where id = ?")
                .param(villageId)
                .query(String.class)
                .single();
    }

    private String playerName(TestPlayer player) {
        return jdbc.sql("select name from player where id = ?")
                .param(player.playerId)
                .query(String.class)
                .single();
    }

    /** Like {@link #withExtra} with the world's invite code. */
    private String keepingInvite(String extra) {
        String body = settingsJson("Testland", 1, 5, 10, originalSettings.inviteCode()).strip();
        return body.substring(0, body.length() - 1) + extra;
    }

    /**
     * The standard body with more fields ({@code extra} starts with a comma and closes the object).
     */
    private static String withExtra(String extra) {
        String body = settingsJson("Testland", 1, 5, 10, "c").strip();
        return body.substring(0, body.length() - 1) + extra;
    }

    private static String settingsJson(
            String name, double speed, int queueSize, int queueMax, String inviteCode) {
        return """
        {"name":"%s","speed":%s,"unitSpeed":1.0,"startResources":500,"luckRange":0.25,
         "beginnerProtectionHours":72,"nobleMaxDistance":70,"loyaltyRegenPerHour":1.0,
         "buildQueueSize":%d,"buildQueueMax":%d,"recruitQueueSize":5,
         "cancelWindowSeconds":600,"barbariansPerNewPlayer":3,
         "barbarianGrowthEnabled":true,"tradeMaxRatio":3.0,"inviteCode":"%s",
         "npcThinkMinutes":20}
        """
                .formatted(name, speed, queueSize, queueMax, inviteCode);
    }

    private static WorldSettingsUpdateRequest request(AdminWorldSettingsDto s) {
        return new WorldSettingsUpdateRequest(
                s.name(),
                s.speed(),
                s.unitSpeed(),
                s.startResources(),
                s.luckRange(),
                s.beginnerProtectionHours(),
                s.nobleMaxDistance(),
                s.loyaltyRegenPerHour(),
                s.buildQueueSize(),
                s.buildQueueMax(),
                s.recruitQueueSize(),
                s.cancelWindowSeconds(),
                s.barbariansPerNewPlayer(),
                s.barbarianGrowthEnabled(),
                s.tradeMaxRatio(),
                s.inviteCode(),
                s.npcThinkMinutes(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                s.barbarianWallsEnabled(),
                s.barbarianWallStartDays(),
                s.barbarianWallMaxLevel(),
                s.barbarianWallsRebuild(),
                s.unitLevelsEnabled(),
                s.paladinVillagesPerSlot(),
                s.paladinMaxSlots(),
                s.bannersEnabled(),
                s.bannerDropFactor(),
                s.marketExchangeRatio(),
                s.victoryMode(),
                s.dominationPercent(),
                s.victoryHoldHours(),
                s.keepCount(),
                s.keepPercent(),
                s.villageNameLanguage());
    }
}
