package de.bannerfall.banner;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
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
 * The end of the banner milestones (M32c, 02-game-rules §21.4.2, §21.8): fusing three banners into
 * one of the next tier, and the six banner achievements. Luck is off; the dice are fixed per test
 * ({@code 0.0} makes every roll succeed, {@code 0.99} every roll fail).
 */
@IntegrationTest
class BannerFusingFlowTest {

    private static final double ALWAYS = 0.0;
    private static final double NEVER = 0.99;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired TestRandomSource dice;
    @Autowired BannerService bannerService;

    private double luckRangeBefore;
    private TestPlayer player;
    private TestPlayer rival;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        jdbc.sql("update world_settings set luck_range = 0").update();
        setBanners(true);
        player = TestPlayer.register(mockMvc);
        rival = TestPlayer.register(mockMvc);
        endProtection(player);
        endProtection(rival);
        dice.use(FixedRandomSource.constant(NEVER));
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql("update world_settings set luck_range = ?").param(luckRangeBefore).update();
        setBanners(true);
    }

    // ---- fusing (§21.4.2) ----

    @Test
    void threeBannersOfATierBecomeANewOneOfTheNextTierOfAChosenType() throws Exception {
        long war = grant(player, BannerType.WAR, 1);
        long haste = grant(player, BannerType.HASTE, 1);
        long harvest = grant(player, BannerType.HARVEST, 1);

        String overview =
                body(
                        fuse(player, "HASTE", war, haste, harvest)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.banners.length()").value(1))
                                .andExpect(jsonPath("$.banners[0].type").value("HASTE"))
                                .andExpect(jsonPath("$.banners[0].tier").value(2))
                                .andExpect(jsonPath("$.banners[0].villageId").doesNotExist()));

        long fused = number(overview, "$.banners[0].id").longValue();
        // a new banner: new id, origin FUSED; the three are gone
        assertThat(fused).isNotIn(war, haste, harvest);
        assertThat(bannersOf(player)).containsExactly("HASTE:2:FUSED");
        // each of the three: GRANTED, then FUSED to nobody; the new one: FUSED to the player
        for (long part : List.of(war, haste, harvest)) {
            assertThat(events(part)).containsExactly("GRANTED:" + player.playerId, "FUSED:-");
        }
        assertThat(events(fused)).containsExactly("FUSED:" + player.playerId);
        assertThat(
                        jdbc.sql("select detail from banner_event where banner_id = ?")
                                .param(fused)
                                .query(String.class)
                                .single())
                .isEqualTo("from " + war + ", " + haste + ", " + harvest);
        // the log keeps the type and tier of the banners that are gone
        assertThat(
                        jdbc.sql(
                                        "select type || ':' || tier from banner_event where"
                                                + " banner_id = ? and kind = 'FUSED'")
                                .param(war)
                                .query(String.class)
                                .single())
                .isEqualTo("WAR:1");
        // a fused banner is no drop: the daily cap counts FOUND lines only (M32b)
        assertThat(
                        count(
                                "select count(*) from banner_event where to_player_id = ? and kind"
                                        + " = 'FOUND'",
                                player.playerId))
                .isZero();
    }

    @Test
    void twoTierTwoBannersAndATierOneDontFuse() throws Exception {
        long a = grant(player, BannerType.WAR, 2);
        long b = grant(player, BannerType.WAR, 2);
        long c = grant(player, BannerType.WAR, 1);

        fuse(player, "WAR", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_TIERS_DIFFER"));
        assertThat(bannersOf(player)).hasSize(3);
    }

    @Test
    void tierThreeIsTheTop() throws Exception {
        long a = grant(player, BannerType.OATH, 3);
        long b = grant(player, BannerType.OATH, 3);
        long c = grant(player, BannerType.OATH, 3);

        fuse(player, "OATH", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_TIER_MAX"));
    }

    @Test
    void theNewBannerTakesTheTypeOfOneOfTheThree() throws Exception {
        long a = grant(player, BannerType.WAR, 1);
        long b = grant(player, BannerType.HASTE, 1);
        long c = grant(player, BannerType.HASTE, 1);

        fuse(player, "BULWARK", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_FUSE_TYPE"));
        assertThat(bannersOf(player)).hasSize(3);
    }

    @Test
    void aBannerThatFliesCannotBeFused() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long a = grant(player, BannerType.WAR, 1);
        long b = grant(player, BannerType.WAR, 1);
        long c = grant(player, BannerType.WAR, 1);
        player.browser
                .put("/api/villages/" + player.villageId + "/banner/" + c, "")
                .andExpect(status().isOk());

        fuse(player, "WAR", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_IN_STASH"));
    }

    @Test
    void onlyThreeDifferentBannersOfYourOwn() throws Exception {
        long a = grant(player, BannerType.WAR, 1);
        long b = grant(player, BannerType.WAR, 1);
        long foreign = grant(rival, BannerType.WAR, 1);

        fuse(player, "WAR", a, b, foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
        fuse(player, "WAR", a, b)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        fuse(player, "WAR", a, b, b)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.browser
                .post("/api/banners/fuse", "{\"bannerIds\":[" + a + "],\"type\":\"NOPE\"}")
                .andExpect(status().isBadRequest());
        assertThat(bannersOf(player)).hasSize(2);
        assertThat(bannersOf(rival)).hasSize(1);
    }

    @Test
    void nothingFusesWithBannersSwitchedOff() throws Exception {
        long a = grant(player, BannerType.WAR, 1);
        long b = grant(player, BannerType.WAR, 1);
        long c = grant(player, BannerType.WAR, 1);
        setBanners(false);

        fuse(player, "WAR", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
        assertThat(bannersOf(player)).hasSize(3);
    }

    // ---- achievements (§21.8) ----

    @Test
    void theFirstFoundBannerMakesAStandardBearer() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(player, Map.of(UnitType.AXEMAN, 300));
        setUnits(rival, Map.of(UnitType.SPEARMAN, 30));

        arriveAt(attack(player, rival, "{\"AXEMAN\":100}"));

        assertThat(bannersOf(player)).containsExactly("HARVEST:1:FOUND");
        assertThat(achievements(player)).containsEntry("STANDARD_BEARER", 1);
        // the page shows the collection so far
        String page = body(player.browser.get("/api/achievements"));
        assertThat(progress(page, "BANNER_COLLECTOR")).isEqualTo(1);
        assertThat(achievements(rival)).doesNotContainKey("COLOURS_LOST");
    }

    @Test
    void aBannerThatStartsWorkingRaisesTheColours() throws Exception {
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long banner = grant(player, BannerType.TREASURY, 2);
        assertThat(achievements(player)).doesNotContainKey("COLOURS_RAISED");

        player.browser
                .put("/api/villages/" + player.villageId + "/banner/" + banner, "")
                .andExpect(status().isOk());

        assertThat(achievements(player)).containsEntry("COLOURS_RAISED", 1);
    }

    @Test
    void stealingCountsOncePerBannerAndNeverForOnesOwnBanner() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setLevel(rival.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        long banner = grant(rival, BannerType.WAR, 1);
        fly(banner, rival.villageId);

        // the player's thieves take it
        steal(player, rival);
        assertThat(ownerOf(banner)).isEqualTo(player.playerId);
        assertThat(achievements(player)).containsEntry("BANNER_THIEF", 1);
        assertThat(counter(player, "BANNER_THIEF")).isEqualTo(1);
        assertThat(achievements(rival)).containsEntry("COLOURS_LOST", 1);

        // the rival takes his own banner back: nothing counts for him
        fly(banner, player.villageId);
        steal(rival, player);
        assertThat(ownerOf(banner)).isEqualTo(rival.playerId);
        assertThat(counter(rival, "BANNER_THIEF")).isZero();
        assertThat(achievements(player)).containsEntry("COLOURS_LOST", 1);

        // and the player steals it a second time: one banner counts once
        fly(banner, rival.villageId);
        steal(player, rival);
        assertThat(ownerOf(banner)).isEqualTo(player.playerId);
        assertThat(counter(player, "BANNER_THIEF")).isEqualTo(1);
    }

    @Test
    void collectorAndFullSetCountWhatThePlayerHasOwnedNotOnlyWhatHeHasNow() throws Exception {
        // three tier I banners fused into one: Builders and Drill are gone, but were his
        long a = grant(player, BannerType.HARVEST, 1);
        long b = grant(player, BannerType.BUILDERS, 1);
        long c = grant(player, BannerType.DRILL, 1);
        assertThat(achievements(player)).containsEntry("BANNER_COLLECTOR", 1);
        fuse(player, "HARVEST", a, b, c).andExpect(status().isOk());

        for (BannerType type :
                List.of(
                        BannerType.WAR,
                        BannerType.BULWARK,
                        BannerType.HASTE,
                        BannerType.TREASURY,
                        BannerType.PLUNDER)) {
            grant(player, type, 1);
        }
        assertThat(achievements(player)).doesNotContainKey("FULL_SET");

        grant(player, BannerType.OATH, 1);

        assertThat(achievements(player)).containsEntry("FULL_SET", 1);
        // granted banners are owned, not found
        assertThat(achievements(player)).doesNotContainKey("STANDARD_BEARER");
    }

    @Test
    void fusingCountsTheBannersLeftNotTheOnesFusedAway() throws Exception {
        // nine banners (Collector bronze); fusing three leaves seven, which is no silver (10)
        long a = grant(player, BannerType.WAR, 1);
        long b = grant(player, BannerType.WAR, 1);
        long c = grant(player, BannerType.WAR, 1);
        for (int i = 0; i < 6; i++) {
            grant(player, BannerType.HARVEST, 1);
        }
        assertThat(achievements(player)).containsEntry("BANNER_COLLECTOR", 1);

        fuse(player, "WAR", a, b, c).andExpect(status().isOk());

        assertThat(bannersOf(player)).hasSize(7);
        assertThat(achievements(player)).containsEntry("BANNER_COLLECTOR", 1);
    }

    @Test
    void aStolenBannerCountsForTheCollectorAtOnce() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setLevel(rival.villageId, BuildingType.BANNER_HALL, 1);
        grant(player, BannerType.HARVEST, 1);
        grant(player, BannerType.DRILL, 1);
        long banner = grant(rival, BannerType.WAR, 1);
        fly(banner, rival.villageId);
        assertThat(achievements(player)).doesNotContainKey("BANNER_COLLECTOR");

        steal(player, rival);

        // the stolen one is the third
        assertThat(ownerOf(banner)).isEqualTo(player.playerId);
        assertThat(achievements(player)).containsEntry("BANNER_COLLECTOR", 1);
    }

    @Test
    void bannersFromBeforeTheAchievementsAreCountedOnTheNextVisit() throws Exception {
        // banners a world had before M32c: in the table, without any log line
        for (BannerType type : BannerType.values()) {
            jdbc.sql(
                            """
                            insert into banner (owner_id, type, tier, origin, created_at)
                            values (?, ?, 1, 'FOUND', now())
                            """)
                    .params(player.playerId, type.name())
                    .update();
        }
        assertThat(achievements(player)).isEmpty();

        String page = body(player.browser.get("/api/achievements"));

        assertThat(achievements(player))
                .containsEntry("BANNER_COLLECTOR", 1)
                .containsEntry("FULL_SET", 1)
                .doesNotContainKey("STANDARD_BEARER");
        assertThat(progress(page, "BANNER_COLLECTOR")).isEqualTo(9);
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions fuse(
            TestPlayer who, String type, long... ids) throws Exception {
        StringBuilder list = new StringBuilder();
        for (long id : ids) {
            list.append(list.isEmpty() ? "" : ",").append(id);
        }
        return who.browser.post(
                "/api/banners/fuse", "{\"bannerIds\":[" + list + "],\"type\":\"" + type + "\"}");
    }

    private long grant(TestPlayer owner, BannerType type, int tier) {
        return bannerService.grant(owner.playerId, type, tier, "test").id();
    }

    private void fly(long bannerId, long villageId) {
        jdbc.sql("update banner set equipped_village_id = ?, equipped_at = now() where id = ?")
                .params(villageId, bannerId)
                .update();
    }

    /** A won attack of 100 axemen with 10 thieves; the dice decide the theft. */
    private void steal(TestPlayer thief, TestPlayer victim) throws Exception {
        setUnits(thief, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 10));
        setUnits(victim, Map.of());
        arriveAt(attack(thief, victim, "{\"AXEMAN\":100,\"BANNER_THIEF\":10}"));
    }

    private Instant attack(TestPlayer from, TestPlayer to, String unitsJson) throws Exception {
        String sentCommand =
                body(
                        from.command("ATTACK", coordinates(to), unitsJson)
                                .andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sentCommand, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    private long ownerOf(long bannerId) {
        return jdbc.sql("select owner_id from banner where id = ?")
                .param(bannerId)
                .query(Long.class)
                .single();
    }

    /** "TYPE:tier:origin" of every banner the player owns, oldest first. */
    private List<String> bannersOf(TestPlayer player) {
        return jdbc.sql(
                        "select type || ':' || tier || ':' || origin from banner where owner_id ="
                                + " ? order by id")
                .param(player.playerId)
                .query(String.class)
                .list();
    }

    /** "KIND:to player" (or "-") of the banner's log, oldest first. */
    private List<String> events(long bannerId) {
        return jdbc.sql(
                        "select kind || ':' || coalesce(to_player_id::text, '-') from banner_event"
                                + " where banner_id = ? order by id")
                .param(bannerId)
                .query(String.class)
                .list();
    }

    /** The highest level of every achievement the player has. */
    private Map<String, Integer> achievements(TestPlayer player) {
        Map<String, Integer> levels = new HashMap<>();
        jdbc.sql(
                        "select achievement, max(level) from player_achievement where player_id ="
                                + " ? group by achievement")
                .param(player.playerId)
                .query(
                        (rs, n) -> {
                            levels.put(rs.getString(1), rs.getInt(2));
                            return null;
                        })
                .list();
        return levels;
    }

    private long counter(TestPlayer player, String key) {
        return jdbc.sql(
                        "select coalesce(max(value), 0) from achievement_counter where player_id ="
                                + " ? and achievement = ?")
                .params(player.playerId, key)
                .query(Long.class)
                .single();
    }

    private static int progress(String page, String key) {
        List<Number> found =
                JsonPath.read(page, "$.achievements[?(@.key == '" + key + "')].progress");
        return found.getFirst().intValue();
    }

    private void setBanners(boolean enabled) {
        jdbc.sql("update world_settings set banners_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    /** The home stack has exactly these units. */
    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set "
                                    + column("u_", type)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql("update village set " + column("b_", building) + " = ? where id = ?")
                .params(level, villageId)
                .update();
    }

    private void endProtection(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private static String column(String prefix, Enum<?> type) {
        return prefix + type.name().toLowerCase(Locale.ROOT);
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
