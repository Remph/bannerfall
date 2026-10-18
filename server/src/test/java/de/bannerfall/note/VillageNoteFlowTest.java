package de.bannerfall.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notes per village (1.4, 02-game-rules §23): {@code PUT /api/villages/{id}/note} and the note in
 * the map chunks. The barbarian villages made here sit near a corner of the map, where no spawn
 * reaches, and are removed afterwards (with their notes).
 */
@IntegrationTest
class VillageNoteFlowTest {

    private static final int MAP_SIZE = TestWorldConfiguration.MAP_SIZE;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    private TestPlayer player;
    private final List<Long> villageIds = new ArrayList<>();

    @BeforeEach
    void registerPlayer() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void removeTestVillages() {
        villageIds.forEach(id -> jdbc.sql("delete from village where id = ?").param(id).update());
    }

    @Test
    void aNoteShowsOnTheMapForItsAuthorOnly() throws Exception {
        long barbarian = barbarian(MAP_SIZE - 3, 2);
        TestPlayer other = TestPlayer.register(mockMvc);

        note(player, barbarian, "  Wall 3, spears only.\nFarm with A every 2 h.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villageId").value(barbarian))
                .andExpect(
                        jsonPath("$.text").value("Wall 3, spears only.\nFarm with A every 2 h."));

        assertThat(noteOnMap(player, barbarian))
                .isEqualTo("Wall 3, spears only.\nFarm with A every 2 h.");
        assertThat(noteOnMap(other, barbarian)).isNull();

        // the other player's own note on the same village is his alone
        note(other, barbarian, "Taken by Nora's farm runs").andExpect(status().isOk());
        assertThat(noteOnMap(other, barbarian)).isEqualTo("Taken by Nora's farm runs");
        assertThat(noteOnMap(player, barbarian))
                .isEqualTo("Wall 3, spears only.\nFarm with A every 2 h.");
    }

    @Test
    void writingAgainReplacesTheNoteAndABlankTextDeletesIt() throws Exception {
        long barbarian = barbarian(MAP_SIZE - 3, 3);
        note(player, barbarian, "first").andExpect(status().isOk());
        note(player, barbarian, "second").andExpect(status().isOk());
        assertThat(noteOnMap(player, barbarian)).isEqualTo("second");

        note(player, barbarian, " \n ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").doesNotExist());
        assertThat(noteOnMap(player, barbarian)).isNull();
        assertThat(rows(player)).isZero();
        // deleting what isn't there is fine too
        note(player, barbarian, "").andExpect(status().isOk());
    }

    @Test
    void ownVillagesTakeNotesAndANoteStaysWhenTheVillageChangesOwner() throws Exception {
        note(player, player.villageId, "Off village").andExpect(status().isOk());
        long barbarian = barbarian(MAP_SIZE - 3, 4);
        note(player, barbarian, "mine soon").andExpect(status().isOk());

        // taken over (as a conquest would): the note belongs to the player and the village
        jdbc.sql("update village set player_id = ?, name = 'Taken' where id = ?")
                .params(player.playerId, barbarian)
                .update();

        assertThat(noteOnMap(player, barbarian)).isEqualTo("mine soon");
        String body = TestPlayer.body(player.browser.get(player.villagePath()));
        int x = JsonPath.read(body, "$.x");
        int y = JsonPath.read(body, "$.y");
        String map =
                TestPlayer.body(player.browser.get("/api/map?x=" + x + "&y=" + y + "&w=1&h=1"));
        assertThat(JsonPath.<List<String>>read(map, "$[*].note")).containsExactly("Off village");
    }

    @Test
    void theFarmListShowsTheNoteOnItsRows() throws Exception {
        long noted = barbarian(MAP_SIZE - 3, 6);
        long plain = barbarian(MAP_SIZE - 3, 7);
        note(player, noted, "Wall 3").andExpect(status().isOk());
        String farmList = player.villagePath() + "/farm-targets";
        String addOne = "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"AXEMAN\":5}}";

        player.browser
                .post(farmList, addOne.formatted(MAP_SIZE - 3, 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Wall 3"));
        player.browser
                .post(farmList, addOne.formatted(MAP_SIZE - 3, 7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").doesNotExist());

        String body = TestPlayer.body(player.browser.get(farmList));
        assertThat(JsonPath.<List<Number>>read(body, "$[*].target.id"))
                .containsExactly((Number) (int) noted, (Number) (int) plain);
        assertThat(JsonPath.<List<String>>read(body, "$[*].note")).containsExactly("Wall 3", null);
    }

    @Test
    void limitsAndUnknownVillages() throws Exception {
        long barbarian = barbarian(MAP_SIZE - 3, 5);

        // 500 characters count code points: 500 emoji (1000 UTF-16 chars) are fine, 501 are not
        note(player, barbarian, "🏰".repeat(500)).andExpect(status().isOk());
        note(player, barbarian, "a".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        // line breaks and tabs are fine, other control characters are not
        note(player, barbarian, "bell\u0007")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        assertThat(noteOnMap(player, barbarian)).isEqualTo("🏰".repeat(500));

        player.browser
                .put("/api/villages/" + barbarian + "/note", "{}")
                .andExpect(status().isBadRequest());
        note(player, 99_999_999L, "nowhere")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    private ResultActions note(TestPlayer who, long villageId, String text) throws Exception {
        return who.browser.put(
                "/api/villages/" + villageId + "/note",
                JSON.writeValueAsString(Map.of("text", text)));
    }

    /** The note on the village's map entry, as {@code who} gets it. */
    private String noteOnMap(TestPlayer who, long villageId) throws Exception {
        Map<String, Object> village =
                jdbc.sql("select x, y from village where id = ?")
                        .param(villageId)
                        .query()
                        .singleRow();
        String body =
                TestPlayer.body(
                        who.browser.get(
                                "/api/map?x="
                                        + village.get("x")
                                        + "&y="
                                        + village.get("y")
                                        + "&w=1&h=1"));
        List<String> notes = JsonPath.read(body, "$[*].note");
        assertThat(notes).hasSize(1);
        return notes.getFirst();
    }

    private long rows(TestPlayer who) {
        return jdbc.sql("select count(*) from village_note where player_id = ?")
                .param(who.playerId)
                .query(Long.class)
                .single();
    }

    private long barbarian(int x, int y) {
        long id =
                jdbc.sql(
                                """
                                insert into village (x, y, wood, clay, iron, resources_updated_at,
                                                     loyalty_updated_at, points, created_at)
                                values (:x, :y, 0, 0, 0, now(), now(), 0, now())
                                returning id
                                """)
                        .param("x", x)
                        .param("y", y)
                        .query(Long.class)
                        .single();
        villageIds.add(id);
        return id;
    }
}
