package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tags per village (1.7, 02-game-rules §26): {@code PUT /api/villages/{id}/tag} and {@code
 * /api/me}.
 */
@IntegrationTest
class VillageTagFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    @Test
    void aTagIsSavedInCapitalsAndShownInMeToItsOwnerOnly() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);

        tag(player, player.villageId, "  off ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villageId").value(player.villageId))
                .andExpect(jsonPath("$.tag").value("OFF"));

        player.browser.get("/api/me").andExpect(jsonPath("$.villages[0].tag").value("OFF"));
        other.browser.get("/api/me").andExpect(jsonPath("$.villages[0].tag").doesNotExist());
        // the village itself and its map entry carry no tag
        assertThat(TestPlayer.body(player.village())).doesNotContain("OFF");
    }

    @Test
    void writingAgainReplacesTheTagAndABlankTagRemovesIt() throws Exception {
        tag(player, player.villageId, "off").andExpect(status().isOk());
        tag(player, player.villageId, "Def").andExpect(status().isOk());
        player.browser.get("/api/me").andExpect(jsonPath("$.villages[0].tag").value("DEF"));
        assertThat(rows(player)).isEqualTo(1);

        tag(player, player.villageId, "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").doesNotExist());
        player.browser.get("/api/me").andExpect(jsonPath("$.villages[0].tag").doesNotExist());
        assertThat(rows(player)).isZero();
        // removing what isn't there is fine too
        tag(player, player.villageId, "").andExpect(status().isOk());
    }

    @Test
    void aTagWithTooManyCharactersOrOddOnesIsRejected() throws Exception {
        for (String bad : new String[] {"TOOLONG", "O K", "ÄÖ", "a-b"}) {
            tag(player, player.villageId, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        assertThat(rows(player)).isZero();
    }

    @Test
    void onlyOwnVillagesTakeATag() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        tag(player, other.villageId, "OFF")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        tag(player, 987_654_321L, "OFF")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        assertThat(rows(other)).isZero();
    }

    @Test
    void aTagOfAFormerOwnerIsNeverRead() throws Exception {
        tag(player, player.villageId, "OFF").andExpect(status().isOk());
        TestPlayer other = TestPlayer.register(mockMvc);
        // taken over without the cleanup a conquest does: the row is stale and must not show up
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(other.playerId, player.villageId)
                .update();
        String me = TestPlayer.body(other.browser.get("/api/me"));
        assertThat(JsonPath.<List<String>>read(me, "$.villages[*].tag")).containsOnlyNulls();
        // the new owner may set his own; the row changes hands
        tag(other, player.villageId, "def").andExpect(status().isOk());
        me = TestPlayer.body(other.browser.get("/api/me"));
        assertThat(JsonPath.<List<String>>read(me, "$.villages[*].tag")).contains("DEF");
        assertThat(rows(other)).isEqualTo(1);
        assertThat(rows(player)).isZero();
    }

    private ResultActions tag(TestPlayer who, long villageId, String tag) throws Exception {
        return who.browser.put(
                "/api/villages/" + villageId + "/tag", JSON.writeValueAsString(Map.of("tag", tag)));
    }

    private long rows(TestPlayer who) {
        return jdbc.sql("select count(*) from village_tag where player_id = ?")
                .param(who.playerId)
                .query(Long.class)
                .single();
    }
}
