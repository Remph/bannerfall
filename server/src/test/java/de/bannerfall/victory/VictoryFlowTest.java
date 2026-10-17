package de.bannerfall.victory;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestEventHandler;
import de.bannerfall.TestPlayer;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.guild.GuildService;
import de.bannerfall.village.Village;
import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * How a world is won and ends (v1.3, 02-game-rules §22). The database is shared with the other
 * tests, so the leading side is made by handing a test player enough extra villages to be ahead of
 * everybody, and the share is set to what that is; everything is put back afterwards.
 */
@IntegrationTest
class VictoryFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired VictoryService victory;
    @Autowired BarbarianService barbarians;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired GuildService guildService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestEventHandler testEvents;
    @Autowired SentMessages sent;

    private Host host;
    private TestPlayer alice;
    private final List<Long> extraVillages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        host = new Host(mockMvc);
        testEvents.reset();
        alice = TestPlayer.register(mockMvc);
        jdbc.sql("update world_settings set guilds_enabled = false").update();
        settings("DOMINATION", 60, 1, 5, 51);
    }

    @AfterEach
    void putTheWorldBack() {
        jdbc.sql("delete from world_result_side").update();
        jdbc.sql("delete from world_result").update();
        jdbc.sql(
                        """
                        update world_settings set ended_at = null, ends_at = null,
                            victory_leader = null, victory_leader_since = null,
                            victory_mode = 'DOMINATION', domination_percent = 60,
                            victory_hold_hours = 48, keep_count = 5, keep_percent = 51,
                            guilds_enabled = false
                        """)
                .update();
        jdbc.sql(
                        """
                        delete from game_event
                        where kind in ('VICTORY_CHECK', 'WORLD_END', 'TEST')
                          and status = 'PENDING'
                        """)
                .update();
        List<Long> gone = new ArrayList<>(extraVillages);
        gone.addAll(jdbc.sql("select id from village where keep").query(Long.class).list());
        for (long id : gone) {
            jdbc.sql("delete from unit_stack where home_village_id = ? or location_village_id = ?")
                    .params(id, id)
                    .update();
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        extraVillages.clear();
        jdbc.sql("delete from guild").update();
        guildService.setInviteInterval(Duration.ofMinutes(1));
        worldService.reload();
    }

    private void settings(
            String mode, int dominationPercent, int holdHours, int keepCount, int keepPercent) {
        jdbc.sql(
                        """
                        update world_settings set victory_mode = ?, domination_percent = ?,
                            victory_hold_hours = ?, keep_count = ?, keep_percent = ?
                        """)
                .params(mode, dominationPercent, holdHours, keepCount, keepPercent)
                .update();
        worldService.reload();
    }

    /**
     * Gives {@code player} more villages than any other player or guild has, and sets the
     * domination share to what he holds then, so he alone meets it.
     */
    private void makeDominant(TestPlayer player) {
        long most =
                jdbc.sql(
                                """
                                select coalesce(max(c), 0) from (
                                    select count(*) as c from village
                                    where player_id is not null and player_id <> ?
                                    group by player_id
                                    union all
                                    select count(*) as c from village v
                                    join guild_member m on m.player_id = v.player_id
                                    where m.guild_id not in (
                                        select guild_id from guild_member where player_id = ?)
                                    group by m.guild_id) counts
                                """)
                        .params(player.playerId, player.playerId)
                        .query(Long.class)
                        .single();
        // the other tests leave many villages behind: enough for a share of at least 1 %
        long before = jdbc.sql("select count(*) from village").query(Long.class).single();
        giveVillages(player, (int) Math.max(most + 1, before / 99 + 2));
        long all = jdbc.sql("select count(*) from village").query(Long.class).single();
        long own =
                jdbc.sql("select count(*) from village where player_id = ?")
                        .param(player.playerId)
                        .query(Long.class)
                        .single();
        int percent = (int) Math.max(1, own * 100 / all);
        jdbc.sql("update world_settings set domination_percent = ?").param(percent).update();
        worldService.reload();
    }

    private void giveVillages(TestPlayer player, int count) {
        List<Village> spawned =
                new TransactionTemplate(transactions)
                        .execute(status -> barbarians.spawnInitial(count));
        for (Village village : spawned) {
            extraVillages.add(village.getId());
            jdbc.sql("update village set player_id = ?, name = 'extra' where id = ?")
                    .params(player.playerId, village.getId())
                    .update();
        }
    }

    private void check(Instant at) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> victory.check(at));
    }

    // ---- domination ----

    @Test
    void aSideThatHoldsItsShareForTheHoldTimeWinsAndTheWorldFreezes() throws Exception {
        makeDominant(alice);
        Instant t0 = clock.instant();
        check(t0);

        alice.browser
                .get("/api/world/victory")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DOMINATION"))
                .andExpect(jsonPath("$.phase").value("OPEN"))
                .andExpect(jsonPath("$.leader.kind").value("PLAYER"))
                .andExpect(jsonPath("$.leader.id").value(alice.playerId))
                .andExpect(jsonPath("$.sides[0].id").value(alice.playerId))
                .andExpect(jsonPath("$.leaderSince").value(t0.toString()))
                .andExpect(jsonPath("$.winsAt").value(t0.plus(Duration.ofHours(1)).toString()))
                .andExpect(jsonPath("$.result").doesNotExist());

        check(t0.plus(Duration.ofMinutes(59)));
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        // an event after the end waits: the world is frozen
        Instant end = t0.plus(Duration.ofHours(1));
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status ->
                                scheduler.schedule(
                                        GameEventKind.TEST,
                                        4242L,
                                        end.plus(Duration.ofMinutes(1))));

        check(end);
        assertThat(worldService.phase()).isEqualTo(WorldPhase.ENDED);
        assertThat(worldService.endedAt()).isEqualTo(end);

        alice.browser
                .get("/api/world/victory")
                .andExpect(jsonPath("$.phase").value("ENDED"))
                .andExpect(jsonPath("$.sides").isEmpty())
                .andExpect(jsonPath("$.result.reason").value("DOMINATION"))
                .andExpect(jsonPath("$.result.endedAt").value(end.toString()))
                .andExpect(jsonPath("$.result.winner.id").value(alice.playerId))
                .andExpect(jsonPath("$.result.winner.kind").value("PLAYER"));
        alice.browser.get("/api/world").andExpect(jsonPath("$.phase").value("ENDED"));

        // reading works, changing doesn't; the chat and messages stay open
        alice.village().andExpect(status().isOk());
        alice.browser
                .patch("/api/villages/" + alice.villageId, "{\"name\":\"Frozen\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ENDED"));
        alice.browser.post("/api/reports/read-all", "").andExpect(status().is2xxSuccessful());

        clock.set(end.plus(Duration.ofHours(2)));
        processor.processDue(clock.instant());
        assertThat(pendingTestEvents()).isEqualTo(1);

        // the host takes it back: everything runs again from where it stopped, so the event that
        // was a minute after the end now comes a minute after the reopening
        host.post("/api/admin/world-end/reopen", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("OPEN"))
                .andExpect(jsonPath("$.endedAt").doesNotExist());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        processor.processDue(clock.instant());
        assertThat(pendingTestEvents()).isEqualTo(1);
        clock.set(clock.instant().plus(Duration.ofMinutes(1)));
        processor.processDue(clock.instant());
        assertThat(pendingTestEvents()).isZero();
        alice.browser.get("/api/world/victory").andExpect(jsonPath("$.result").doesNotExist());
        host.post("/api/admin/world-end/reopen", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_ENDED"));
    }

    @Test
    void droppingBelowTheShareStopsTheClock() {
        makeDominant(alice);
        Instant t0 = clock.instant();
        check(t0);
        jdbc.sql("update world_settings set domination_percent = 100").update();
        worldService.reload();
        check(t0.plus(Duration.ofMinutes(30)));
        assertThat(leader()).isNull();

        makeDominant(alice);
        check(t0.plus(Duration.ofMinutes(35)));
        check(t0.plus(Duration.ofMinutes(65)));
        // the new clock started at minute 35
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);
        check(t0.plus(Duration.ofMinutes(95)));
        assertThat(worldService.phase()).isEqualTo(WorldPhase.ENDED);
    }

    @Test
    void everyPlayerHearsWhenTheClockStartsAndStops() throws Exception {
        TestPlayer bob = TestPlayer.register(mockMvc);
        makeDominant(alice);
        Instant t0 = clock.instant();
        check(t0);

        String winsAt = t0.plus(Duration.ofHours(1)).toString();
        String started = lastClock(alice);
        assertThat(JsonPath.<Number>read(started, "$.leader.id").longValue())
                .isEqualTo(alice.playerId);
        assertThat(JsonPath.<String>read(started, "$.winsAt")).isEqualTo(winsAt);
        assertThat(JsonPath.<Object>read(started, "$.previous")).isNull();
        assertThat(JsonPath.<Boolean>read(started, "$.leaderYours")).isTrue();
        String toBob = lastClock(bob);
        assertThat(JsonPath.<Number>read(toBob, "$.leader.id").longValue())
                .isEqualTo(alice.playerId);
        assertThat(JsonPath.<Boolean>read(toBob, "$.leaderYours")).isFalse();

        // the clock running on says nothing
        int before = clocks(alice).size();
        check(t0.plus(Duration.ofMinutes(5)));
        assertThat(clocks(alice)).hasSize(before);

        jdbc.sql("update world_settings set domination_percent = 100").update();
        worldService.reload();
        check(t0.plus(Duration.ofMinutes(10)));
        String stopped = lastClock(alice);
        assertThat(JsonPath.<Object>read(stopped, "$.leader")).isNull();
        assertThat(JsonPath.<Object>read(stopped, "$.winsAt")).isNull();
        assertThat(JsonPath.<Number>read(stopped, "$.previous.id").longValue())
                .isEqualTo(alice.playerId);
        assertThat(JsonPath.<Boolean>read(stopped, "$.previousYours")).isTrue();
        assertThat(JsonPath.<Boolean>read(lastClock(bob), "$.previousYours")).isFalse();
    }

    private List<String> clocks(TestPlayer player) {
        return sent.toPlayer(player.playerId).stream()
                .filter(json -> json.contains("\"type\":\"VICTORY_CLOCK\""))
                .toList();
    }

    private String lastClock(TestPlayer player) {
        List<String> clocks = clocks(player);
        assertThat(clocks).isNotEmpty();
        return clocks.getLast();
    }

    @Test
    void aGuildIsOneSide() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        guildService.setInviteInterval(Duration.ZERO);
        TestPlayer bob = TestPlayer.register(mockMvc);
        String suffix = Long.toString(System.nanoTime(), 36);
        String tag = suffix.substring(suffix.length() - 4);
        long guildId =
                JsonPath.<Number>read(
                                body(
                                        alice.browser
                                                .post(
                                                        "/api/guilds",
                                                        "{\"name\":\"Victors "
                                                                + suffix
                                                                + "\",\"tag\":\""
                                                                + tag
                                                                + "\"}")
                                                .andExpect(status().isCreated())),
                                "$.guild.id")
                        .longValue();
        String bobName =
                jdbc.sql("select name from player where id = ?")
                        .param(bob.playerId)
                        .query(String.class)
                        .single();
        alice.browser
                .post("/api/guilds/mine/invitations", "{\"playerName\":\"" + bobName + "\"}")
                .andExpect(status().isCreated());
        long invitation =
                JsonPath.<Number>read(
                                body(bob.browser.get("/api/guilds/mine")), "$.invitations[0].id")
                        .longValue();
        bob.browser
                .post("/api/guilds/invitations/" + invitation + "/accept", "")
                .andExpect(status().isOk());
        // the villages are bob's: the guild leads with them
        makeDominant(bob);
        check(clock.instant());

        alice.browser
                .get("/api/world/victory")
                .andExpect(jsonPath("$.leader.kind").value("GUILD"))
                .andExpect(jsonPath("$.leader.id").value(guildId))
                .andExpect(jsonPath("$.leader.tag").value(tag));
        assertThat(leader()).isEqualTo("GUILD:" + guildId);

        // with guilds off every player is his own side
        jdbc.sql("update world_settings set guilds_enabled = false").update();
        worldService.reload();
        check(clock.instant().plusSeconds(300));
        assertThat(leader()).isEqualTo("PLAYER:" + bob.playerId);
    }

    // ---- end date ----

    @Test
    void theEndDateEndsTheWorldAndTheMostPointsWin() throws Exception {
        Instant endsAt = clock.instant().plus(Duration.ofHours(3));
        host.put("/api/admin/world-end", "{\"endsAt\":\"" + endsAt + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(endsAt.toString()));
        alice.browser.get("/api/world").andExpect(jsonPath("$.endsAt").value(endsAt.toString()));

        clock.set(endsAt.minusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        clock.set(endsAt);
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.ENDED);
        String result = body(alice.browser.get("/api/world/victory"));
        assertThat(JsonPath.<String>read(result, "$.result.reason")).isEqualTo("END_DATE");
        List<Number> points = JsonPath.read(result, "$.result.sides[*].points");
        assertThat(points).isNotEmpty();
        assertThat(points.stream().map(Number::longValue).toList())
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(JsonPath.<Number>read(result, "$.result.winner.points").longValue())
                .isEqualTo(points.get(0).longValue());

        // a new registration waits for the next world
        Browser.open(mockMvc)
                .register(Browser.uniqueUsername(), Browser.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ENDED"));
        host.put("/api/admin/world-end", "{\"endsAt\":null}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_ENDED"));
    }

    @Test
    void theHostCanEndTheWorldNowOrTakeTheDateBack() throws Exception {
        Instant endsAt = clock.instant().plus(Duration.ofHours(3));
        host.put("/api/admin/world-end", "{\"endsAt\":\"" + endsAt + "\"}")
                .andExpect(status().isOk());
        host.put("/api/admin/world-end", "{\"endsAt\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").doesNotExist());
        clock.set(endsAt.plusSeconds(1));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.OPEN);

        host.post("/api/admin/world-end/now", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(clock.instant().toString()));
        processor.processDue(clock.instant());
        assertThat(worldService.phase()).isEqualTo(WorldPhase.ENDED);
        host.get("/api/admin/audit?limit=5").andExpect(jsonPath("$[0].action").value("WORLD_END"));
    }

    @Test
    void aFrozenWorldStandsStillAndPicksUpWhereItStopped() throws Exception {
        Instant t0 = clock.instant();
        jdbc.sql(
                        "update village set wood = 0, clay = 0, iron = 0, resources_updated_at = ?"
                                + " where id = ?")
                .params(Timestamp.from(t0), alice.villageId)
                .update();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status ->
                                scheduler.schedule(
                                        GameEventKind.TEST,
                                        4343L,
                                        t0.plus(Duration.ofMinutes(10))));

        // an end date in the past ends the world now, not back then
        host.put("/api/admin/world-end", "{\"endsAt\":\"" + t0.minus(Duration.ofHours(5)) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(t0.toString()));
        processor.processDue(clock.instant());
        assertThat(worldService.endedAt()).isEqualTo(t0);

        // two hours later nothing has grown, even though the village is read (and caught up)
        clock.set(t0.plus(Duration.ofHours(2)));
        alice.village()
                .andExpect(jsonPath("$.resources.wood").value(0.0))
                .andExpect(jsonPath("$.resources.at").value(t0.toString()));

        host.post("/api/admin/world-end/reopen", "").andExpect(status().isOk());
        Instant due =
                jdbc.sql("select due_at from game_event where kind = 'TEST' and ref_id = 4343")
                        .query(Timestamp.class)
                        .single()
                        .toInstant();
        assertThat(due).isEqualTo(t0.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(10)));

        // half an hour after the reopening: half an hour of production, not two and a half
        clock.set(t0.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(30)));
        String village = body(alice.village());
        double wood = JsonPath.<Number>read(village, "$.resources.wood").doubleValue();
        double perHour = JsonPath.<Number>read(village, "$.production.wood").doubleValue();
        assertThat(perHour).isPositive();
        assertThat(wood).isCloseTo(perHour / 2, within(0.01));
        processor.processDue(clock.instant());
        assertThat(pendingTestEvents()).isZero();
    }

    // ---- banner keeps ----

    @Test
    void keepsAppearAroundTheCentreAndHoldingMostOfThemWins() throws Exception {
        settings("KEEPS", 60, 1, 3, 51);
        Instant t0 = clock.instant();
        check(t0);
        List<Long> keeps =
                jdbc.sql("select id from village where keep order by id").query(Long.class).list();
        assertThat(keeps).hasSize(3);
        // strong, near the centre, with a garrison
        int mapSize = worldService.settings().mapSize();
        for (long id : keeps) {
            var row =
                    jdbc.sql("select x, y, b_wall, player_id from village where id = ?")
                            .param(id)
                            .query()
                            .singleRow();
            assertThat(row.get("player_id")).isNull();
            assertThat(((Number) row.get("b_wall")).intValue()).isEqualTo(10);
            double distance =
                    Math.hypot(
                            ((Number) row.get("x")).intValue() - mapSize / 2,
                            ((Number) row.get("y")).intValue() - mapSize / 2);
            assertThat(distance).isLessThan(12);
        }
        assertThat(
                        jdbc.sql(
                                        """
                                        select sum(u_spearman + u_swordsman + u_heavy_cavalry)
                                        from unit_stack where home_village_id = ?
                                        """)
                                .param(keeps.get(0))
                                .query(Long.class)
                                .single())
                .isEqualTo(2100);
        // a second check spawns no more
        check(t0.plusSeconds(60));
        assertThat(jdbc.sql("select count(*) from village where keep").query(Long.class).single())
                .isEqualTo(3);

        int x =
                jdbc.sql("select x from village where id = ?")
                        .param(keeps.get(0))
                        .query(Integer.class)
                        .single();
        int y =
                jdbc.sql("select y from village where id = ?")
                        .param(keeps.get(0))
                        .query(Integer.class)
                        .single();
        alice.browser
                .get("/api/map?x=" + x + "&y=" + y + "&w=1&h=1")
                .andExpect(jsonPath("$[0].keep").value(true));

        // 51 % of 3 keeps is 2
        jdbc.sql("update village set player_id = ?, name = 'keep' where id in (?, ?)")
                .params(alice.playerId, keeps.get(0), keeps.get(1))
                .update();
        Instant t1 = t0.plusSeconds(300);
        check(t1);
        alice.browser
                .get("/api/world/victory")
                .andExpect(jsonPath("$.keepCount").value(3))
                .andExpect(jsonPath("$.keepsNeeded").value(2))
                .andExpect(jsonPath("$.leader.id").value(alice.playerId))
                .andExpect(jsonPath("$.sides[0].keeps").value(2));
        check(t1.plus(Duration.ofHours(1)));
        alice.browser
                .get("/api/world/victory")
                .andExpect(jsonPath("$.result.reason").value("KEEPS"))
                .andExpect(jsonPath("$.result.winner.id").value(alice.playerId));
    }

    private long pendingTestEvents() {
        return jdbc.sql(
                        "select count(*) from game_event where kind = 'TEST' and status ="
                                + " 'PENDING'")
                .query(Long.class)
                .single();
    }

    private String leader() {
        return jdbc.sql("select victory_leader from world_settings")
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
