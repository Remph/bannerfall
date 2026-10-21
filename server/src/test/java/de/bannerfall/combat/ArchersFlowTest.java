package de.bannerfall.combat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
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
 * Archers (1.6, 02-game-rules §5.6): the world setting decides whether they can be recruited and
 * researched, and in a real battle they meet the defenders' defence against archers. Luck is off.
 */
@IntegrationTest
class ArchersFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;

    private WorldSettings before;
    private TestPlayer attacker;
    private TestPlayer defender;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        before = worldService.settings();
        jdbc.sql("update world_settings set luck_range = 0").update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set luck_range = ?, archers_enabled = ?,"
                                + " unit_levels_enabled = ?")
                .params(before.luckRange(), before.archersEnabled(), before.unitLevelsEnabled())
                .update();
        worldService.reload();
    }

    @Test
    void theWorldSettingDecidesWhetherArchersCanBeRecruited() throws Exception {
        readyToRecruit(attacker);

        // off (the default): not listed, not recruited
        assertThat(units(attacker, "BARRACKS")).doesNotContain("ARCHER");
        assertThat(units(attacker, "STABLE")).doesNotContain("MOUNTED_ARCHER");
        attacker.recruit(UnitType.ARCHER, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHERS_DISABLED"));
        attacker.recruit(UnitType.MOUNTED_ARCHER, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHERS_DISABLED"));

        setArchers(true);
        // the archer after the barracks' other units, the mounted archer after the stable's
        assertThat(units(attacker, "BARRACKS"))
                .containsExactly("SPEARMAN", "SWORDSMAN", "AXEMAN", "ARCHER");
        assertThat(units(attacker, "STABLE"))
                .containsExactly("SCOUT", "LIGHT_CAVALRY", "HEAVY_CAVALRY", "MOUNTED_ARCHER");
        attacker.recruitOk(UnitType.ARCHER, 2);
        attacker.recruitOk(UnitType.MOUNTED_ARCHER, 1);
    }

    @Test
    void theirRequirementsCount() throws Exception {
        setArchers(true);
        readyToRecruit(attacker);
        setLevel(attacker, BuildingType.SMITHY, 4);

        attacker.recruit(UnitType.ARCHER, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        attacker.recruit(UnitType.MOUNTED_ARCHER, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void theSmithyShowsAndResearchesThemOnlyInAWorldWithArchers() throws Exception {
        jdbc.sql("update world_settings set unit_levels_enabled = true").update();
        worldService.reload();
        readyToRecruit(attacker);
        setLevel(attacker, BuildingType.SMITHY, 10);

        assertThat(JsonPath.<List<String>>read(body(attacker.smithy()), "$.units[*].unit"))
                .doesNotContain("ARCHER", "MOUNTED_ARCHER");
        attacker.research(UnitType.ARCHER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHERS_DISABLED"));

        setArchers(true);
        String smithy = body(attacker.smithy());
        assertThat(
                        JsonPath.<List<Integer>>read(
                                smithy, "$.units[?(@.unit == 'ARCHER')].stats[0].defenseArcher"))
                .containsExactly(5);
        assertThat(
                        JsonPath.<List<String>>read(
                                smithy, "$.units[?(@.unit == 'MOUNTED_ARCHER')].attackType"))
                .containsExactly("ARCHER");
        attacker.research(UnitType.ARCHER).andExpect(status().isCreated());
    }

    @Test
    void mountedArchersMeetTheSpearmensDefenceAgainstArchers() throws Exception {
        setArchers(true);
        setUnits(attacker, Map.of(UnitType.MOUNTED_ARCHER, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        // A = 50 × 110 = 5500; D = 100 × 20 + 20 = 2020: (2020 / 5500)^1.5 = 0.2226
        Instant arrival = attack(coordinates(defender), "{\"MOUNTED_ARCHER\":50}");

        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat(JsonPath.<Integer>read(report, "$.payload.attackerLosses.MOUNTED_ARCHER"))
                .isEqualTo(11);
    }

    @Test
    void archersThatExistKeepFightingWhenTheHostSwitchesThemOff() throws Exception {
        setUnits(attacker, Map.of(UnitType.MOUNTED_ARCHER, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setArchers(false);
        Instant arrival = attack(coordinates(defender), "{\"MOUNTED_ARCHER\":50}");

        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat(JsonPath.<Integer>read(report, "$.payload.attackerLosses.MOUNTED_ARCHER"))
                .isEqualTo(11);
    }

    // ---- helpers ----

    private void setArchers(boolean enabled) {
        jdbc.sql("update world_settings set archers_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    /** Barracks 5, Stable 10, Smithy 15, a big farm and plenty of resources. */
    private void readyToRecruit(TestPlayer player) {
        setLevel(player, BuildingType.BARRACKS, 5);
        setLevel(player, BuildingType.STABLE, 10);
        setLevel(player, BuildingType.SMITHY, 15);
        setLevel(player, BuildingType.FARM, 20);
        setLevel(player, BuildingType.WAREHOUSE, 20);
        jdbc.sql(
                        "update village set wood = 50000, clay = 50000, iron = 50000,"
                                + " resources_updated_at = ? where id = ?")
                .params(Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private void setLevel(TestPlayer player, BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    /** The units the recruitment page lists for {@code building}, in its order. */
    private static List<String> units(TestPlayer player, String building) throws Exception {
        return JsonPath.read(
                body(player.recruitment()), "$[?(@.building == '" + building + "')].units[*].unit");
    }

    private Instant attack(Coordinates target, String unitsJson) throws Exception {
        String sent =
                body(attacker.command("ATTACK", target, unitsJson).andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sent, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    /** The home stack has exactly these units. */
    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set u_"
                                    + type.name().toLowerCase(Locale.ROOT)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
