package de.bannerfall.chronicle;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.EnemyKind;
import de.bannerfall.guild.GuildEnemyService;
import de.bannerfall.guild.GuildService;
import de.bannerfall.victory.VictoryService;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The world chronicle (2.1 I1, 02-game-rules §32): written while the world runs, read once it has
 * ended. The database is shared, so the world is ended by the host's "end now" and put back
 * afterwards like in {@code VictoryFlowTest}.
 */
@IntegrationTest
class ChronicleFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired VictoryService victory;
    @Autowired GuildService guildService;
    @Autowired GuildEnemyService enemies;
    @Autowired Chronicle chronicle;
    @Autowired TestRandomSource dice;

    private double luckRangeBefore;
    private Timestamp openedBefore;
    private int nobleMaxDistanceBefore;
    private double loyaltyRegenBefore;
    private TestPlayer attacker;
    private TestPlayer defender;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from chronicle_event").update();
        jdbc.sql("delete from chronicle_frame").update();
        luckRangeBefore = worldService.settings().luckRange();
        openedBefore =
                jdbc.sql("select initialized_at from world_settings")
                        .query(Timestamp.class)
                        .optional()
                        .orElse(null);
        // the world opened two and a half days ago (the test world may have no opening at all)
        jdbc.sql("update world_settings set initialized_at = ?")
                .param(Timestamp.from(clock.instant().minus(Duration.ofHours(60))))
                .update();
        nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        loyaltyRegenBefore = worldService.settings().loyaltyRegenPerHour();
        jdbc.sql(
                        "update world_settings set luck_range = 0, noble_max_distance = 1000,"
                                + " loyalty_regen_per_hour = 0, guilds_enabled = false")
                .update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void putTheWorldBack() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from world_result_side").update();
        jdbc.sql("delete from world_result").update();
        jdbc.sql("delete from chronicle_event").update();
        jdbc.sql("delete from chronicle_frame").update();
        jdbc.sql("delete from guild").update();
        jdbc.sql(
                        """
                        update world_settings set ended_at = null, ends_at = null,
                            victory_leader = null, victory_leader_since = null,
                            guilds_enabled = false, luck_range = ?, noble_max_distance = ?,
                            loyalty_regen_per_hour = ?, initialized_at = ?
                        """)
                .params(luckRangeBefore, nobleMaxDistanceBefore, loyaltyRegenBefore, openedBefore)
                .update();
        worldService.reload();
    }

    @Test
    void theChronicleOpensAtTheEndAndTellsTheConquestAndReplaysIt() throws Exception {
        attacker.browser
                .get("/api/chronicle")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_ENDED"));
        int day = chronicle.daysDone(worldService.gameTime(clock.instant()));
        assertThat(chronicle.dailyFrame()).contains(day);
        assertThat(chronicle.dailyFrame()).isEmpty(); // once per day

        dice.use(FixedRandomSource.constant(0.99)); // every noble hits 35
        Instant conquest = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}");
        clock.set(conquest);
        processor.processDue(conquest);
        endTheWorld();

        String read = body(attacker.browser.get("/api/chronicle").andExpect(status().isOk()));
        List<String> kinds = JsonPath.read(read, "$.entries[*].kind");
        assertThat(kinds).containsSubsequence("CONQUEST", "WORLD_ENDED");
        assertThat(kinds.getLast()).isEqualTo("WORLD_ENDED");
        Map<String, Object> taken =
                JsonPath.<List<Map<String, Object>>>read(read, "$.entries[?(@.kind == 'CONQUEST')]")
                        .getFirst();
        assertThat(JsonPath.<Number>read(taken, "$.facts.to.id").longValue())
                .isEqualTo(attacker.playerId);
        assertThat(JsonPath.<Number>read(taken, "$.facts.from.id").longValue())
                .isEqualTo(defender.playerId);
        assertThat(JsonPath.<Boolean>read(taken, "$.facts.eliminated")).isTrue();
        assertThat(JsonPath.<Boolean>read(taken, "$.facts.first")).isTrue();
        assertThat(JsonPath.<Number>read(taken, "$.facts.village.id").longValue())
                .isEqualTo(defender.villageId);
        assertThat(JsonPath.<Boolean>read(taken, "$.mine")).isTrue();

        // the replay: the daily frame names every village, the end only what changed
        List<Map<String, Object>> frames = JsonPath.read(read, "$.map.frames");
        assertThat(frames).hasSizeGreaterThanOrEqualTo(2);
        List<Integer> end = JsonPath.read(frames.getLast(), "$.changes");
        assertThat(end).hasSize(2);
        int sideIndex = end.get(1) - 1;
        assertThat(JsonPath.<Number>read(read, "$.map.sides[" + sideIndex + "].id").longValue())
                .isEqualTo(attacker.playerId);
        assertThat(JsonPath.<Boolean>read(read, "$.map.sides[" + sideIndex + "].mine")).isTrue();
        Map<String, Object> dot = JsonPath.read(read, "$.map.villages[" + end.get(0) + "]");
        Coordinates where = coordinates(defender.villageId);
        assertThat(dot).containsEntry("x", where.x()).containsEntry("y", where.y());

        // the loser reads the same chronicle, with the conquest as his own moment
        defender.browser
                .get("/api/chronicle")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.kind == 'CONQUEST')].mine").value(true));
    }

    @Test
    void guildsAndTheirWarsAreToldAndTheLeadIsTakenFromTheFrames() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        long red = guildId(guildService.create(attacker.playerId, "Red Hand", "RED"));
        long blue = guildId(guildService.create(defender.playerId, "Blue Shield", "BLU"));
        enemies.add(attacker.playerId, EnemyKind.GUILD, blue);
        enemies.add(attacker.playerId, EnemyKind.GUILD, blue); // the same mark again: no new war
        frameAt(0, Map.of(attacker.playerId, 1000L, defender.playerId, 5000L));
        frameAt(1, Map.of(attacker.playerId, 9000L, defender.playerId, 5000L));
        endTheWorld();

        String read = body(attacker.browser.get("/api/chronicle").andExpect(status().isOk()));
        assertThat(JsonPath.<Boolean>read(read, "$.guilds")).isTrue();
        List<String> kinds = JsonPath.read(read, "$.entries[*].kind");
        assertThat(kinds).containsSubsequence("GUILD_FOUNDED", "GUILD_FOUNDED", "WAR");
        assertThat(kinds.stream().filter("WAR"::equals)).hasSize(1);
        assertThat(JsonPath.<List<Number>>read(read, "$.entries[?(@.kind == 'WAR')].facts.side.id"))
                .map(Number::longValue)
                .containsExactly(red);
        assertThat(
                        JsonPath.<List<Number>>read(
                                read, "$.entries[?(@.kind == 'WAR')].facts.other.id"))
                .map(Number::longValue)
                .containsExactly(blue);
        // day 0 Blue leads, after day 1 Red: the first LEAD line is Red's on day 1, none for the
        // first leader (the end's frame holds every player of the shared database, so another
        // line may follow)
        assertThat(
                        JsonPath.<List<Number>>read(
                                read, "$.entries[?(@.kind == 'LEAD')].facts.side.id"))
                .map(Number::longValue)
                .first()
                .isEqualTo(red);
        assertThat(JsonPath.<List<Integer>>read(read, "$.entries[?(@.kind == 'LEAD')].day"))
                .first()
                .isEqualTo(1);
    }

    // ---- helpers ----

    /** The host's "end now", and the event worker ends the world. */
    private void endTheWorld() {
        victory.endNow();
        processor.processDue(clock.instant());
        worldService.reload();
        assertThat(worldService.endedAt()).isNotNull();
    }

    /**
     * A stored frame of {@code day} with only the two test players' villages and these points, as
     * if written on that day.
     */
    private void frameAt(int day, Map<Long, Long> points) {
        StringBuilder players = new StringBuilder("[");
        points.forEach(
                (id, value) -> {
                    Map<String, Object> row =
                            jdbc.sql(
                                            """
                                            select p.name, g.id as gid, g.name as gname, g.tag
                                            from player p
                                            left join guild_member m on m.player_id = p.id
                                            left join guild g on g.id = m.guild_id where p.id = ?
                                            """)
                                    .param(id)
                                    .query()
                                    .singleRow();
                    if (players.length() > 1) {
                        players.append(',');
                    }
                    players.append(
                            String.format(
                                    Locale.ROOT,
                                    "{\"id\":%d,\"name\":\"%s\",\"npc\":false,\"guildId\":%s,"
                                            + "\"guildName\":\"%s\",\"guildTag\":\"%s\","
                                            + "\"points\":%d}",
                                    id,
                                    row.get("name"),
                                    row.get("gid"),
                                    row.get("gname"),
                                    row.get("tag"),
                                    value));
                });
        players.append(']');
        jdbc.sql(
                        """
                        insert into chronicle_frame (day, at, owners, players)
                        values (?, ?, cast(? as jsonb), cast(? as jsonb))
                        """)
                .params(
                        day,
                        Timestamp.from(clock.instant().minus(Duration.ofDays(2 - day))),
                        "[["
                                + attacker.villageId
                                + ","
                                + attacker.playerId
                                + "],["
                                + defender.villageId
                                + ","
                                + defender.playerId
                                + "]]",
                        players.toString())
                .update();
    }

    private static long guildId(Object myGuild) {
        return ((de.bannerfall.guild.MyGuildDto) myGuild).guild().id();
    }

    private Coordinates coordinates(long villageId) {
        return jdbc.sql("select x, y from village where id = ?")
                .param(villageId)
                .query((rs, row) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                .single();
    }

    /** Puts the units at home first (on top of what is there), then sends them. */
    private Instant attack(TestPlayer from, TestPlayer to, String unitsJson) throws Exception {
        Map<String, Integer> units = JsonPath.read(unitsJson, "$");
        units.forEach(
                (type, count) ->
                        jdbc.sql(
                                        "update unit_stack set "
                                                + column(UnitType.valueOf(type))
                                                + " = greatest("
                                                + column(UnitType.valueOf(type))
                                                + ", ?) where home_village_id = ?"
                                                + " and location_village_id = ?")
                                .params(count, from.villageId, from.villageId)
                                .update());
        String sent =
                body(
                        from.command("ATTACK", coordinates(to.villageId), unitsJson)
                                .andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sent, "$.arriveAt"));
    }

    private static String column(UnitType type) {
        return "u_" + type.name().toLowerCase(Locale.ROOT);
    }
}
