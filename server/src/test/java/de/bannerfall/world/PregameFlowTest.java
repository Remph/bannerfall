package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.npc.NpcLordService;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.victory.VictoryService;
import de.bannerfall.village.SpawnService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The pre-game (M14g, 02-game-rules §1b): registration without a village, the closed game API, the
 * host's controls and the opening that creates the villages. The shared test world is put into the
 * pre-game for each test and back to its normal state afterwards.
 */
@IntegrationTest
class PregameFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired SentMessages sent;
    @Autowired BarbarianService barbarians;
    @Autowired NpcLordService npcLords;
    @Autowired EventScheduler scheduler;
    @Autowired WorldOpeningService opening;
    @Autowired SpawnService spawns;
    @Autowired PlayerRepository players;
    @Autowired VictoryService victory;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;

    private Host host;
    private Timestamp initializedBefore;
    private int barbarianInitialBefore;
    private long newestVillageBefore;
    private int mapSizeBefore;

    @BeforeEach
    void enterPregame() {
        host = new Host(mockMvc);
        jdbc.sql("delete from game_event").update();
        initializedBefore =
                jdbc.sql("select initialized_at from world_settings")
                        .query((rs, row) -> Optional.ofNullable(rs.getTimestamp(1)))
                        .single()
                        .orElse(null);
        barbarianInitialBefore = worldService.settings().barbarianInitialCount();
        mapSizeBefore = worldService.settings().mapSize();
        newestVillageBefore =
                jdbc.sql("select coalesce(max(id), 0) from village").query(Long.class).single();
        jdbc.sql(
                        "update world_settings set pregame = true, opens_at = null, initialized_at"
                                + " = null, barbarian_initial_count = 3")
                .update();
        worldService.reload();
    }

    @AfterEach
    void backToNormal() {
        jdbc.sql("delete from game_event").update();
        // Free the map again. An opening gives a village to every player who has none (also the
        // ones other test classes left behind) and the shared test map has room for only
        // so many (WORLD_FULL): without this, the registrations of the tests that run after this
        // class fail.
        jdbc.sql("delete from unit_stack where home_village_id > ? or location_village_id > ?")
                .param(newestVillageBefore)
                .param(newestVillageBefore)
                .update();
        jdbc.sql("delete from village where id > ?").param(newestVillageBefore).update();
        jdbc.sql(
                        "update world_settings set pregame = false, opens_at = null,"
                                + " initialized_at = ?, barbarian_initial_count = ?")
                .param(initializedBefore)
                .param(barbarianInitialBefore)
                .update();
        worldService.reload();
    }

    @Test
    void aPlayerRegistersWithoutAVillageAndSeesTheWorld() throws Exception {
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername())
                .andExpect(jsonPath("$.villages").isEmpty())
                .andExpect(jsonPath("$.needsRespawn").value(false));

        // registering before the opening makes a Pioneer (02-game-rules §19)
        long playerId =
                JsonPath.<Number>read(
                                player.get("/api/me")
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(),
                                "$.playerId")
                        .longValue();
        assertThat(
                        jdbc.sql(
                                        "select count(*) from player_achievement where player_id ="
                                                + " ? and achievement = 'PIONEER'")
                                .param(playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1L);

        player.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages").isEmpty())
                .andExpect(jsonPath("$.needsRespawn").value(false));
        player.get("/api/world")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREGAME"))
                .andExpect(jsonPath("$.opensAt").doesNotExist())
                .andExpect(jsonPath("$.registeredPlayers").isNumber())
                .andExpect(jsonPath("$.beginnerProtectionHours").value(72))
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
        player.get("/api/chat/messages").andExpect(status().isOk());
    }

    @Test
    void gameRequestsAreClosedUntilTheWorldOpens() throws Exception {
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername());

        for (String path :
                List.of(
                        "/api/ranking",
                        "/api/map?x=0&y=0&width=5&height=5",
                        "/api/villages/1",
                        "/api/reports")) {
            player.get(path)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WORLD_NOT_OPEN"));
        }
        player.post("/api/me/respawn", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_OPEN"));

        // not logged in: still 401, not 409
        Browser.open(mockMvc).get("/api/ranking").andExpect(status().isUnauthorized());
    }

    @Test
    void guildsCanBeFoundedBeforeTheOpening() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        try {
            Browser founder = Browser.open(mockMvc);
            founder.register(Browser.uniqueUsername());

            founder.post("/api/guilds", "{\"name\":\"Early Birds\",\"tag\":\"EB\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("LEADER"));
            founder.get("/api/guilds/mine")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.guild.tag").value("EB"));
            founder.get("/api/guilds").andExpect(status().isOk());
            founder.get("/api/world")
                    .andExpect(jsonPath("$.guildsEnabled").value(true))
                    .andExpect(jsonPath("$.guildMaxMembers").value(10));
        } finally {
            jdbc.sql("delete from guild").update();
            jdbc.sql("update world_settings set guilds_enabled = false").update();
            worldService.reload();
        }
    }

    @Test
    void theLoginPageCanReadThePhaseWithoutLogin() throws Exception {
        Browser visitor = Browser.open(mockMvc);
        visitor.get("/api/world/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREGAME"))
                .andExpect(jsonPath("$.name").value("Bannerfall 1"))
                .andExpect(jsonPath("$.inviteCode").doesNotExist());

        Instant opensAt = clock.instant().plus(Duration.ofDays(3));
        setOpening(opensAt).andExpect(status().isOk());
        visitor.get("/api/world/status").andExpect(jsonPath("$.opensAt").value(opensAt.toString()));
    }

    @Test
    void theOpeningCreatesTheContentAndAVillageForEveryPlayerWhoRegisteredBefore()
            throws Exception {
        Browser first = Browser.open(mockMvc);
        String firstName = Browser.uniqueUsername();
        first.register(firstName);
        Browser second = Browser.open(mockMvc);
        String secondName = Browser.uniqueUsername();
        second.register(secondName);
        long firstPlayer = playerId(firstName);
        long villagesBefore = count("village");

        Instant opensAt = clock.instant().plus(Duration.ofDays(2));
        setOpening(opensAt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREGAME"))
                .andExpect(jsonPath("$.opensAt").value(opensAt.toString()));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);

        clock.set(opensAt.plusSeconds(30));
        processor.processDue(clock.instant());

        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        assertThat(worldService.opensAt()).isNull();
        // 3 initial barbarians and 2 players
        assertThat(count("village") - villagesBefore).isGreaterThanOrEqualTo(5);
        for (Browser player : List.of(first, second)) {
            player.get("/api/me")
                    .andExpect(jsonPath("$.villages.length()").value(1))
                    .andExpect(jsonPath("$.needsRespawn").value(false));
        }
        // beginner protection counts from the opening, not from the registration
        first.get("/api/me")
                .andExpect(
                        jsonPath("$.protectedUntil")
                                .value(clock.instant().plus(Duration.ofHours(72)).toString()));
        assertThat(sent.toPlayer(firstPlayer)).anyMatch(json -> json.contains("WORLD_OPENED"));
        // the game API works now
        first.get("/api/ranking").andExpect(status().isOk());
        // the recurring growth event exists
        assertThat(
                        jsonCount(
                                "select count(*) from game_event where kind = 'BARBARIAN_GROWTH'"
                                        + " and status = 'PENDING'"))
                .isEqualTo(1);
    }

    @Test
    void theOpeningLeavesOutDeletedAndPermanentlyBannedAccounts() throws Exception {
        String keeper = Browser.uniqueUsername();
        String timedBan = Browser.uniqueUsername();
        String permanentBan = Browser.uniqueUsername();
        String deleted = Browser.uniqueUsername();
        for (String name : List.of(keeper, timedBan, permanentBan, deleted)) {
            Browser.open(mockMvc).register(name);
        }
        Instant tomorrow = clock.instant().plus(Duration.ofDays(1));
        host.post(
                        "/api/admin/players/" + playerId(timedBan) + "/ban",
                        "{\"until\":\"" + tomorrow + "\",\"reason\":\"x\"}")
                .andExpect(status().isOk());
        host.post(
                        "/api/admin/players/" + playerId(permanentBan) + "/ban",
                        "{\"until\":null,\"reason\":\"x\"}")
                .andExpect(status().isOk());
        host.post("/api/admin/players/" + playerId(deleted) + "/delete", "")
                .andExpect(status().isOk());

        host.post("/api/admin/world-opening/now", "").andExpect(status().isOk());
        processor.processDue(clock.instant());

        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        assertThat(villageCountOf(keeper)).isEqualTo(1);
        // a timed ban leaves a player in the game (M14e), so he gets his village at the opening
        assertThat(villageCountOf(timedBan)).isEqualTo(1);
        assertThat(villageCountOf(permanentBan)).isZero();
        assertThat(villageCountOf(deleted)).isZero();
    }

    @Test
    void aRegistrationAfterTheOpeningGetsItsVillageAsUsual() throws Exception {
        setOpening(clock.instant()).andExpect(status().isOk());
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        Browser late = Browser.open(mockMvc);
        late.register(Browser.uniqueUsername()).andExpect(jsonPath("$.villages.length()").value(1));
    }

    @Test
    void openNowOpensAtTheNextWorkerRun() throws Exception {
        Browser player = Browser.open(mockMvc);
        player.register(Browser.uniqueUsername());

        host.post("/api/admin/world-opening/now", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREGAME"));
        processor.processDue(clock.instant());

        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        player.get("/api/me").andExpect(jsonPath("$.villages.length()").value(1));
    }

    @Test
    void theTimeCanBeMovedAndTakenBack() throws Exception {
        Instant first = clock.instant().plus(Duration.ofHours(5));
        Instant later = clock.instant().plus(Duration.ofHours(9));
        setOpening(first).andExpect(status().isOk());
        setOpening(later).andExpect(status().isOk());
        assertThat(pendingOpenings()).containsExactly(later);

        clock.set(first.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);

        host.put("/api/admin/world-opening", "{\"opensAt\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opensAt").doesNotExist());
        assertThat(pendingOpenings()).isEmpty();
        clock.set(later.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);
    }

    @Test
    void aRestartRestoresALostOpeningEvent() throws Exception {
        Instant opensAt = clock.instant().plus(Duration.ofDays(1));
        setOpening(opensAt).andExpect(status().isOk());
        jdbc.sql("delete from game_event").update();

        initializer.initialize();

        assertThat(pendingOpenings()).containsExactly(opensAt);
        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);
    }

    @Test
    void mapSizeAndTheContentCountsChangeOnlyBeforeTheOpening() throws Exception {
        String full = fullSettings(60, 7, 2);
        host.put("/api/admin/world-settings", full)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapSize").value(60))
                .andExpect(jsonPath("$.barbarianInitialCount").value(7))
                .andExpect(jsonPath("$.npcLordCount").value(2));
        assertThat(worldService.settings().mapSize()).isEqualTo(60);

        // restore the shared values, then open
        host.put("/api/admin/world-settings", fullSettings(mapSizeBefore, 3, 0))
                .andExpect(status().isOk());
        host.post("/api/admin/world-opening/now", "").andExpect(status().isOk());
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        host.put("/api/admin/world-settings", fullSettings(60, 3, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ALREADY_OPEN"));
        // the same value as now, and null, are fine in an open world
        host.put("/api/admin/world-settings", fullSettings(mapSizeBefore, 3, 0))
                .andExpect(status().isOk());
        host.put("/api/admin/world-settings", fullSettings(null, null, null))
                .andExpect(status().isOk());
        // the time can't be set any more
        setOpening(clock.instant().plus(Duration.ofDays(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ALREADY_OPEN"));
    }

    @Test
    void theProductionStartPutsOnlyAFreshWorldIntoThePregame() {
        jdbc.sql("update world_settings set pregame = false").update();
        worldService.reload();
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        assertThat(worldService.enterPregame()).isTrue();
        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);
        assertThat(worldService.enterPregame()).isTrue();

        // content exists: nothing to wait for
        jdbc.sql("update world_settings set pregame = false, initialized_at = now()").update();
        worldService.reload();
        assertThat(worldService.enterPregame()).isFalse();
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
    }

    @Test
    void theProductionStartKeepsAFreshWorldInThePregameEvenIfTheSettingsWereReadBefore() {
        // a fresh production database: the row says "open" (the column default) and nothing has
        // been created. A request that arrives before the start listener runs has cached that.
        jdbc.sql("update world_settings set pregame = false, initialized_at = null").update();
        worldService.reload();
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        long villagesBefore = count("village");

        WorldInitializer startup =
                new WorldInitializer(
                        worldService,
                        barbarians,
                        npcLords,
                        scheduler,
                        opening,
                        spawns,
                        players,
                        victory,
                        events,
                        clock,
                        true,
                        true);
        new TransactionTemplate(transactions).executeWithoutResult(status -> startup.onStart());

        assertThat(worldService.phase()).isEqualTo(WorldPhase.PREGAME);
        assertThat(count("village")).isEqualTo(villagesBefore);
        assertThat(jsonCount("select count(*) from world_settings where initialized_at is null"))
                .isEqualTo(1);
        assertThat(jsonCount("select count(*) from game_event")).isZero();
    }

    // ---- helpers ----

    private ResultActions setOpening(Instant opensAt) throws Exception {
        return host.put("/api/admin/world-opening", "{\"opensAt\":\"" + opensAt + "\"}");
    }

    private List<Instant> pendingOpenings() {
        return jdbc
                .sql(
                        "select due_at from game_event where kind = 'WORLD_OPEN'"
                                + " and status = 'PENDING'")
                .query(Timestamp.class)
                .list()
                .stream()
                .map(Timestamp::toInstant)
                .toList();
    }

    private long playerId(String username) {
        return jdbc.sql(
                        "select p.id from player p join account a on a.id = p.account_id"
                                + " where a.username = ?")
                .param(username)
                .query(Long.class)
                .single();
    }

    private long villageCountOf(String username) {
        return jdbc.sql(
                        "select p.village_count from player p join account a on a.id = p.account_id"
                                + " where a.username = ?")
                .param(username)
                .query(Long.class)
                .single();
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    private long jsonCount(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static String fullSettings(Integer mapSize, Integer barbarians, Integer lords) {
        return """
        {"name":"Bannerfall 1","speed":1.0,"unitSpeed":1.0,"startResources":500,
         "luckRange":0.25,"beginnerProtectionHours":72,"nobleMaxDistance":70,
         "loyaltyRegenPerHour":1.0,"buildQueueSize":5,"buildQueueMax":10,"recruitQueueSize":5,
         "cancelWindowSeconds":600,"barbariansPerNewPlayer":0,"barbarianGrowthEnabled":true,
         "tradeMaxRatio":3.0,"inviteCode":"change-me","npcThinkMinutes":20,
         "mapSize":%s,"barbarianInitialCount":%s,"npcLordCount":%s}
        """
                .formatted(mapSize, barbarians, lords);
    }
}
