package de.bannerfall.recruit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The recruit endpoints (M5a): queueing, the rule checks and their error codes, the overview. The
 * clock stands still here; what happens over time is in {@link RecruitQueueTest}.
 */
@IntegrationTest
class RecruitControllerTest {

    private static final double EPSILON = 1e-9;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        // one test queues a building, and processDue in other tests handles everything that is due
        jdbc.sql("delete from game_event").update();
    }

    // ---- queueing ----

    @Test
    void recruitingPaysTheCostAndCountsThePopulation() throws Exception {
        setLevel(BuildingType.BARRACKS, 1); // population 30 + 7
        Instant now = clock.instant();

        player.recruit(UnitType.SPEARMAN, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.building").value("BARRACKS"))
                .andExpect(jsonPath("$.unit").value("SPEARMAN"))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.secondsPerUnit").value(1000))
                .andExpect(jsonPath("$.startAt").value(now.toString()))
                .andExpect(jsonPath("$.endAt").value(now.plusSeconds(4000).toString()));

        // 4 × (40, 35, 15) paid, 4 population in use while they train, none at home yet
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(340, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(360, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(440, EPSILON)))
                .andExpect(jsonPath("$.population.used").value(41))
                .andExpect(jsonPath("$.unitsHome").isEmpty());
    }

    @Test
    void theBuildingLevelOfTheMomentSetsTheTime() throws Exception {
        setLevel(BuildingType.FARM, 30); // room for the barracks' population
        setLevel(BuildingType.BARRACKS, 25);

        // rulebook: a spearman at barracks 25 takes 210 s
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secondsPerUnit").value(210));

        // the queued order keeps its time, new orders use the level of their moment
        setLevel(BuildingType.BARRACKS, 1);
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secondsPerUnit").value(1000));
        player.recruitment()
                .andExpect(jsonPath("$[0].queue[0].secondsPerUnit").value(210))
                .andExpect(jsonPath("$[0].queue[1].secondsPerUnit").value(1000));
    }

    @Test
    void eachBuildingHasItsOwnQueueOfFiveOrders() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.STABLE, 1);
        for (int i = 0; i < 5; i++) {
            player.recruitOk(UnitType.SPEARMAN, 1);
        }

        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
        player.recruit(UnitType.SCOUT, 1).andExpect(status().isCreated());

        // the queue is checked before everything else
        setStock(0, 0, 0);
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
    }

    @Test
    void requirementsAreCheckedAgainstTheCurrentLevels() throws Exception {
        makeRich();
        // a new village has no barracks
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        setLevel(BuildingType.BARRACKS, 1);
        player.recruit(UnitType.SPEARMAN, 1).andExpect(status().isCreated());
        // the swordsman also needs the smithy, the axeman barracks 2 and smithy 2
        player.recruit(UnitType.SWORDSMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        setLevel(BuildingType.SMITHY, 1);
        player.recruit(UnitType.SWORDSMAN, 1).andExpect(status().isCreated());
        player.recruit(UnitType.AXEMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void queuedBuildingLevelsDoNotCount() throws Exception {
        makeRich();
        setLevel(BuildingType.HEADQUARTERS, 3); // what the barracks needs
        player.enqueueOk(BuildingType.BARRACKS);

        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void aNobleNeedsTheAcademy() throws Exception {
        makeRich();
        player.recruit(UnitType.NOBLE, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        setLevel(BuildingType.ACADEMY, 1); // population 30 + 80, a noble needs 100 of the 240
        player.recruit(UnitType.NOBLE, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.building").value("ACADEMY"))
                .andExpect(jsonPath("$.secondsPerUnit").value(10800));
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(100_000 - 40_000, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(100_000 - 50_000, EPSILON)))
                .andExpect(jsonPath("$.population.used").value(210));
    }

    @Test
    void theStockIsComparedRoundedDown() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        setStock(79.9, 500, 500);
        player.recruit(UnitType.SPEARMAN, 2) // 80 wood
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));

        setStock(80, 500, 500);
        player.recruit(UnitType.SPEARMAN, 2).andExpect(status().isCreated());
    }

    @Test
    void anOrderNeedsFreePopulation() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1);
        setHomeSpearmen(200); // 237 of 240 used

        player.recruit(UnitType.SPEARMAN, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"));
        player.recruit(UnitType.SPEARMAN, 3).andExpect(status().isCreated());
        player.village().andExpect(jsonPath("$.population.used").value(240));
    }

    @Test
    void requirementsComeBeforeResourcesAndResourcesBeforePopulation() throws Exception {
        setStock(0, 0, 0);
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        setLevel(BuildingType.BARRACKS, 1);
        setHomeSpearmen(203); // the farm is full
        player.recruit(UnitType.SPEARMAN, 1)
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
    }

    // ---- recruit all (M27) ----

    @Test
    void recruitAllQueuesEveryOrderAndPaysForAll() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.SMITHY, 1);
        Instant now = clock.instant();

        // spearman (40, 35, 15) × 6 + swordsman (30, 30, 70) × 4: barracks orders one after another
        player.recruitAll(UnitType.SPEARMAN, 6, UnitType.SWORDSMAN, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$[0].count").value(6))
                .andExpect(jsonPath("$[0].startAt").value(now.toString()))
                .andExpect(jsonPath("$[0].endAt").value(now.plusSeconds(6000).toString()))
                .andExpect(jsonPath("$[1].unit").value("SWORDSMAN"))
                .andExpect(jsonPath("$[1].startAt").value(now.plusSeconds(6000).toString()));

        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(500 - 240 - 120, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(500 - 210 - 120, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(500 - 90 - 280, EPSILON)));
        player.recruitment().andExpect(jsonPath("$[0].queue.length()").value(2));
    }

    @Test
    void recruitAllSpansBuildings() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.STABLE, 1);

        player.recruitAll(UnitType.SPEARMAN, 2, UnitType.SCOUT, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[1].building").value("STABLE"))
                .andExpect(jsonPath("$[1].startAt").value(clock.instant().toString()));
    }

    @Test
    void recruitAllIsAllOrNothingWhenTheResourcesRunOut() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.SMITHY, 1);

        // each order alone fits into 500 wood, together they need 400 + 120
        player.recruitAll(UnitType.SPEARMAN, 10, UnitType.SWORDSMAN, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"))
                .andExpect(jsonPath("$.unit").value("SWORDSMAN"));

        // the spearmen were rolled back
        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(500, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(500, EPSILON)));
    }

    @Test
    void recruitAllSharesTheFreePopulation() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.STABLE, 1);
        setHomeSpearmen(freePopulation() - 5);

        // 3 spearmen (1 each) + 2 scouts (2 each) = 7 > 5
        player.recruitAll(UnitType.SPEARMAN, 3, UnitType.SCOUT, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POPULATION"))
                .andExpect(jsonPath("$.unit").value("SCOUT"));
        player.recruitment()
                .andExpect(jsonPath("$[0].queue").isEmpty())
                .andExpect(jsonPath("$[1].queue").isEmpty());

        player.recruitAll(UnitType.SPEARMAN, 3, UnitType.SCOUT, 1).andExpect(status().isCreated());
        assertThat(freePopulation()).isZero();
    }

    @Test
    void recruitAllNeedsAFreePlaceInTheQueueForEachOrder() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.SMITHY, 1);
        for (int i = 0; i < 4; i++) {
            player.recruitOk(UnitType.SPEARMAN, 1);
        }

        player.recruitAll(UnitType.SPEARMAN, 1, UnitType.SWORDSMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"))
                .andExpect(jsonPath("$.unit").value("SWORDSMAN"));
        player.recruitment().andExpect(jsonPath("$[0].queue.length()").value(4));

        player.recruitAll(UnitType.SWORDSMAN, 1).andExpect(status().isCreated());
    }

    @Test
    void recruitAllChecksTheRequirementsOfEveryOrder() throws Exception {
        makeRich();
        setLevel(BuildingType.BARRACKS, 1); // no smithy: no swordsmen

        player.recruitAll(UnitType.SPEARMAN, 1, UnitType.SWORDSMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"))
                .andExpect(jsonPath("$.unit").value("SWORDSMAN"));
        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());
    }

    @Test
    void recruitAllHasAnEntryPerUnitAtMost() throws Exception {
        // the annotation can't read UnitType.values().length
        assertThat(RecruitAllRequest.MAX_ORDERS).isEqualTo(UnitType.values().length);
    }

    // ---- request handling ----

    @Test
    void unitAndCountAreValidated() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        String path = player.villagePath() + "/recruit-orders";

        for (String body :
                new String[] {
                    "{\"unit\":\"SPEARMAN\",\"count\":0}",
                    "{\"unit\":\"SPEARMAN\",\"count\":-1}",
                    "{\"unit\":\"SPEARMAN\",\"count\":30001}",
                    "{\"unit\":\"SPEARMAN\"}"
                }) {
            player.browser
                    .post(path, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.errors[0].field").value("count"));
        }
        player.browser
                .post(path, "{\"count\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("unit"));
        // an unknown unit can't be read at all: malformed JSON, no field list
        player.browser
                .post(path, "{\"unit\":\"DRAGON\",\"count\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());
    }

    @Test
    void foreignVillagesAreNotFound() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        long order = player.recruitOk(UnitType.SPEARMAN, 1);
        TestPlayer other = TestPlayer.register(mockMvc);
        String path = player.villagePath();

        other.browser
                .get(path + "/recruitment")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(path + "/recruit-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .delete(path + "/recruit-orders/" + order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        // nothing happened to the order
        player.recruitment().andExpect(jsonPath("$[0].queue.length()").value(1));
    }

    @Test
    void onlyOrdersOfTheVillageCanBeCancelled() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        long order = player.recruitOk(UnitType.SPEARMAN, 1);
        TestPlayer other = TestPlayer.register(mockMvc);

        // through the other player's own village: that village has no such order
        other.cancelRecruit(order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_ORDER_NOT_FOUND"));
        player.cancelRecruit(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_ORDER_NOT_FOUND"));
        player.recruitment().andExpect(jsonPath("$[0].queue.length()").value(1));

        player.cancelRecruit(order).andExpect(status().isNoContent());
        player.cancelRecruit(order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_ORDER_NOT_FOUND"));
    }

    @Test
    void theRecruitEndpointsNeedLogin() throws Exception {
        Browser anonymous = Browser.open(mockMvc);
        String path = player.villagePath();

        anonymous.get(path + "/recruitment").andExpect(status().isUnauthorized());
        anonymous
                .post(path + "/recruit-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}")
                .andExpect(status().isUnauthorized());
        anonymous
                .post(path + "/recruit-orders/all", "{\"orders\":[]}")
                .andExpect(status().isUnauthorized());
        anonymous.delete(path + "/recruit-orders/1").andExpect(status().isUnauthorized());
    }

    // ---- the overview ----

    @Test
    void theOverviewListsTheFiveRecruitingBuildingsWithTheirUnits() throws Exception {
        player.recruitment()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[0].level").value(0))
                .andExpect(
                        jsonPath("$[0].units[*].unit")
                                .value(contains("SPEARMAN", "SWORDSMAN", "AXEMAN")))
                .andExpect(jsonPath("$[0].queue").isEmpty())
                .andExpect(jsonPath("$[1].building").value("STABLE"))
                .andExpect(
                        jsonPath("$[1].units[*].unit")
                                .value(contains("SCOUT", "LIGHT_CAVALRY", "HEAVY_CAVALRY")))
                .andExpect(jsonPath("$[2].building").value("WORKSHOP"))
                .andExpect(jsonPath("$[2].units[*].unit").value(contains("RAM", "CATAPULT")))
                .andExpect(jsonPath("$[3].building").value("ACADEMY"))
                .andExpect(jsonPath("$[3].units[*].unit").value(contains("NOBLE")))
                // M32b: the Banner Hall trains the banner thief (banners are on by default), and
                // since 1.4 the paladin
                .andExpect(jsonPath("$[4].building").value("BANNER_HALL"))
                .andExpect(
                        jsonPath("$[4].units[*].unit").value(contains("PALADIN", "BANNER_THIEF")));
    }

    @Test
    void theOverviewShowsCostTimeAndWhatIsMissing() throws Exception {
        // without barracks: not available, the time is the one at level 1
        player.recruitment()
                .andExpect(jsonPath("$[0].units[0].unit").value("SPEARMAN"))
                .andExpect(jsonPath("$[0].units[0].cost.wood").value(40))
                .andExpect(jsonPath("$[0].units[0].cost.clay").value(35))
                .andExpect(jsonPath("$[0].units[0].cost.iron").value(15))
                .andExpect(jsonPath("$[0].units[0].pop").value(1))
                .andExpect(jsonPath("$[0].units[0].secondsPerUnit").value(1000))
                .andExpect(jsonPath("$[0].units[0].available").value(false))
                .andExpect(jsonPath("$[0].units[0].missing[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[0].units[0].missing[0].level").value(1));

        setLevel(BuildingType.BARRACKS, 1);
        player.recruitment()
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[0].units[0].available").value(true))
                .andExpect(jsonPath("$[0].units[0].missing").isEmpty())
                // the swordsman needs the smithy, the axeman barracks 2 and smithy 2 (in order)
                .andExpect(jsonPath("$[0].units[1].available").value(false))
                .andExpect(jsonPath("$[0].units[1].missing.length()").value(1))
                .andExpect(jsonPath("$[0].units[1].missing[0].building").value("SMITHY"))
                .andExpect(jsonPath("$[0].units[2].missing[0].building").value("BARRACKS"))
                .andExpect(jsonPath("$[0].units[2].missing[0].level").value(2))
                .andExpect(jsonPath("$[0].units[2].missing[1].building").value("SMITHY"))
                .andExpect(jsonPath("$[0].units[2].missing[1].level").value(2))
                .andExpect(jsonPath("$[3].units[0].missing[0].building").value("ACADEMY"));
    }

    @Test
    void theOverviewShowsHowManyThePlayerCanAfford() throws Exception {
        setLevel(BuildingType.BARRACKS, 1); // 203 of 240 population free
        player.recruitment()
                // 500 wood / 40
                .andExpect(jsonPath("$[0].units[0].maxAffordable").value(12))
                // swordsman: 500 iron / 70
                .andExpect(jsonPath("$[0].units[1].maxAffordable").value(7))
                // a noble costs far more than 500
                .andExpect(jsonPath("$[3].units[0].maxAffordable").value(0));

        makeRich();
        setHomeSpearmen(198); // 5 population free
        player.recruitment()
                .andExpect(jsonPath("$[0].units[0].maxAffordable").value(5))
                // a scout needs 2 population
                .andExpect(jsonPath("$[1].units[0].maxAffordable").value(2));
    }

    @Test
    void theOverviewShowsTheQueueOfEachBuilding() throws Exception {
        setLevel(BuildingType.BARRACKS, 1);
        setLevel(BuildingType.STABLE, 1);
        long spearmen = player.recruitOk(UnitType.SPEARMAN, 2);
        long scouts = player.recruitOk(UnitType.SCOUT, 1);
        long more = player.recruitOk(UnitType.SPEARMAN, 1);

        player.recruitment()
                .andExpect(jsonPath("$[0].queue.length()").value(2))
                .andExpect(jsonPath("$[0].queue[0].id").value(spearmen))
                .andExpect(jsonPath("$[0].queue[0].count").value(2))
                .andExpect(jsonPath("$[0].queue[1].id").value(more))
                .andExpect(jsonPath("$[1].queue.length()").value(1))
                .andExpect(jsonPath("$[1].queue[0].id").value(scouts))
                .andExpect(jsonPath("$[1].queue[0].unit").value("SCOUT"))
                .andExpect(jsonPath("$[2].queue").isEmpty())
                .andExpect(jsonPath("$[3].queue").isEmpty());
    }

    // ---- helpers ----

    private void makeRich() {
        setStock(100_000, 100_000, 100_000);
    }

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(wood, clay, iron, player.villageId)
                .update();
    }

    /** The column name comes from the enum, never from user input. */
    private void setLevel(BuildingType building, int level) {
        String column = "b_" + building.name().toLowerCase(Locale.ROOT);
        jdbc.sql("update village set " + column + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private int freePopulation() throws Exception {
        String village = TestPlayer.body(player.village());
        return TestPlayer.number(village, "$.population.capacity").intValue()
                - TestPlayer.number(village, "$.population.used").intValue();
    }

    private void setHomeSpearmen(int count) {
        jdbc.sql("update unit_stack set u_spearman = ? where home_village_id = ?")
                .params(count, player.villageId)
                .update();
    }
}
