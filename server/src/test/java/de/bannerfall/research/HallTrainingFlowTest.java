package de.bannerfall.research;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.ResearchRules;
import de.bannerfall.rules.UnitLevels;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hall training (1.4, 02-game-rules §5.4): the Banner Hall trains the paladin's and the banner
 * thief's levels like the Smithy researches the others, also with unit levels switched off. The
 * clock is moved by hand; levels and resources are set with SQL.
 */
@IntegrationTest
class HallTrainingFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;
    @Autowired UnitLevelService unitLevels;
    @Autowired PlayerRepository players;

    private TestPlayer player;
    private int paladinSlots;
    private boolean banners;

    @BeforeEach
    void registerPlayer() throws Exception {
        paladinSlots = worldService.settings().paladinMaxSlots();
        banners = worldService.settings().bannersEnabled();
        setWorld(false, 5, true);
        player = TestPlayer.register(mockMvc);
        setLevel(BuildingType.HEADQUARTERS, 8);
        setLevel(BuildingType.BARRACKS, 5);
        setLevel(BuildingType.SMITHY, 10);
        setLevel(BuildingType.BANNER_HALL, 2);
        setLevel(BuildingType.WAREHOUSE, 25);
        setStock(50_000);
    }

    @AfterEach
    void restoreTheWorld() {
        setWorld(false, paladinSlots, banners);
    }

    @Test
    void theHallListsThePaladinAndTheThiefWithTheirNextTraining() throws Exception {
        String hall = body(player.bannerHall().andExpect(status().isOk()));

        assertThat(JsonPath.<Integer>read(hall, "$.hallLevel")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(hall, "$.units[*].unit"))
                .containsExactly("PALADIN", "BANNER_THIEF");
        assertThat(JsonPath.<List<String>>read(hall, "$.units[*].trainedIn"))
                .containsOnly("BANNER_HALL");
        String paladin = "$.units[0]";
        assertThat(JsonPath.<List<Integer>>read(hall, paladin + ".stats[*].attack"))
                .containsExactly(120, 126, 132);
        assertThat(JsonPath.<List<Integer>>read(hall, paladin + ".stats[*].defenseCavalry"))
                .containsExactly(150, 158, 165);
        assertThat(JsonPath.<Integer>read(hall, paladin + ".next.level")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(hall, paladin + ".next.cost.wood")).isEqualTo(5000);
        // 12 h at Banner Hall 1, shortened by the hall's level like a research by the smithy's
        long seconds =
                ResearchRules.seconds(
                        gameData.unit(UnitType.PALADIN).levelDef(2),
                        2,
                        worldService.settings().speed());
        assertThat(JsonPath.<Number>read(hall, paladin + ".next.durationSeconds").longValue())
                .isEqualTo(seconds);
        assertThat(JsonPath.<List<?>>read(hall, paladin + ".next.missing")).isEmpty();
        String thief = "$.units[1]";
        assertThat(JsonPath.<Integer>read(hall, thief + ".next.cost.wood")).isEqualTo(2000);
        assertThat(JsonPath.<Integer>read(hall, thief + ".next.cost.clay")).isEqualTo(2400);
        assertThat(JsonPath.<Integer>read(hall, thief + ".next.cost.iron")).isEqualTo(3000);
        assertThat((Object) JsonPath.read(hall, "$.running")).isNull();
        assertThat(JsonPath.<List<Double>>read(hall, "$.theftChancePerThief"))
                .containsExactly(0.03, 0.04, 0.05);
    }

    @Test
    void withUnitLevelsOffTheHallTrainsAndTheLevelCounts() throws Exception {
        String order = body(player.research(UnitType.PALADIN).andExpect(status().isCreated()));
        assertThat(stock()).isEqualTo(45_000);
        clock.set(Instant.parse(JsonPath.read(order, "$.endAt")));

        String hall = body(player.bannerHall());
        assertThat(JsonPath.<Integer>read(hall, "$.units[0].level")).isEqualTo(2);
        // the smithy shows the trained level too, its own units stay at level 1
        String smithy = body(player.smithy());
        assertThat(JsonPath.<Integer>read(smithy, "$.units[9].level")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(smithy, "$.units[0].maxLevel")).isEqualTo(1);
    }

    @Test
    void aBattleCountsTheHallsLevelsButNotTheSmithysWithUnitLevelsOff() {
        insertResearch(UnitType.PALADIN, 3);
        insertResearch(UnitType.BANNER_THIEF, 2);
        insertResearch(UnitType.SPEARMAN, 3);
        var owner = players.findById(player.playerId).orElseThrow();

        UnitLevels off = unitLevels.levelsOf(owner, clock.instant());
        assertThat(off.get(UnitType.PALADIN)).isEqualTo(3);
        assertThat(off.get(UnitType.BANNER_THIEF)).isEqualTo(2);
        assertThat(off.get(UnitType.SPEARMAN)).isEqualTo(1);

        setWorld(true, 5, true);
        assertThat(unitLevels.levelsOf(owner, clock.instant()).get(UnitType.SPEARMAN)).isEqualTo(3);
    }

    @Test
    void theHallTrainsOneAtATimeBesideTheSmithy() throws Exception {
        setWorld(true, 5, true);
        setLevel(BuildingType.BARRACKS, 1);
        player.research(UnitType.PALADIN).andExpect(status().isCreated());

        player.research(UnitType.BANNER_THIEF)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESEARCH_BUSY"));
        String hall = body(player.bannerHall());
        assertThat(JsonPath.<Boolean>read(hall, "$.units[1].next.villageBusy")).isTrue();
        assertThat(JsonPath.<String>read(hall, "$.running.unit")).isEqualTo("PALADIN");
        // the smithy is not busy with it
        player.research(UnitType.SPEARMAN).andExpect(status().isCreated());
        String smithy = body(player.smithy());
        assertThat(JsonPath.<String>read(smithy, "$.running.unit")).isEqualTo("SPEARMAN");
    }

    @Test
    void levelThreeNeedsBannerHallThree() throws Exception {
        insertResearch(UnitType.PALADIN, 2);

        player.research(UnitType.PALADIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        String hall = body(player.bannerHall());
        assertThat(JsonPath.<List<String>>read(hall, "$.units[0].next.missing[*].building"))
                .containsExactly("BANNER_HALL");
        assertThat(JsonPath.<List<Integer>>read(hall, "$.units[0].next.missing[*].level"))
                .containsExactly(3);
    }

    @Test
    void aWorldWithoutTheUnitTrainsNothingForIt() throws Exception {
        setWorld(false, 0, true);
        player.research(UnitType.PALADIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_DISABLED"));
        assertThat(JsonPath.<List<String>>read(body(player.bannerHall()), "$.units[*].unit"))
                .containsExactly("BANNER_THIEF");

        setWorld(false, 5, false);
        player.research(UnitType.BANNER_THIEF)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
        String paladinOnly = body(player.bannerHall());
        assertThat(JsonPath.<List<String>>read(paladinOnly, "$.units[*].unit"))
                .containsExactly("PALADIN");
        assertThat((Object) JsonPath.read(paladinOnly, "$.theftChancePerThief")).isNull();

        setWorld(false, 0, false);
        player.bannerHall()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
    }

    @Test
    void cancellingATrainingGivesTheCostBack() throws Exception {
        String order = body(player.research(UnitType.BANNER_THIEF).andExpect(status().isCreated()));

        player.cancelResearch(JsonPath.<Number>read(order, "$.id").longValue())
                .andExpect(status().isNoContent());

        assertThat(stock()).isEqualTo(50_000);
        assertThat((Object) JsonPath.read(body(player.bannerHall()), "$.running")).isNull();
    }

    private void insertResearch(UnitType unit, int level) {
        jdbc.sql(
                        """
                        insert into research_order (player_id, village_id, unit, level, start_at,
                            end_at, cost_wood, cost_clay, cost_iron, created_at)
                        values (?, ?, ?, ?, ?, ?, 0, 0, 0, ?)
                        """)
                .params(
                        player.playerId,
                        player.villageId,
                        unit.name(),
                        level,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant()))
                .update();
    }

    private void setWorld(boolean unitLevels, int paladinSlots, boolean banners) {
        jdbc.sql(
                        "update world_settings set unit_levels_enabled = ?, paladin_max_slots = ?,"
                                + " banners_enabled = ?")
                .params(unitLevels, paladinSlots, banners)
                .update();
        worldService.reload();
    }

    private void setLevel(BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setStock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private int stock() {
        return jdbc.sql("select floor(wood)::int from village where id = ?")
                .param(player.villageId)
                .query(Integer.class)
                .single();
    }
}
