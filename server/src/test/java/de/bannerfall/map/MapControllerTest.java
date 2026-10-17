package de.bannerfall.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/map. The database is shared with all integration tests and registered players spawn
 * around the centre, so the villages made here sit near the corners of the MAP_SIZE × MAP_SIZE map,
 * where no spawn reaches, and are removed afterwards.
 */
@IntegrationTest
class MapControllerTest {

    private static final int MAP_SIZE = TestWorldConfiguration.MAP_SIZE;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired WorldService worldService;

    private TestPlayer player;
    private final List<Long> villageIds = new ArrayList<>();
    private final List<Long> playerIds = new ArrayList<>();

    @BeforeEach
    void registerPlayer() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void removeTestVillages() {
        jdbc.sql("delete from banner_roll where winner_id = ?").param(player.playerId).update();
        jdbc.sql("update world_settings set banners_enabled = true where id = 1").update();
        worldService.reload();
        villageIds.forEach(id -> jdbc.sql("delete from village where id = ?").param(id).update());
        playerIds.forEach(id -> jdbc.sql("delete from player where id = ?").param(id).update());
    }

    @Test
    void returnsOnlyTheVillagesInTheRectangle() throws Exception {
        // the rectangle: x 2 … 4, y (MAP_SIZE − 7) … (MAP_SIZE − 4)
        long topLeft = barbarian(2, MAP_SIZE - 7);
        long bottomRight = barbarian(4, MAP_SIZE - 4);
        long left = barbarian(1, MAP_SIZE - 6);
        long right = barbarian(5, MAP_SIZE - 6);
        long above = barbarian(3, MAP_SIZE - 8);
        long below = barbarian(3, MAP_SIZE - 3);

        String body = TestPlayer.body(map(2, MAP_SIZE - 7, 3, 4).andExpect(status().isOk()));

        assertThat(ids(body))
                .contains(topLeft, bottomRight)
                .doesNotContain(left, right, above, below);
        List<Integer> xs = JsonPath.read(body, "$[*].x");
        List<Integer> ys = JsonPath.read(body, "$[*].y");
        assertThat(xs).allSatisfy(x -> assertThat(x).isBetween(2, 4));
        assertThat(ys).allSatisfy(y -> assertThat(y).isBetween(MAP_SIZE - 7, MAP_SIZE - 4));
    }

    @Test
    void aPlayerVillageHasItsOwner() throws Exception {
        String village = TestPlayer.body(player.village());
        int x = JsonPath.read(village, "$.x");
        int y = JsonPath.read(village, "$.y");
        String name = JsonPath.read(village, "$.name");
        String playerName =
                jdbc.sql("select name from player where id = ?")
                        .param(player.playerId)
                        .query(String.class)
                        .single();

        map(x, y, 1, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(player.villageId))
                .andExpect(jsonPath("$[0].x").value(x))
                .andExpect(jsonPath("$[0].y").value(y))
                .andExpect(jsonPath("$[0].name").value(name))
                .andExpect(jsonPath("$[0].playerId").value(player.playerId))
                .andExpect(jsonPath("$[0].playerName").value(playerName))
                .andExpect(jsonPath("$[0].npc").value(false))
                .andExpect(jsonPath("$[0].points").value(39));
    }

    @Test
    void barbarianAndNpcVillages() throws Exception {
        long barbarian = barbarian(MAP_SIZE - 4, 2);
        // at most 12 characters of the unique name: it is shorter while System.nanoTime() is small
        // (a freshly booted machine)
        String unique = Browser.uniqueUsername();
        String lordName = "Lord " + unique.substring(0, Math.min(12, unique.length()));
        long lord = npcPlayer(lordName);
        long lordVillage = village(lord, "Greywatch", MAP_SIZE - 3, 4, 250);

        String body = TestPlayer.body(map(MAP_SIZE - 4, 2, 2, 3).andExpect(status().isOk()));

        assertThat(ids(body)).containsExactly(barbarian, lordVillage); // ordered by row
        map(MAP_SIZE - 4, 2, 2, 3)
                .andExpect(jsonPath("$[0].name").value(nullValue()))
                .andExpect(jsonPath("$[0].playerId").value(nullValue()))
                .andExpect(jsonPath("$[0].playerName").value(nullValue()))
                .andExpect(jsonPath("$[0].npc").value(false))
                .andExpect(jsonPath("$[0].points").value(0))
                .andExpect(jsonPath("$[1].name").value("Greywatch"))
                .andExpect(jsonPath("$[1].playerId").value(lord))
                .andExpect(jsonPath("$[1].playerName").value(lordName))
                .andExpect(jsonPath("$[1].npc").value(true))
                .andExpect(jsonPath("$[1].points").value(250));
    }

    @Test
    void usedBannerRollsAreMarkedLikeInTheFarmList() throws Exception {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        long used = barbarian(MAP_SIZE - 6, MAP_SIZE - 6);
        long back = barbarian(MAP_SIZE - 5, MAP_SIZE - 6);
        long free = barbarian(MAP_SIZE - 4, MAP_SIZE - 6);
        long lord = npcPlayer("Lord " + Browser.uniqueUsername().substring(0, 8));
        long lordVillage = village(lord, "Ashford", MAP_SIZE - 6, MAP_SIZE - 5, 100);
        long lordOther = village(lord, "Brightmoor", MAP_SIZE - 5, MAP_SIZE - 5, 100);
        roll("V" + used, now.minus(Duration.ofHours(2)));
        roll("V" + back, now.minus(Duration.ofHours(25))); // the 24-hour window is over
        roll("P" + lord, now.minus(Duration.ofHours(10)));

        String body = TestPlayer.body(map(MAP_SIZE - 6, MAP_SIZE - 6, 3, 2));

        assertThat(ids(body)).containsExactly(used, back, free, lordVillage, lordOther);
        assertThat(rolls(body))
                .containsExactly(
                        now.plus(Duration.ofHours(22)).toString(),
                        null,
                        null,
                        // the roll against a player covers all his villages
                        now.plus(Duration.ofHours(14)).toString(),
                        now.plus(Duration.ofHours(14)).toString());

        // the player's own village: never, also with a roll stored against himself
        String own = TestPlayer.body(player.village());
        int x = JsonPath.read(own, "$.x");
        int y = JsonPath.read(own, "$.y");
        roll("P" + player.playerId, now);
        map(x, y, 1, 1).andExpect(jsonPath("$[0].nextBannerRollAt").value(nullValue()));

        jdbc.sql("update world_settings set banners_enabled = false where id = 1").update();
        worldService.reload();
        assertThat(rolls(TestPlayer.body(map(MAP_SIZE - 6, MAP_SIZE - 6, 3, 2))))
                .containsOnlyNulls();
    }

    @Test
    void aRectangleOverTheMapEdgeOnlyHasTheFieldsOnTheMap() throws Exception {
        long corner = barbarian(MAP_SIZE - 1, MAP_SIZE - 1);

        String body =
                TestPlayer.body(map(MAP_SIZE - 2, MAP_SIZE - 2, 60, 60).andExpect(status().isOk()));

        assertThat(ids(body)).contains(corner);
    }

    @Test
    void aRectangleOffTheMapIsEmpty() throws Exception {
        map(MAP_SIZE, 0, 20, 20).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        // x + w would overflow an int
        map(Integer.MAX_VALUE, Integer.MAX_VALUE, 60, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"w, 0, 0, 61, 20", "h, 0, 0, 20, 61", "w, 0, 0, 0, 20", "x, -1, 0, 20, 20"})
    void invalidParametersAre400(String field, int x, int y, int w, int h) throws Exception {
        map(x, y, w, h)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    void missingOrMalformedParametersAre400() throws Exception {
        player.browser
                .get("/api/map?x=0&y=0&w=20")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.browser
                .get("/api/map?x=0&y=0&w=abc&h=20")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void needsLogin() throws Exception {
        Browser.open(mockMvc)
                .get("/api/map?x=0&y=0&w=20&h=20")
                .andExpect(status().isUnauthorized());
    }

    private ResultActions map(int x, int y, int w, int h) throws Exception {
        return player.browser.get("/api/map?x=" + x + "&y=" + y + "&w=" + w + "&h=" + h);
    }

    private void roll(String loserKey, Instant at) {
        jdbc.sql("insert into banner_roll (winner_id, loser_key, rolled_at) values (?, ?, ?)")
                .params(player.playerId, loserKey, Timestamp.from(at))
                .update();
    }

    private static List<String> rolls(String body) {
        return JsonPath.read(body, "$[*].nextBannerRollAt");
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private long barbarian(int x, int y) {
        return village(null, null, x, y, 0);
    }

    private long village(Long playerId, String name, int x, int y, int points) {
        long id =
                jdbc.sql(
                                """
                                insert into village (player_id, name, x, y, wood, clay, iron,
                                                     resources_updated_at, loyalty_updated_at,
                                                     points, created_at)
                                values (cast(:playerId as bigint), cast(:name as varchar), :x, :y,
                                        0, 0, 0, now(), now(), :points, now())
                                returning id
                                """)
                        .param("playerId", playerId)
                        .param("name", name)
                        .param("x", x)
                        .param("y", y)
                        .param("points", points)
                        .query(Long.class)
                        .single();
        villageIds.add(id);
        return id;
    }

    private long npcPlayer(String name) {
        long id =
                jdbc.sql(
                                """
                                insert into player (name, npc, protected_until, created_at)
                                values (?, true, now(), now())
                                returning id
                                """)
                        .param(name)
                        .query(Long.class)
                        .single();
        playerIds.add(id);
        return id;
    }
}
