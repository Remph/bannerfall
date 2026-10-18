package de.bannerfall.paladin;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.rules.Travel;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The paladin (M31, 02-game-rules §5.5): the Banner Hall that recruits him (1.4; the barracks
 * before), the slots a player has, what takes a slot, and the pace of a support that carries a
 * paladin. Villages of a player beyond the first are barbarian villages handed over by SQL; the
 * clock is moved by hand.
 */
@IntegrationTest
class PaladinFlowTest {

    private static final int MAP_ROW = TestWorldConfiguration.MAP_SIZE - 14;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;
    @Autowired EventProcessor processor;

    private TestPlayer player;
    private final List<Long> extraVillages = new ArrayList<>();

    @BeforeEach
    void registerPlayer() throws Exception {
        jdbc.sql("delete from game_event").update();
        setPaladinSettings(4, 5);
        player = TestPlayer.register(mockMvc);
        // a Banner Hall for the paladin (and what it needs), a farm and a warehouse that hold five
        // paladins, and the stock
        setLevel(player.villageId, BuildingType.HEADQUARTERS, 8);
        setLevel(player.villageId, BuildingType.BARRACKS, 5);
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(player.villageId, BuildingType.FARM, 15);
        setLevel(player.villageId, BuildingType.WAREHOUSE, 20);
        setStock(30_000);
    }

    @AfterEach
    void restoreTheWorld() {
        for (long id : extraVillages) {
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        extraVillages.clear();
        setPaladinSettings(4, 5);
        jdbc.sql("delete from game_event").update();
    }

    // ---- the Banner Hall and the first slot ----

    @Test
    void thePaladinNeedsABannerHall() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);

        player.recruit(UnitType.PALADIN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    @Test
    void theBannerHallListsThePaladinBeforeTheThief() throws Exception {
        String recruitment = body(player.recruitment().andExpect(status().isOk()));

        assertThat(JsonPath.<List<String>>read(recruitment, "$[*].building"))
                .containsExactly("BARRACKS", "STABLE", "WORKSHOP", "ACADEMY", "BANNER_HALL");
        assertThat(JsonPath.<List<String>>read(recruitment, "$[0].units[*].unit"))
                .containsExactly("SPEARMAN", "SWORDSMAN", "AXEMAN");
        assertThat(JsonPath.<List<String>>read(recruitment, "$[4].units[*].unit"))
                .containsExactly("PALADIN", "BANNER_THIEF");
        assertThat(JsonPath.<Boolean>read(recruitment, "$[4].units[0].available")).isTrue();
        // one slot, so at most one is on offer, though the stock would pay for more
        assertThat(JsonPath.<Integer>read(recruitment, "$[4].units[0].maxAffordable")).isEqualTo(1);
        // 3 h at Banner Hall 1, at the test world's speed
        assertThat(number(recruitment, "$[4].units[0].secondsPerUnit").longValue())
                .isEqualTo(
                        RecruitRules.secondsPerUnit(
                                gameData.unit(UnitType.PALADIN), 1, worldService.settings()));
    }

    @Test
    void aPlayerStartsWithOneSlot() throws Exception {
        player.browser
                .get("/api/paladin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSlots").value(5))
                .andExpect(jsonPath("$.villagesPerSlot").value(4))
                .andExpect(jsonPath("$.villages").value(1))
                .andExpect(jsonPath("$.slots").value(1))
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.nextSlotAtVillages").value(5))
                .andExpect(
                        jsonPath("$.minutesPerField")
                                .value(gameData.unit(UnitType.PALADIN).minutesPerField()));

        long order = player.recruitOk(UnitType.PALADIN, 1);

        assertThat(order).isPositive();
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        player.recruit(UnitType.PALADIN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_SLOTS_FULL"));
        // the refused order cost nothing
        assertThat(stockWood()).isEqualTo(30_000 - 1000);
    }

    @Test
    void anOrderForMoreThanTheFreeSlotsIsRefused() throws Exception {
        player.recruit(UnitType.PALADIN, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_SLOTS_FULL"));

        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
    }

    @Test
    void thePaladinCostsAndTakesTenPopulation() throws Exception {
        var def = gameData.unit(UnitType.PALADIN);
        int popBefore = number(body(player.village()), "$.population.used").intValue();

        player.recruitOk(UnitType.PALADIN, 1);

        assertThat(number(body(player.village()), "$.population.used").intValue())
                .isEqualTo(popBefore + def.pop());
        assertThat(stockWood()).isEqualTo(30_000 - def.cost().wood());
    }

    // ---- more villages, more slots ----

    @Test
    void everyFourthVillageOpensASlot() throws Exception {
        player.recruitOk(UnitType.PALADIN, 1);
        addVillages(3); // 4 villages: still one slot
        player.browser
                .get("/api/paladin")
                .andExpect(jsonPath("$.villages").value(4))
                .andExpect(jsonPath("$.slots").value(1));
        player.recruit(UnitType.PALADIN, 1).andExpect(status().isConflict());

        addVillages(1); // the fifth village: the second slot
        player.browser
                .get("/api/paladin")
                .andExpect(jsonPath("$.villages").value(5))
                .andExpect(jsonPath("$.slots").value(2))
                .andExpect(jsonPath("$.used").value(1))
                .andExpect(jsonPath("$.nextSlotAtVillages").value(9));
        player.recruitOk(UnitType.PALADIN, 1);
        player.recruit(UnitType.PALADIN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_SLOTS_FULL"));
    }

    @Test
    void theMaximumStopsTheSlots() throws Exception {
        setPaladinSettings(1, 3);
        addVillages(9);

        player.browser
                .get("/api/paladin")
                .andExpect(jsonPath("$.villages").value(10))
                .andExpect(jsonPath("$.slots").value(3))
                .andExpect(jsonPath("$.nextSlotAtVillages").doesNotExist());
        player.recruitOk(UnitType.PALADIN, 3);
        player.recruit(UnitType.PALADIN, 1).andExpect(status().isConflict());
    }

    @Test
    void aLostVillageDoesNotTakeAPaladinAwayButBlocksTheNext() throws Exception {
        List<Long> added = addVillages(4);
        player.recruitOk(UnitType.PALADIN, 2);
        player.browser.get("/api/paladin").andExpect(jsonPath("$.slots").value(2));

        // a village changes hands: only one slot is left, two paladins stay
        jdbc.sql("update village set player_id = null where id = ?").param(added.get(0)).update();

        player.browser
                .get("/api/paladin")
                .andExpect(jsonPath("$.slots").value(1))
                .andExpect(jsonPath("$.used").value(2));
        player.recruit(UnitType.PALADIN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_SLOTS_FULL"));
        String recruitment = body(player.recruitment());
        assertThat(JsonPath.<Integer>read(recruitment, "$[4].units[0].maxAffordable")).isZero();
    }

    // ---- what takes a slot ----

    @Test
    void aFinishedPaladinStandsInTheHomeStackAndKeepsTheSlot() throws Exception {
        player.recruitOk(UnitType.PALADIN, 1);
        clock.set(clock.instant().plusSeconds(24 * 3600));

        player.village().andExpect(jsonPath("$.unitsHome.PALADIN").value(1));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        player.recruit(UnitType.PALADIN, 1).andExpect(status().isConflict());
    }

    @Test
    void cancellingTheOrderGivesTheSlotBack() throws Exception {
        long order = player.recruitOk(UnitType.PALADIN, 1);

        player.cancelRecruit(order).andExpect(status().isNoContent());

        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
        player.recruitOk(UnitType.PALADIN, 1);
    }

    @Test
    void aPaladinOnTheWayOrInSupportKeepsHisSlot() throws Exception {
        TestPlayer friend = TestPlayer.register(mockMvc);
        setHomeUnits(player, "u_paladin", 1);

        player.command("SUPPORT", coordinates(friend), "{\"PALADIN\":1}")
                .andExpect(status().isCreated());

        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        player.recruit(UnitType.PALADIN, 1).andExpect(status().isConflict());
        jdbc.sql("update unit_stack set u_paladin = 0 where home_village_id = ?")
                .param(player.villageId)
                .update();
        // a paladin who is dead (in no stack, no movement) is gone: the slot is free again
        jdbc.sql("delete from movement where home_village_id = ?").param(player.villageId).update();
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
        player.recruitOk(UnitType.PALADIN, 1);
    }

    @Test
    void aPaladinWaitingToBeDismissedKeepsHisSlotUntilHeIsGone() throws Exception {
        setHomeUnits(player, "u_paladin", 1);

        long order = player.dismissOk(UnitType.PALADIN, 1);

        player.village().andExpect(jsonPath("$.unitsHome.PALADIN").doesNotExist());
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        player.recruit(UnitType.PALADIN, 1).andExpect(status().isConflict());

        // cancelling puts him back at home: still one paladin, not two
        player.cancelDismiss(order).andExpect(status().isNoContent());
        player.village().andExpect(jsonPath("$.unitsHome.PALADIN").value(1));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));

        player.dismissOk(UnitType.PALADIN, 1);
        clock.set(clock.instant().plusSeconds(24 * 3600));
        // his time is up: the slot is free without anything catching the village up first
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
        player.recruitOk(UnitType.PALADIN, 1);
    }

    @Test
    void aDismissalFreesItsSlotsOneByOneByTheClock() throws Exception {
        setHomeUnits(player, "u_paladin", 3);
        player.dismissOk(UnitType.PALADIN, 3);
        int secondsPerUnit =
                jdbc.sql("select seconds_per_unit from dismiss_order where village_id = ?")
                        .param(player.villageId)
                        .query(Integer.class)
                        .single();

        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(3));
        clock.set(clock.instant().plusSeconds(secondsPerUnit - 1));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(3));
        clock.set(clock.instant().plusSeconds(1));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(2));
        clock.set(clock.instant().plusSeconds(secondsPerUnit));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        clock.set(clock.instant().plusSeconds(10L * secondsPerUnit));
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
    }

    @Test
    void recruitAllIsAllOrNothingAlsoForTheSlots() throws Exception {
        player.recruitAll(UnitType.PALADIN, 2, UnitType.SPEARMAN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_SLOTS_FULL"))
                .andExpect(jsonPath("$.unit").value("PALADIN"));

        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
        player.recruitAll(UnitType.SPEARMAN, 1, UnitType.PALADIN, 1)
                .andExpect(status().isCreated());
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
    }

    // ---- the world settings ----

    @Test
    void withoutPaladinsTheBannerHallDoesNotOfferHim() throws Exception {
        setPaladinSettings(4, 0);

        // not in the Banner Hall's list and not recruitable
        assertThat(JsonPath.<List<String>>read(body(player.recruitment()), "$[4].units[*].unit"))
                .containsExactly("BANNER_THIEF");
        player.recruit(UnitType.PALADIN, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_DISABLED"));
        // the Town Hall's preview of Banner Hall 0 -> 1 does not announce a unit that is not there
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        String unlocksPath = "$[?(@.building=='BANNER_HALL')].next.unlocksUnits[*]";
        assertThat(JsonPath.<List<String>>read(body(player.buildings()), unlocksPath))
                .doesNotContain("PALADIN");
        setPaladinSettings(4, 5);
        assertThat(JsonPath.<List<String>>read(body(player.buildings()), unlocksPath))
                .contains("PALADIN");
        setPaladinSettings(4, 0);
        player.browser
                .get("/api/paladin")
                .andExpect(jsonPath("$.maxSlots").value(0))
                .andExpect(jsonPath("$.slots").value(0))
                .andExpect(jsonPath("$.nextSlotAtVillages").doesNotExist());
    }

    @Test
    void withBannersOffTheBannerHallStillTrainsThePaladin() throws Exception {
        setBanners(false);
        try {
            // the hall trains him alone: no thieves without banners
            String recruitment = body(player.recruitment());
            assertThat(JsonPath.<String>read(recruitment, "$[4].building"))
                    .isEqualTo("BANNER_HALL");
            assertThat(JsonPath.<List<String>>read(recruitment, "$[4].units[*].unit"))
                    .containsExactly("PALADIN");
            player.recruitOk(UnitType.PALADIN, 1);
            // and a village without one can build it
            setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
            assertThat(JsonPath.<List<String>>read(body(player.buildings()), "$[*].building"))
                    .contains("BANNER_HALL");
            player.enqueueOk(BuildingType.BANNER_HALL);

            // a world with neither banners nor paladins has no use for it
            setPaladinSettings(4, 0);
            player.enqueue(BuildingType.BANNER_HALL)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
            assertThat(JsonPath.<List<String>>read(body(player.recruitment()), "$[*].building"))
                    .doesNotContain("BANNER_HALL");
        } finally {
            setBanners(true);
        }
    }

    // ---- the pace of a support ----

    @Test
    void aSupportWithAPaladinWalksAtHisSpeed() throws Exception {
        TestPlayer friend = TestPlayer.register(mockMvc);
        Coordinates target = coordinates(friend);
        setHomeUnits(player, "u_spearman", 20);
        setHomeUnits(player, "u_paladin", 1);
        double paladin = gameData.unit(UnitType.PALADIN).minutesPerField();

        long withSeconds =
                seconds(
                        player.previewCommand(
                                "SUPPORT", target, "{\"SPEARMAN\":10,\"PALADIN\":1}"));
        long withoutSeconds =
                seconds(player.previewCommand("SUPPORT", target, "{\"SPEARMAN\":10}"));

        assertThat(withSeconds).isEqualTo(secondsAt(player, target, paladin));
        assertThat(withoutSeconds).isEqualTo(secondsAt(player, target, 18));
        assertThat(withSeconds).isLessThan(withoutSeconds);
        // sending takes the same time as the preview says
        String sent =
                body(
                        player.command("SUPPORT", target, "{\"SPEARMAN\":10,\"PALADIN\":1}")
                                .andExpect(status().isCreated()));
        assertThat(
                        Instant.parse(JsonPath.read(sent, "$.arriveAt")).getEpochSecond()
                                - Instant.parse(JsonPath.read(sent, "$.departAt")).getEpochSecond())
                .isEqualTo(withSeconds);
    }

    @Test
    void anAttackWithAPaladinWalksAtTheSlowestUnit() throws Exception {
        TestPlayer enemy = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), enemy.playerId)
                .update();
        Coordinates target = coordinates(enemy);
        setHomeUnits(player, "u_spearman", 20);
        setHomeUnits(player, "u_light_cavalry", 5);
        setHomeUnits(player, "u_paladin", 1);

        // no help in an attack: spearmen (18) are slower than he is, light cavalry (10) quicker
        assertThat(
                        seconds(
                                player.previewCommand(
                                        "ATTACK", target, "{\"SPEARMAN\":5,\"PALADIN\":1}")))
                .isEqualTo(secondsAt(player, target, 18));
        assertThat(
                        seconds(
                                player.previewCommand(
                                        "ATTACK", target, "{\"LIGHT_CAVALRY\":5,\"PALADIN\":1}")))
                .isEqualTo(secondsAt(player, target, 14));
    }

    @Test
    void aSupportCalledHomeWalksAtThePaladinsSpeedToo() throws Exception {
        TestPlayer friend = TestPlayer.register(mockMvc);
        Coordinates target = coordinates(friend);
        setHomeUnits(player, "u_spearman", 20);
        setHomeUnits(player, "u_paladin", 1);
        String sent =
                body(
                        player.command("SUPPORT", target, "{\"SPEARMAN\":10,\"PALADIN\":1}")
                                .andExpect(status().isCreated()));
        clock.set(Instant.parse(JsonPath.read(sent, "$.arriveAt")));
        processor.processDue(clock.instant());
        String here = body(friend.troops());
        long stackId = number(here, "$.supportsHere[0].stackId").longValue();

        // the paladin brings the spearmen home at 14, the spearmen alone at 18
        String home = body(player.returnStack(stackId, "{\"SPEARMAN\":4,\"PALADIN\":1}"));
        String alone = body(player.returnStack(stackId, "{\"SPEARMAN\":6}"));

        assertThat(duration(home)).isEqualTo(secondsAt(player, target, 14));
        assertThat(duration(alone)).isEqualTo(secondsAt(player, target, 18));
    }

    // ---- helpers ----

    private long seconds(org.springframework.test.web.servlet.ResultActions preview)
            throws Exception {
        return number(body(preview.andExpect(status().isOk())), "$.travelSeconds").longValue();
    }

    private long duration(String movement) {
        return Instant.parse(JsonPath.read(movement, "$.arriveAt")).getEpochSecond()
                - Instant.parse(JsonPath.read(movement, "$.departAt")).getEpochSecond();
    }

    /** From the player's village to {@code target} at this pace, in the test world. */
    private long secondsAt(TestPlayer from, Coordinates target, double minutesPerField) {
        return Travel.travelSeconds(
                Travel.distance(coordinates(from), target),
                minutesPerField,
                worldService.settings());
    }

    private Coordinates coordinates(TestPlayer p) {
        return jdbc.sql("select x, y from village where id = ?")
                .param(p.villageId)
                .query((rs, n) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                .single();
    }

    /** Gives the player more villages: barbarian villages in a row at the bottom of the map. */
    private List<Long> addVillages(int count) {
        List<Long> added = new ArrayList<>();
        for (int x = 20; added.size() < count && x < TestWorldConfiguration.MAP_SIZE; x++) {
            Long id =
                    jdbc.sql(
                                    """
                                    insert into village (player_id, name, x, y, wood, clay, iron,
                                                         resources_updated_at, loyalty_updated_at,
                                                         points, created_at)
                                    values (:player, 'extra', :x, :y, 0, 0, 0, now(), now(), 0, now())
                                    on conflict (x, y) do nothing
                                    returning id
                                    """)
                            .param("player", player.playerId)
                            .param("x", x)
                            .param("y", MAP_ROW)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                added.add(id);
                extraVillages.add(id);
            }
        }
        assertThat(added).hasSize(count);
        return added;
    }

    private void setPaladinSettings(int villagesPerSlot, int maxSlots) {
        jdbc.sql("update world_settings set paladin_villages_per_slot = ?, paladin_max_slots = ?")
                .params(villagesPerSlot, maxSlots)
                .update();
        worldService.reload();
    }

    private void setBanners(boolean enabled) {
        jdbc.sql("update world_settings set banners_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, villageId)
                .update();
    }

    private void setStock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private int stockWood() {
        return jdbc.sql("select floor(wood)::int from village where id = ?")
                .param(player.villageId)
                .query(Integer.class)
                .single();
    }

    /** The column name is a constant of this test, never user input. */
    private void setHomeUnits(TestPlayer p, String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, p.villageId, p.villageId)
                .update();
    }
}
