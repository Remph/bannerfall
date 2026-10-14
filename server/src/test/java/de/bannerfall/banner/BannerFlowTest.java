package de.bannerfall.banner;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
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
 * Banners (M32a, 02-game-rules §21): the Banner Hall and the stash, equipping, swapping and
 * unequipping with the lock, and the four economy effects. Banners are put into the stash by SQL
 * (the drops come with M32b); the clock is moved by hand.
 */
@IntegrationTest
class BannerFlowTest {

    private static final double EPSILON = 1e-6;
    private static final int MAP_ROW = TestWorldConfiguration.MAP_SIZE - 12;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;
    @Autowired BannerCatalog catalog;
    @Autowired EventProcessor processor;

    private TestPlayer player;
    private final List<Long> extraVillages = new ArrayList<>();

    @BeforeEach
    void registerPlayer() throws Exception {
        jdbc.sql("delete from game_event").update();
        setBanners(true);
        player = TestPlayer.register(mockMvc);
        // room for a few orders
        setLevel(player.villageId, BuildingType.FARM, 15);
        setLevel(player.villageId, BuildingType.BARRACKS, 5);
        setStock(player.villageId, 20_000);
    }

    @AfterEach
    void restoreTheWorld() {
        for (long id : extraVillages) {
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        extraVillages.clear();
        setBanners(true);
        jdbc.sql("delete from game_event").update();
    }

    // ---- the numbers of the file ----

    @Test
    void theFileHoldsTheRulebookNumbers() {
        assertThat(catalog.effect(BannerType.HARVEST, 3)).isEqualTo(0.15);
        assertThat(catalog.effect(BannerType.WAR, 3)).isEqualTo(0.10);
        assertThat(catalog.effect(BannerType.TREASURY, 2)).isEqualTo(0.20);
        assertThat(catalog.effect(BannerType.OATH, 1)).isEqualTo(0.04);
        assertThat(catalog.drops().tierOdds()).containsExactly(70, 25, 5);
        assertThat(catalog.fusing().bannersNeeded()).isEqualTo(3);
    }

    // ---- the overview ----

    @Test
    void theOverviewListsTheStashAndEveryVillageWithItsHall() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 2);
        long banner = giveBanner(player, BannerType.DRILL, 3);

        String overview =
                body(
                        player.browser
                                .get("/api/banners")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.enabled").value(true))
                                .andExpect(jsonPath("$.hallMaxLevel").value(3))
                                .andExpect(jsonPath("$.banners.length()").value(1))
                                .andExpect(jsonPath("$.banners[0].id").value(banner))
                                .andExpect(jsonPath("$.banners[0].type").value("DRILL"))
                                .andExpect(jsonPath("$.banners[0].tier").value(3))
                                .andExpect(jsonPath("$.banners[0].villageId").doesNotExist())
                                .andExpect(jsonPath("$.villages.length()").value(1))
                                .andExpect(jsonPath("$.villages[0].id").value(player.villageId))
                                .andExpect(jsonPath("$.villages[0].hallLevel").value(2))
                                .andExpect(jsonPath("$.villages[0].bannerId").doesNotExist())
                                .andExpect(jsonPath("$.villages[0].effectiveTier").value(0))
                                .andExpect(jsonPath("$.villages[0].locked").value(false)));
        // the effects table is the file's, so the page needs no copy of it
        assertThat(JsonPath.<List<Double>>read(overview, "$.effects.PLUNDER"))
                .containsExactly(0.10, 0.20, 0.30);
        assertThat(JsonPath.<Integer>read(overview, "$.effects.length()")).isEqualTo(9);
    }

    @Test
    void anotherPlayersBannersAreNotInTheList() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        giveBanner(other, BannerType.WAR, 1);

        player.browser.get("/api/banners").andExpect(jsonPath("$.banners.length()").value(0));
    }

    // ---- the Banner Hall ----

    @Test
    void theHallHasThreeLevelsAndNeedsTheHeadquartersAndTheBarracks() throws Exception {
        var def = gameData.building(BuildingType.BANNER_HALL);
        assertThat(def.maxLevel()).isEqualTo(3);

        player.enqueue(BuildingType.BANNER_HALL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        setLevel(player.villageId, BuildingType.HEADQUARTERS, 8);
        player.enqueue(BuildingType.BANNER_HALL).andExpect(status().isCreated());
    }

    @Test
    void theHallIsHiddenInAWorldWithoutBannersUnlessOneStandsThere() throws Exception {
        setBanners(false);

        String list = body(player.buildings().andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(list, "$[*].building"))
                .doesNotContain("BANNER_HALL");
        setLevel(player.villageId, BuildingType.HEADQUARTERS, 8);
        player.enqueue(BuildingType.BANNER_HALL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));

        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        list = body(player.buildings());
        assertThat(JsonPath.<List<String>>read(list, "$[*].building")).contains("BANNER_HALL");
    }

    // ---- equip, swap, unequip ----

    @Test
    void aBannerOfTheStashFliesInAVillageWithAHall() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long banner = giveBanner(player, BannerType.HARVEST, 2);

        player.browser
                .put(equipPath(player.villageId, banner), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners[0].villageId").value(player.villageId))
                .andExpect(jsonPath("$.banners[0].equippedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.villages[0].bannerId").value(banner))
                // tier II in a level 1 hall works as tier I
                .andExpect(jsonPath("$.villages[0].effectiveTier").value(1));

        player.village()
                .andExpect(jsonPath("$.banner.type").value("HARVEST"))
                .andExpect(jsonPath("$.banner.tier").value(2))
                .andExpect(jsonPath("$.banner.effectiveTier").value(1));
        assertThat(eventKinds(banner)).containsExactly("EQUIPPED");
    }

    @Test
    void aVillageWithoutAHallCannotFlyABanner() throws Exception {
        long banner = giveBanner(player, BannerType.HARVEST, 1);

        player.browser
                .put(equipPath(player.villageId, banner), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_HALL_REQUIRED"));

        assertThat(villageOf(banner)).isNull();
    }

    @Test
    void aBannerThatFliesAlreadyCannotBeEquippedAgain() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long second = addVillage();
        setLevel(second, BuildingType.BANNER_HALL, 1);
        long banner = giveBanner(player, BannerType.HASTE, 1);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());

        player.browser
                .put(equipPath(second, banner), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_IN_STASH"));

        assertThat(villageOf(banner)).isEqualTo(player.villageId);
    }

    @Test
    void swappingPutsTheOldBannerBackIntoTheStash() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        long first = giveBanner(player, BannerType.HARVEST, 1);
        long second = giveBanner(player, BannerType.WAR, 3);
        player.browser.put(equipPath(player.villageId, first), "").andExpect(status().isOk());

        player.browser
                .put(equipPath(player.villageId, second), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages[0].bannerId").value(second))
                .andExpect(jsonPath("$.villages[0].effectiveTier").value(3));

        assertThat(villageOf(first)).isNull();
        assertThat(villageOf(second)).isEqualTo(player.villageId);
        assertThat(eventKinds(first)).containsExactly("EQUIPPED", "UNEQUIPPED");
        player.village().andExpect(jsonPath("$.banner.type").value("WAR"));
    }

    @Test
    void unequippingPutsTheBannerBackAndAnEmptySlotHasNothingToTake() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long banner = giveBanner(player, BannerType.OATH, 1);
        player.browser
                .delete("/api/villages/" + player.villageId + "/banner")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_BANNER_EQUIPPED"));
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());

        player.browser
                .delete("/api/villages/" + player.villageId + "/banner")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners[0].villageId").doesNotExist())
                .andExpect(jsonPath("$.villages[0].bannerId").doesNotExist());

        player.village().andExpect(jsonPath("$.banner").doesNotExist());
        // the banner is the same one, not lost on the way
        assertThat(villageOf(banner)).isNull();
        assertThat(eventKinds(banner)).containsExactly("EQUIPPED", "UNEQUIPPED");
    }

    @Test
    void anIncomingAttackLocksTheSlot() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long flying = giveBanner(player, BannerType.BULWARK, 1);
        long spare = giveBanner(player, BannerType.HARVEST, 1);
        player.browser.put(equipPath(player.villageId, flying), "").andExpect(status().isOk());
        attackFrom(TestPlayer.register(mockMvc));

        player.browser.get("/api/banners").andExpect(jsonPath("$.villages[0].locked").value(true));
        player.browser
                .put(equipPath(player.villageId, spare), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_LOCKED"));
        player.browser
                .delete("/api/villages/" + player.villageId + "/banner")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_LOCKED"));

        assertThat(villageOf(flying)).isEqualTo(player.villageId);
        assertThat(villageOf(spare)).isNull();
    }

    @Test
    void foreignBannersAndVillagesAreNotFound() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        TestPlayer other = TestPlayer.register(mockMvc);
        long foreign = giveBanner(other, BannerType.WAR, 1);
        long mine = giveBanner(player, BannerType.WAR, 1);

        player.browser
                .put(equipPath(player.villageId, foreign), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
        player.browser
                .put(equipPath(other.villageId, mine), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        player.browser
                .delete("/api/villages/" + other.villageId + "/banner")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        player.browser
                .put(equipPath(player.villageId, 999_999_999L), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }

    @Test
    void theBannerOfAVillageIsNobodyElsesBusiness() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long banner = giveBanner(player, BannerType.HARVEST, 1);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        TestPlayer other = TestPlayer.register(mockMvc);

        // a stranger sees the village on the map and reads it, but no banner (§21.7)
        other.browser
                .get("/api/villages/" + player.villageId)
                .andExpect(jsonPath("$.banner").doesNotExist());
    }

    @Test
    void theEndpointsNeedLogin() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/banners"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inAWorldWithoutBannersNothingCanBeEquippedAndNothingWorks() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        long banner = giveBanner(player, BannerType.HARVEST, 3);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        double withBanner = productionWood(player);

        setBanners(false);

        player.browser
                .put(equipPath(player.villageId, giveBanner(player, BannerType.WAR, 1)), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
        player.browser
                .get("/api/banners")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.villages[0].effectiveTier").value(0));
        // the banner is kept (it may come back), but dormant
        assertThat(productionWood(player)).isLessThan(withBanner);
        assertThat(productionWood(player)).isCloseTo(basicProduction(), within(EPSILON));
        // the owner still sees it, asleep
        player.village().andExpect(jsonPath("$.banner.effectiveTier").value(0));
    }

    // ---- Harvest ----

    @Test
    void harvestRaisesTheProductionByTheTierTheHallAllows() throws Exception {
        double base = productionWood(player);
        long banner = giveBanner(player, BannerType.HARVEST, 3);

        // hall 1: a tier III banner works at tier I (+5 %)
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        assertThat(productionWood(player)).isCloseTo(base * 1.05, within(EPSILON));

        // hall 3: the full +15 %, on all three resources
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        player.village()
                .andExpect(jsonPath("$.production.wood", closeTo(base * 1.15, EPSILON)))
                .andExpect(jsonPath("$.production.clay", closeTo(base * 1.15, EPSILON)))
                .andExpect(jsonPath("$.production.iron", closeTo(base * 1.15, EPSILON)));

        // hall gone: the banner sleeps
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        assertThat(productionWood(player)).isCloseTo(base, within(EPSILON));
    }

    @Test
    void equippingCatchesTheVillageUpFirstSoTheOldRateCountsUntilThen() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        setStock(player.villageId, 500);
        double base = productionWood(player);
        long banner = giveBanner(player, BannerType.HARVEST, 3);

        clock.advance(Duration.ofHours(1));
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));

        // one hour at the base rate, one at +15 %
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(500 + base + base * 1.15, 1e-3)));

        // and the same on the way out
        player.browser
                .delete("/api/villages/" + player.villageId + "/banner")
                .andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));
        player.village()
                .andExpect(
                        jsonPath(
                                "$.resources.wood",
                                closeTo(500 + base + base * 1.15 + base, 1e-3)));
    }

    // ---- Builders and Drill ----

    @Test
    void buildersShortenNewBuildOrdersAndKeepTheirCost() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 2);
        long banner = giveBanner(player, BannerType.BUILDERS, 3);
        long plain = buildSeconds(BuildingType.WOODCUTTER, 2);

        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        // hall 2: a tier III banner works at tier II (−10 %)
        long shortened = BannerRules.seconds(plain, 0.10);
        assertThat(shortened).isLessThan(plain);
        String overview = body(player.buildings());
        assertThat(
                        JsonPath.<List<Number>>read(
                                        overview,
                                        "$[?(@.building=='WOODCUTTER')].next.durationSeconds")
                                .getFirst()
                                .longValue())
                .isEqualTo(shortened);

        var before = stock(player.villageId);
        var order = body(player.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated()));
        long queued =
                Duration.between(
                                java.time.Instant.parse(JsonPath.read(order, "$.startAt")),
                                java.time.Instant.parse(JsonPath.read(order, "$.endAt")))
                        .getSeconds();
        assertThat(queued).isEqualTo(shortened);
        // the cost is the plain one
        assertThat(before.wood() - stock(player.villageId).wood()).isEqualTo(63);
    }

    @Test
    void anOrderKeepsTheTimeOfItsMomentWhenTheBannerChanges() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        long banner = giveBanner(player, BannerType.BUILDERS, 3);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        var order = body(player.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated()));
        var endAt = JsonPath.<String>read(order, "$.endAt");

        player.browser
                .delete("/api/villages/" + player.villageId + "/banner")
                .andExpect(status().isOk());

        player.village().andExpect(jsonPath("$.buildQueue[0].endAt").value(endAt));
    }

    @Test
    void drillShortensNewRecruitOrders() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        long banner = giveBanner(player, BannerType.DRILL, 3);
        long plain =
                RecruitRules.secondsPerUnit(
                        gameData.unit(UnitType.SPEARMAN), 5, worldService.settings().speed());
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());

        player.recruit(UnitType.SPEARMAN, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secondsPerUnit").value(BannerRules.seconds(plain, 0.15)));
        String recruitment = body(player.recruitment());
        assertThat(
                        JsonPath.<List<Number>>read(
                                        recruitment,
                                        "$[0].units[?(@.unit=='SPEARMAN')].secondsPerUnit")
                                .getFirst()
                                .longValue())
                .isEqualTo(BannerRules.seconds(plain, 0.15));
    }

    // ---- Treasury ----

    @Test
    void treasuryRaisesTheWarehouseAndTheHidingPlace() throws Exception {
        setLevel(player.villageId, BuildingType.HIDING_PLACE, 3);
        String plain = body(player.village());
        int storage = number(plain, "$.storageCapacity").intValue();
        int hiding = number(plain, "$.hidingCapacity").intValue();
        setLevel(player.villageId, BuildingType.BANNER_HALL, 2);
        long banner = giveBanner(player, BannerType.TREASURY, 3);

        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());

        // hall 2: tier II, +20 %
        player.village()
                .andExpect(jsonPath("$.storageCapacity").value(BannerRules.capacity(storage, 0.20)))
                .andExpect(jsonPath("$.hidingCapacity").value(BannerRules.capacity(hiding, 0.20)));
    }

    @Test
    void productionStopsAtTheRaisedCapacity() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 3);
        long banner = giveBanner(player, BannerType.TREASURY, 3);
        player.browser.put(equipPath(player.villageId, banner), "").andExpect(status().isOk());
        setStock(player.villageId, 1250);

        clock.advance(Duration.ofHours(10));

        // 1000 × 1.3: more than the plain warehouse, less than what ten hours would make
        player.village().andExpect(jsonPath("$.resources.wood", closeTo(1300, EPSILON)));
    }

    // ---- helpers ----

    private static String equipPath(long villageId, long bannerId) {
        return "/api/villages/" + villageId + "/banner/" + bannerId;
    }

    private long giveBanner(TestPlayer owner, BannerType type, int tier) {
        return jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, origin, created_at)
                        values (?, ?, ?, 'GRANTED', now()) returning id
                        """)
                .params(owner.playerId, type.name(), tier)
                .query(Long.class)
                .single();
    }

    private Long villageOf(long bannerId) {
        return jdbc.sql("select equipped_village_id from banner where id = ?")
                .param(bannerId)
                .query((rs, n) -> rs.getObject("equipped_village_id", Long.class))
                .list()
                .getFirst();
    }

    private List<String> eventKinds(long bannerId) {
        return jdbc.sql("select kind from banner_event where banner_id = ? order by id")
                .param(bannerId)
                .query(String.class)
                .list();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private double productionWood(TestPlayer p) throws Exception {
        return number(body(p.village()), "$.production.wood").doubleValue();
    }

    /** The woodcutter's plain production at its level (the village has no other bonus). */
    private double basicProduction() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        return productionWood(player);
    }

    private long buildSeconds(BuildingType building, int targetLevel) {
        return BuildRules.buildSeconds(
                gameData.building(building), targetLevel, 1, worldService.settings());
    }

    private record Stock(long wood) {}

    private Stock stock(long villageId) {
        return new Stock(
                jdbc.sql("select floor(wood)::bigint from village where id = ?")
                        .param(villageId)
                        .query(Long.class)
                        .single());
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

    private void setStock(long villageId, int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), villageId)
                .update();
    }

    /** A second village for the player: a barbarian village handed over by SQL. */
    private long addVillage() {
        for (int x = 20; x < TestWorldConfiguration.MAP_SIZE; x++) {
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
                extraVillages.add(id);
                // a warehouse, so the village can be caught up
                setLevel(id, BuildingType.WAREHOUSE, 1);
                setLevel(id, BuildingType.HEADQUARTERS, 1);
                return id;
            }
        }
        throw new IllegalStateException("no free field for a village");
    }

    /** An attack from another player is on its way to the player's first village. */
    private void attackFrom(TestPlayer attacker) throws Exception {
        jdbc.sql("update unit_stack set u_spearman = 5 where home_village_id = ?")
                .params(attacker.villageId)
                .update();
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
        String village = body(player.village());
        Coordinates target =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        attacker.command("ATTACK", target, "{\"SPEARMAN\":5}").andExpect(status().isCreated());
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
