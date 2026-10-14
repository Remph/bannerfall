package de.bannerfall.research;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
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
import de.bannerfall.rules.ResearchRules;
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
 * Unit levels (M30, 02-game-rules §5.4): the smithy page, starting, finishing and cancelling a
 * research. The clock is moved by hand; levels and resources are set with SQL. Battles with levels
 * are in {@code BattleFlowTest}.
 */
@IntegrationTest
class ResearchFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        setLevelsEnabled(true);
        player = TestPlayer.register(mockMvc);
        // everything a spearman research needs: Smithy 10 and barracks, a roomy warehouse, stock
        setLevel(BuildingType.SMITHY, 10);
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.WAREHOUSE, 15);
        setStock(10_000, 10_000, 10_000);
    }

    @AfterEach
    void restoreTheWorld() {
        setLevelsEnabled(false);
    }

    // ---- the smithy page ----

    @Test
    void theSmithyListsEveryUnitWithItsStatsAndLevels() throws Exception {
        String smithy = body(player.smithy().andExpect(status().isOk()));

        assertThat(JsonPath.<Boolean>read(smithy, "$.levelsEnabled")).isTrue();
        assertThat(JsonPath.<Integer>read(smithy, "$.smithyLevel")).isEqualTo(10);
        assertThat(JsonPath.<List<String>>read(smithy, "$.units[*].unit"))
                .containsExactly(
                        "SPEARMAN",
                        "SWORDSMAN",
                        "AXEMAN",
                        "SCOUT",
                        "LIGHT_CAVALRY",
                        "HEAVY_CAVALRY",
                        "RAM",
                        "CATAPULT",
                        "NOBLE",
                        "PALADIN",
                        "BANNER_THIEF");
        String spear = "$.units[0]";
        assertThat(JsonPath.<Integer>read(smithy, spear + ".level")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(smithy, spear + ".maxLevel")).isEqualTo(3);
        assertThat(JsonPath.<List<Integer>>read(smithy, spear + ".stats[*].attack"))
                .containsExactly(10, 11, 12);
        assertThat(JsonPath.<List<Integer>>read(smithy, spear + ".stats[*].defenseInfantry"))
                .containsExactly(15, 17, 18);
        assertThat(JsonPath.<List<Integer>>read(smithy, spear + ".stats[*].defenseCavalry"))
                .containsExactly(40, 44, 48);
        assertThat(JsonPath.<String>read(smithy, spear + ".attackType")).isEqualTo("INFANTRY");
        assertThat(JsonPath.<Integer>read(smithy, spear + ".carry")).isEqualTo(25);
        // minutes per field in this world: 18 ÷ (speed × unitSpeed)
        double speed = worldService.settings().speed() * worldService.settings().unitSpeed();
        assertThat(JsonPath.<Number>read(smithy, spear + ".minutesPerField").doubleValue())
                .isEqualTo(18 / speed);
        // the next research: level 2 costs 20 times the recruit cost, 12 h at Smithy 1
        assertThat(JsonPath.<Integer>read(smithy, spear + ".next.level")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(smithy, spear + ".next.cost.wood")).isEqualTo(1000);
        assertThat(JsonPath.<Integer>read(smithy, spear + ".next.cost.clay")).isEqualTo(600);
        assertThat(JsonPath.<Integer>read(smithy, spear + ".next.cost.iron")).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(smithy, spear + ".next.affordable")).isTrue();
        assertThat(JsonPath.<List<?>>read(smithy, spear + ".next.missing")).isEmpty();
        assertThat(JsonPath.<Boolean>read(smithy, spear + ".next.villageBusy")).isFalse();
        // the scout has level 2 only (an upgraded scout), siege, nobles and the paladin have none
        String scout = "$.units[3]";
        assertThat(JsonPath.<Integer>read(smithy, scout + ".maxLevel")).isEqualTo(2);
        assertThat(JsonPath.<List<Integer>>read(smithy, scout + ".stats[*].defenseInfantry"))
                .containsExactly(2, 3);
        assertThat(JsonPath.<Integer>read(smithy, scout + ".next.level")).isEqualTo(2);
        for (int i : new int[] {6, 7, 8, 9}) {
            assertThat(JsonPath.<Integer>read(smithy, "$.units[" + i + "].maxLevel")).isEqualTo(1);
            assertThat((Object) JsonPath.read(smithy, "$.units[" + i + "].next")).isNull();
        }
    }

    @Test
    void aWorldWithoutPaladinsDoesNotListHim() throws Exception {
        int maxSlots = worldService.settings().paladinMaxSlots();
        setPaladinMaxSlots(0);
        try {
            String smithy = body(player.smithy().andExpect(status().isOk()));

            assertThat(JsonPath.<List<String>>read(smithy, "$.units[*].unit"))
                    .hasSize(10) // the nine others and the banner thief (M32b)
                    .doesNotContain("PALADIN");
        } finally {
            setPaladinMaxSlots(maxSlots);
        }
    }

    @Test
    void whatIsMissingIsListed() throws Exception {
        setLevel(BuildingType.SMITHY, 3);
        setStock(10, 10, 10);

        String smithy = body(player.smithy());

        String swordsman = "$.units[1].next";
        assertThat(JsonPath.<Boolean>read(smithy, swordsman + ".affordable")).isFalse();
        assertThat(JsonPath.<List<String>>read(smithy, swordsman + ".missing[*].building"))
                .containsExactly("SMITHY");
        assertThat(JsonPath.<List<Integer>>read(smithy, swordsman + ".missing[*].level"))
                .containsExactly(10);
        assertThat(JsonPath.<List<String>>read(smithy, swordsman + ".requires[*].building"))
                .contains("BARRACKS", "SMITHY");
    }

    @Test
    void withLevelsOffTheSmithyShowsOnlyTheBaseStats() throws Exception {
        setLevelsEnabled(false);

        String smithy = body(player.smithy().andExpect(status().isOk()));

        assertThat(JsonPath.<Boolean>read(smithy, "$.levelsEnabled")).isFalse();
        for (int i = 0; i < 10; i++) {
            String unit = "$.units[" + i + "]";
            assertThat(JsonPath.<Integer>read(smithy, unit + ".level")).isEqualTo(1);
            assertThat(JsonPath.<Integer>read(smithy, unit + ".maxLevel")).isEqualTo(1);
            assertThat(JsonPath.<List<?>>read(smithy, unit + ".stats")).hasSize(1);
            assertThat((Object) JsonPath.read(smithy, unit + ".next")).isNull();
        }
        assertThat(JsonPath.<List<Integer>>read(smithy, "$.units[2].stats[*].attack"))
                .containsExactly(40);
        assertThat(JsonPath.<List<Integer>>read(smithy, "$.units[8].stats[*].defenseInfantry"))
                .containsExactly(100);
    }

    @Test
    void anotherPlayersVillageIsNotFound() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);

        other.browser
                .get("/api/villages/" + player.villageId + "/smithy")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(
                        "/api/villages/" + player.villageId + "/research-orders",
                        "{\"unit\":\"SPEARMAN\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    // ---- starting ----

    @Test
    void aResearchCostsResourcesAndTakesTimeFromTheSmithyLevel() throws Exception {
        Instant now = clock.instant();
        setStock(5000, 5000, 5000);

        String order = body(player.research(UnitType.SPEARMAN).andExpect(status().isCreated()));

        long seconds =
                ResearchRules.seconds(
                        gameData.unit(UnitType.SPEARMAN).levelDef(2),
                        10,
                        worldService.settings().speed());
        assertThat(JsonPath.<String>read(order, "$.unit")).isEqualTo("SPEARMAN");
        assertThat(JsonPath.<Integer>read(order, "$.level")).isEqualTo(2);
        assertThat(Instant.parse(JsonPath.read(order, "$.startAt"))).isEqualTo(now);
        assertThat(Instant.parse(JsonPath.read(order, "$.endAt")))
                .isEqualTo(now.plusSeconds(seconds));
        player.village()
                .andExpect(jsonPath("$.resources.wood").value(4000.0))
                .andExpect(jsonPath("$.resources.clay").value(4400.0))
                .andExpect(jsonPath("$.resources.iron").value(4800.0));
        String smithy = body(player.smithy());
        assertThat(JsonPath.<Integer>read(smithy, "$.running.id"))
                .isEqualTo(JsonPath.<Integer>read(order, "$.id"));
        assertThat(JsonPath.<String>read(smithy, "$.units[0].researching.unit"))
                .isEqualTo("SPEARMAN");
        // being researched: no second "next", the level is still 1
        assertThat((Object) JsonPath.read(smithy, "$.units[0].next")).isNull();
        assertThat(JsonPath.<Integer>read(smithy, "$.units[0].level")).isEqualTo(1);
    }

    @Test
    void theLevelCountsFromTheEndOfTheResearchOnAndNextComesAfter() throws Exception {
        Instant end =
                Instant.parse(JsonPath.read(body(player.research(UnitType.SPEARMAN)), "$.endAt"));

        clock.set(end.minusSeconds(1));
        assertThat(JsonPath.<Integer>read(body(player.smithy()), "$.units[0].level")).isEqualTo(1);

        clock.set(end);
        String smithy = body(player.smithy());
        assertThat(JsonPath.<Integer>read(smithy, "$.units[0].level")).isEqualTo(2);
        assertThat((Object) JsonPath.read(smithy, "$.running")).isNull();
        assertThat(JsonPath.<Integer>read(smithy, "$.units[0].next.level")).isEqualTo(3);
        // level 3 needs Smithy 15
        assertThat(JsonPath.<List<Integer>>read(smithy, "$.units[0].next.missing[*].level"))
                .containsExactly(15);
    }

    @Test
    void levelThreeIsTheLastOne() throws Exception {
        setLevel(BuildingType.SMITHY, 15);
        researchToTheEnd(UnitType.SPEARMAN);
        researchToTheEnd(UnitType.SPEARMAN);

        String smithy = body(player.smithy());
        assertThat(JsonPath.<Integer>read(smithy, "$.units[0].level")).isEqualTo(3);
        assertThat((Object) JsonPath.read(smithy, "$.units[0].next")).isNull();
        player.research(UnitType.SPEARMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_LEVEL_REACHED"));
    }

    @Test
    void aUnitWithoutLevelsCannotBeResearched() throws Exception {
        player.research(UnitType.NOBLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_LEVEL_REACHED"));
    }

    @Test
    void thePaladinHasNoLevelsToResearch() throws Exception {
        player.research(UnitType.PALADIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_LEVEL_REACHED"));
    }

    @Test
    void withLevelsOffNothingCanBeResearched() throws Exception {
        setLevelsEnabled(false);

        player.research(UnitType.SPEARMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNIT_LEVELS_DISABLED"));
    }

    @Test
    void theSmithyDoesOneResearchAtATime() throws Exception {
        setLevel(BuildingType.BARRACKS, 2);
        setLevel(BuildingType.SMITHY, 10);
        player.research(UnitType.SPEARMAN).andExpect(status().isCreated());

        player.research(UnitType.SWORDSMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESEARCH_BUSY"));
        String smithy = body(player.smithy());
        assertThat(JsonPath.<Boolean>read(smithy, "$.units[1].next.villageBusy")).isTrue();
    }

    @Test
    void aUnitIsResearchedInOneVillageAtATime() throws Exception {
        // the player is researching spearmen in another village of his
        TestPlayer other = TestPlayer.register(mockMvc);
        insertResearch(
                player.playerId,
                other.villageId,
                UnitType.SPEARMAN,
                2,
                clock.instant().plusSeconds(600));

        player.research(UnitType.SPEARMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESEARCH_BUSY"));
        String smithy = body(player.smithy());
        assertThat((Object) JsonPath.read(smithy, "$.running")).isNull();
        assertThat(JsonPath.<Number>read(smithy, "$.units[0].researching.villageId").longValue())
                .isEqualTo(other.villageId);
        // another unit is fine
        setLevel(BuildingType.BARRACKS, 2);
        player.research(UnitType.SWORDSMAN).andExpect(status().isCreated());
    }

    @Test
    void theRequirementsOfTheUnitAndTheLevelMustBeMet() throws Exception {
        setLevel(BuildingType.SMITHY, 9);
        player.research(UnitType.SPEARMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        // Smithy 10, but the swordsman needs Smithy 1 and barracks 1 only: the unit's own are met;
        // an axeman needs barracks 2
        setLevel(BuildingType.SMITHY, 10);
        player.research(UnitType.AXEMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        // the heavy cavalry is not even recruitable here
        player.research(UnitType.HEAVY_CAVALRY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void theStockMustPayForIt() throws Exception {
        setStock(999, 10_000, 10_000);

        player.research(UnitType.SPEARMAN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        // nothing was taken
        player.village().andExpect(jsonPath("$.resources.clay").value(10_000.0));
    }

    // ---- cancelling ----

    @Test
    void cancellingGivesTheWholeCostBackAndFreesTheSmithy() throws Exception {
        setStock(5000, 5000, 5000);
        long id = number(body(player.research(UnitType.SPEARMAN)), "$.id").longValue();

        player.cancelResearch(id).andExpect(status().isNoContent());

        player.village()
                .andExpect(jsonPath("$.resources.wood").value(5000.0))
                .andExpect(jsonPath("$.resources.clay").value(5000.0))
                .andExpect(jsonPath("$.resources.iron").value(5000.0));
        assertThat((Object) JsonPath.read(body(player.smithy()), "$.running")).isNull();
        player.research(UnitType.SPEARMAN).andExpect(status().isCreated());
    }

    @Test
    void theRefundIsReceivedLikeIncomingResources() throws Exception {
        // warehouse level 15 holds 1000 × 1.229^14 = 18 252; what doesn't fit is lost
        setStock(5000, 5000, 5000);
        long id = number(body(player.research(UnitType.SPEARMAN)), "$.id").longValue();
        setLevel(BuildingType.WAREHOUSE, 1);
        setStock(900, 900, 900);

        player.cancelResearch(id).andExpect(status().isNoContent());

        player.village()
                .andExpect(jsonPath("$.resources.wood").value(1000.0))
                .andExpect(jsonPath("$.resources.clay").value(900.0 + 100))
                .andExpect(jsonPath("$.resources.iron").value(900.0 + 100));
    }

    @Test
    void aFinishedOrUnknownResearchCannotBeCancelled() throws Exception {
        long id = number(body(player.research(UnitType.SPEARMAN)), "$.id").longValue();
        Instant end =
                jdbc.sql("select end_at from research_order where id = ?")
                        .param(id)
                        .query(Timestamp.class)
                        .single()
                        .toInstant();
        clock.set(end);

        player.cancelResearch(id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESEARCH_ORDER_NOT_FOUND"));
        player.cancelResearch(id + 1_000_000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESEARCH_ORDER_NOT_FOUND"));
        // the level stays
        assertThat(JsonPath.<Integer>read(body(player.smithy()), "$.units[0].level")).isEqualTo(2);
    }

    @Test
    void aForeignResearchCannotBeCancelled() throws Exception {
        long id = number(body(player.research(UnitType.SPEARMAN)), "$.id").longValue();
        TestPlayer other = TestPlayer.register(mockMvc);

        other.browser
                .delete("/api/villages/" + other.villageId + "/research-orders/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESEARCH_ORDER_NOT_FOUND"));
        assertThat(JsonPath.<Integer>read(body(player.smithy()), "$.running.id").longValue())
                .isEqualTo(id);
    }

    // ---- helpers ----

    /** Starts the next level of a unit and lets the clock run to its end. */
    private void researchToTheEnd(UnitType unit) throws Exception {
        String order = body(player.research(unit).andExpect(status().isCreated()));
        clock.set(Instant.parse(JsonPath.read(order, "$.endAt")));
        setStock(10_000, 10_000, 10_000);
    }

    private void insertResearch(
            long playerId, long villageId, UnitType unit, int level, Instant endAt) {
        jdbc.sql(
                        """
                        insert into research_order (player_id, village_id, unit, level, start_at,
                            end_at, cost_wood, cost_clay, cost_iron, created_at)
                        values (?, ?, ?, ?, ?, ?, 0, 0, 0, ?)
                        """)
                .params(
                        playerId,
                        villageId,
                        unit.name(),
                        level,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(endAt),
                        Timestamp.from(clock.instant()))
                .update();
    }

    private void setPaladinMaxSlots(int maxSlots) {
        jdbc.sql("update world_settings set paladin_max_slots = ?").param(maxSlots).update();
        worldService.reload();
    }

    private void setLevelsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set unit_levels_enabled = ?").param(enabled).update();
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

    private void setStock(int wood, int clay, int iron) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(wood, clay, iron, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }
}
