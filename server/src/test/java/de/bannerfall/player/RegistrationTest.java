package de.bannerfall.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.account.Account;
import de.bannerfall.account.AccountRepository;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Registering creates account, player and starting village together (M3a). */
@IntegrationTest
class RegistrationTest {

    /** Points of the start levels: Town Hall 10 + wood/clay/iron 6 each + farm 5 + warehouse 6. */
    private static final int START_POINTS = 39;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired AccountRepository accounts;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired UnitStackRepository stacks;
    @Autowired WorldService worldService;
    @Autowired JdbcClient jdbc;

    @Test
    void registrationCreatesThePlayerAndItsStartingVillage() throws Exception {
        String name = Browser.uniqueUsername();
        Instant now = clock.instant();
        Instant protectedUntil = now.plus(Duration.ofHours(72));

        String body =
                Browser.open(mockMvc)
                        .register(name)
                        .andExpect(jsonPath("$.playerId").isNumber())
                        .andExpect(jsonPath("$.points").value(START_POINTS))
                        .andExpect(jsonPath("$.protectedUntil").value(protectedUntil.toString()))
                        .andExpect(jsonPath("$.needsRespawn").value(false))
                        .andExpect(jsonPath("$.villages.length()").value(1))
                        .andExpect(jsonPath("$.villages[0].name").value(name + "'s village"))
                        .andExpect(jsonPath("$.villages[0].points").value(START_POINTS))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Player player = players.findById(longAt(body, "$.playerId")).orElseThrow();
        assertThat(player.getName()).isEqualTo(name);
        assertThat(player.isNpc()).isFalse();
        assertThat(player.getVillageCount()).isEqualTo(1);
        assertThat(player.getPoints()).isEqualTo(START_POINTS);
        assertThat(player.getProtectedUntil()).isEqualTo(protectedUntil);

        long villageId = longAt(body, "$.villages[0].id");
        Village village = villages.findById(villageId).orElseThrow();
        assertThat(village.getWood()).isEqualTo(500);
        assertThat(village.getClay()).isEqualTo(500);
        assertThat(village.getIron()).isEqualTo(500);
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(now);
        assertThat(village.getLoyalty()).isEqualTo(100);
        assertThat(village.getBuildings().get(BuildingType.HEADQUARTERS)).isEqualTo(1);
        assertThat(village.getBuildings().get(BuildingType.WAREHOUSE)).isEqualTo(1);
        assertThat(village.getBuildings().get(BuildingType.BARRACKS)).isZero();
        assertThat(stacks.findHomeStack(villageId))
                .hasValueSatisfying(stack -> assertThat(stack.getUnits().isEmpty()).isTrue());
    }

    @Test
    void theVillageIsInsideTheMapAndKeepsItsDistance() throws Exception {
        Coordinates spot = spotOf(Browser.open(mockMvc).register(Browser.uniqueUsername()));

        int mapSize = worldService.settings().mapSize();
        assertThat(spot.x()).isBetween(0, mapSize - 1);
        assertThat(spot.y()).isBetween(0, mapSize - 1);
        for (Coordinates other : villages.findAllCoordinates()) {
            if (!other.equals(spot)) {
                assertThat(spot.distanceTo(other))
                        .as("distance to %s", other)
                        .isGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    void twoRegistrationsGetDifferentSpots() throws Exception {
        Coordinates first = spotOf(Browser.open(mockMvc).register(Browser.uniqueUsername()));
        Coordinates second = spotOf(Browser.open(mockMvc).register(Browser.uniqueUsername()));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aFullWorldLeavesNoAccountBehind() throws Exception {
        String name = Browser.uniqueUsername();
        setMapSize(3); // the spawn circle (radius ≥ 6) never touches a 3×3 map
        try {
            Browser.open(mockMvc)
                    .register(name, Browser.PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WORLD_FULL"));
        } finally {
            setMapSize(TestWorldConfiguration.MAP_SIZE); // the context (and database) is shared
        }

        assertThat(accounts.existsByUsernameKey(Account.keyOf(name))).isFalse();
    }

    private Coordinates spotOf(ResultActions registration) throws Exception {
        String body = registration.andReturn().getResponse().getContentAsString();
        return villages.findById(longAt(body, "$.villages[0].id")).orElseThrow().coordinates();
    }

    private static long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private void setMapSize(int mapSize) {
        jdbc.sql("update world_settings set map_size = ? where id = 1").param(mapSize).update();
        worldService.reload();
    }
}
