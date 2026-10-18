package de.bannerfall.banner;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Paladin standards (1.4, 02-game-rules §21.18): the standard slot beside the banner, the locks,
 * fusing within the family, the effects in battle, the extra drop roll, the defenders' thieves
 * after a lost attack, scouting and the reports. Luck is off, the dice are fixed per test: {@code
 * 0.0} makes every roll succeed (a drop is then a tier I Harvest banner and a tier I Spear
 * Standard), {@code 0.99} makes every roll fail.
 */
@IntegrationTest
class StandardFlowTest {

    private static final double ALWAYS = 0.0;
    private static final double NEVER = 0.99;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired TestRandomSource dice;

    private double luckRangeBefore;
    private int paladinSlotsBefore;
    private TestPlayer attacker;
    private TestPlayer defender;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        paladinSlotsBefore = worldService.settings().paladinMaxSlots();
        jdbc.sql(
                        "update world_settings set luck_range = 0, banners_enabled = true,"
                                + " banner_drop_factor = 1, standard_theft_enabled = true")
                .update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        endProtection(attacker);
        endProtection(defender);
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 1);
        dice.use(FixedRandomSource.constant(NEVER));
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set luck_range = ?, paladin_max_slots = ?,"
                                + " standard_theft_enabled = true")
                .params(luckRangeBefore, paladinSlotsBefore)
                .update();
        worldService.reload();
    }

    // ---- the slot (§21.18) ----

    @Test
    void aStandardHasItsOwnSlotBesideTheBanner() throws Exception {
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 2);
        long war = give(attacker, BannerType.WAR, 2);
        long axe = give(attacker, BannerType.AXE_STANDARD, 3);

        equip(attacker, war).andExpect(status().isOk());
        String overview =
                body(
                        equip(attacker, axe)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paladinsEnabled").value(true))
                                .andExpect(jsonPath("$.paladinLevel").value(1))
                                .andExpect(jsonPath("$.standardTheftEnabled").value(true))
                                .andExpect(jsonPath("$.villages[0].bannerId").value(war))
                                .andExpect(jsonPath("$.villages[0].effectiveTier").value(2))
                                .andExpect(jsonPath("$.villages[0].standardId").value(axe))
                                // the paladin level caps it: Hall training is still at level 1
                                .andExpect(jsonPath("$.villages[0].standardEffectiveTier").value(1))
                                .andExpect(jsonPath("$.villages[0].standardLocked").value(false)));
        assertThat(JsonPath.<List<Double>>read(overview, "$.effects.KNIGHT_STANDARD"))
                .containsExactly(0.04, 0.08, 0.12);

        // a trained paladin level 2 lets it work at II
        research(attacker, UnitType.PALADIN, 2, clock.instant().minusSeconds(1));
        attacker.browser
                .get("/api/banners")
                .andExpect(jsonPath("$.paladinLevel").value(2))
                .andExpect(jsonPath("$.villages[0].standardEffectiveTier").value(2));

        // a swap sends the old standard back into the stash and leaves the banner alone
        long spear = give(attacker, BannerType.SPEAR_STANDARD, 1);
        equip(attacker, spear).andExpect(status().isOk());
        assertThat(villageOf(axe)).isNull();
        assertThat(villageOf(spear)).isEqualTo(attacker.villageId);
        assertThat(villageOf(war)).isEqualTo(attacker.villageId);

        attacker.browser
                .delete("/api/villages/" + attacker.villageId + "/standard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages[0].standardId").doesNotExist())
                .andExpect(jsonPath("$.villages[0].bannerId").value(war));
        attacker.browser
                .delete("/api/villages/" + attacker.villageId + "/standard")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_BANNER_EQUIPPED"));
        // taking the banner down leaves no standard behind either
        attacker.browser
                .delete("/api/villages/" + attacker.villageId + "/banner")
                .andExpect(status().isOk());
        assertThat(villageOf(war)).isNull();
        // a standard counts for no banner achievement (§21.18)
        assertThat(achievementLevel(attacker, "BANNER_COLLECTOR")).isZero();
    }

    @Test
    void withoutPaladinsNobodyCarriesAStandard() throws Exception {
        jdbc.sql("update world_settings set paladin_max_slots = 0").update();
        worldService.reload();
        long axe = give(attacker, BannerType.AXE_STANDARD, 1);

        equip(attacker, axe)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PALADIN_DISABLED"));
        attacker.browser.get("/api/banners").andExpect(jsonPath("$.paladinsEnabled").value(false));
    }

    @Test
    void theStandardIsLockedWhileAnAttackWithAPaladinIsUnderWay() throws Exception {
        long axe = give(attacker, BannerType.AXE_STANDARD, 1);
        long spear = give(attacker, BannerType.SPEAR_STANDARD, 1);
        long war = give(attacker, BannerType.WAR, 1);
        equip(attacker, axe).andExpect(status().isOk());
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20, UnitType.PALADIN, 1));

        // an attack without the paladin locks nothing
        attack(attacker, defender, "{\"AXEMAN\":10}");
        equip(attacker, spear).andExpect(status().isOk());
        equip(attacker, axe).andExpect(status().isOk());

        attack(attacker, defender, "{\"AXEMAN\":10,\"PALADIN\":1}");
        equip(attacker, spear)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_LOCKED"));
        attacker.browser
                .delete("/api/villages/" + attacker.villageId + "/standard")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_LOCKED"));
        // the village banner isn't locked by the village's own attacks
        equip(attacker, war)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages[0].locked").value(false))
                .andExpect(jsonPath("$.villages[0].standardLocked").value(true));
    }

    @Test
    void anEmptyStandardSlotCanBeFilledWhileAnAttackWithAPaladinIsUnderWay() throws Exception {
        long axe = give(attacker, BannerType.AXE_STANDARD, 1);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20, UnitType.PALADIN, 1));
        attack(attacker, defender, "{\"AXEMAN\":10,\"PALADIN\":1}");
        attacker.browser
                .get("/api/banners")
                .andExpect(jsonPath("$.villages[0].standardLocked").value(false));

        // the attack left without a standard, so nothing can be pulled out of it
        equip(attacker, axe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages[0].standardId").value(axe))
                .andExpect(jsonPath("$.villages[0].standardLocked").value(true));
        attacker.browser
                .delete("/api/villages/" + attacker.villageId + "/standard")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_LOCKED"));
    }

    @Test
    void standardsFuseOnlyWithStandards() throws Exception {
        long a = give(attacker, BannerType.SPEAR_STANDARD, 1);
        long b = give(attacker, BannerType.RAM_STANDARD, 1);
        long c = give(attacker, BannerType.WAR, 1);

        fuse(attacker, "SPEAR_STANDARD", a, b, c)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNER_FAMILIES_DIFFER"));

        long d = give(attacker, BannerType.SIEGE_STANDARD, 1);
        fuse(attacker, "RAM_STANDARD", a, b, d)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners.length()").value(2));
        assertThat(bannersOf(attacker)).containsExactly("WAR:1:GRANTED", "RAM_STANDARD:2:FUSED");
    }

    // ---- in battle ----

    @Test
    void anAttackWithAPaladinCarriesItsVillagesStandard() throws Exception {
        // 100 axemen and a paladin (4 120) against 250 spearmen (3 770): 88 axemen die without a
        // standard, 82 with an Axe Standard at I (4 320), 71 at III (4 720)
        setUnits(defender, Map.of(UnitType.SPEARMAN, 250));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"PALADIN\":1}"));
        assertThat(losses(attacker.newestReport("ATTACK"))).containsEntry("AXEMAN", 88);

        fly(attacker, attacker.villageId, BannerType.AXE_STANDARD, 3);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 250));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"PALADIN\":1}"));
        String report = attacker.newestReport("ATTACK");
        // paladin level 1: the tier III standard works at I
        assertThat(losses(report)).containsEntry("AXEMAN", 82);
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.payload.banners.attackStandard"))
                .isEqualTo(Map.of("type", "AXE_STANDARD", "tier", 3, "effectiveTier", 1));
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("DEFENSE"),
                                "$.payload.banners.attackStandard"))
                .isEqualTo(Map.of("type", "AXE_STANDARD", "tier", 3, "effectiveTier", 1));

        research(attacker, UnitType.PALADIN, 3, clock.instant().minusSeconds(1));
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 3);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 250));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"PALADIN\":1}"));
        assertThat(losses(attacker.newestReport("ATTACK"))).containsEntry("AXEMAN", 71);

        // without the paladin the axemen fight alone, standard or not
        setUnits(defender, Map.of(UnitType.SPEARMAN, 250));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        String alone = attacker.newestReport("ATTACK");
        assertThat((Object) JsonPath.read(alone, "$.payload.banners")).isNull();
    }

    @Test
    void aDefendingStackCarriesItsOwnVillagesStandard() throws Exception {
        // 100 axemen (4 000) against a paladin and 100 spearmen: D = 1 670 without a standard,
        // 1 745 with a Spear Standard I; the axemen lose 27, or 29
        fly(defender, defender.villageId, BannerType.SPEAR_STANDARD, 1);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100, UnitType.PALADIN, 1));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        assertThat(losses(attacker.newestReport("ATTACK"))).containsEntry("AXEMAN", 29);
        String defense = defender.newestReport("DEFENSE");
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defense, "$.payload.defenderSide.stacks[0].standard"))
                .isEqualTo(Map.of("type", "SPEAR_STANDARD", "tier", 1, "effectiveTier", 1));

        // without a paladin at home the standard stays in the village and does nothing
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        // 100 axemen against 1 520: 23 die
        assertThat(losses(attacker.newestReport("ATTACK"))).containsEntry("AXEMAN", 23);
        assertThat(
                        (Object)
                                JsonPath.read(
                                        defender.newestReport("DEFENSE"),
                                        "$.payload.defenderSide.stacks[0].standard"))
                .isNull();
    }

    @Test
    void aSupportWithAPaladinBringsItsHomesStandard() throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setLevel(supporter.villageId, BuildingType.BANNER_HALL, 1);
        fly(supporter, supporter.villageId, BannerType.SPEAR_STANDARD, 1);
        // the defender's own village has a standard too, but no paladin at home
        fly(defender, defender.villageId, BannerType.SWORD_STANDARD, 1);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman,"
                                + " u_paladin) values (?, ?, 50, 1)")
                .params(supporter.villageId, defender.villageId)
                .update();
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        // D = 750 + 750 × 1.05 + 150 + 20 = 1 707.5: 28 axemen die
        assertThat(losses(attacker.newestReport("ATTACK"))).containsEntry("AXEMAN", 28);
        String defense = defender.newestReport("DEFENSE");
        assertThat((Object) JsonPath.read(defense, "$.payload.defenderSide.stacks[0].standard"))
                .isNull();
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defense, "$.payload.defenderSide.stacks[1].standard"))
                .isEqualTo(Map.of("type", "SPEAR_STANDARD", "tier", 1, "effectiveTier", 1));
    }

    @Test
    void aWinTheWinnersPaladinFoughtInRollsForAStandardToo() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"PALADIN\":1}"));

        // two rolls with two pair windows: the banner and the standard both drop
        assertThat(bannersOf(attacker))
                .containsExactly("HARVEST:1:FOUND", "SPEAR_STANDARD:1:FOUND");
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.payload.banners.foundStandard"))
                .isEqualTo(Map.of("type", "SPEAR_STANDARD", "tier", 1));
        assertThat(attacker.bannersGainedIn("ATTACK"))
                .containsExactly(
                        Map.of("type", "HARVEST", "tier", 1),
                        Map.of("type", "SPEAR_STANDARD", "tier", 1));
        // the standard counts for no banner achievement, the banner does
        assertThat(achievementLevel(attacker, "BANNER_COLLECTOR")).isZero();
        assertThat(achievementLevel(attacker, "STANDARD_BEARER")).isEqualTo(1);
    }

    @Test
    void aWinWithoutThePaladinRollsOnlyForABanner() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        assertThat(bannersOf(attacker)).containsExactly("HARVEST:1:FOUND");
    }

    @Test
    void theStandardsHaveTheirOwnDailyCap() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.PALADIN, 1));
        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":100,\"PALADIN\":1}");
        for (int i = 0; i < 8; i++) {
            jdbc.sql(
                            "insert into banner_event (banner_id, kind, type, tier, to_player_id,"
                                    + " happened_at) values (0, 'FOUND', 'AXE_STANDARD', 1, ?, ?)")
                    .params(attacker.playerId, Timestamp.from(arrival.minusSeconds(60)))
                    .update();
        }

        arriveAt(arrival);

        // eight standards today: no ninth, but the banner still drops
        assertThat(bannersOf(attacker)).containsExactly("HARVEST:1:FOUND");
    }

    @Test
    void theOwnerOfAVillageThatHoldsWithHisPaladinRollsForAStandard() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100, UnitType.PALADIN, 1));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 30));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":30}"));

        assertThat(bannersOf(defender))
                .containsExactly("HARVEST:1:FOUND", "SPEAR_STANDARD:1:FOUND");
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("DEFENSE"),
                                "$.payload.banners.foundStandard"))
                .isEqualTo(Map.of("type", "SPEAR_STANDARD", "tier", 1));
    }

    @Test
    void theDefendersThievesTakeTheStandardOfAnAttackTheyBeat() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        long axe = fly(attacker, attacker.villageId, BannerType.AXE_STANDARD, 3);
        // 10 axemen and a paladin (520) against 100 spearmen and 10 thieves (1 720): the
        // defenders lose a share of 0.166, so 8 thieves survive
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.PALADIN, 1));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100, UnitType.BANNER_THIEF, 10));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"PALADIN\":1}"));

        assertThat(ownerOf(axe)).isEqualTo(defender.playerId);
        assertThat(villageOf(axe)).isNull();
        assertThat(
                        count(
                                "select count(*) from banner_event where banner_id = ? and kind ="
                                        + " 'STOLEN' and from_player_id = ? and to_player_id = ?",
                                axe,
                                attacker.playerId,
                                defender.playerId))
                .isEqualTo(1);
        double chance = 1 - Math.pow(1 - 0.03, 8);
        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Integer>read(defense, "$.payload.banners.standardTheft.thieves"))
                .isEqualTo(8);
        assertThat(JsonPath.<Double>read(defense, "$.payload.banners.standardTheft.chance"))
                .isCloseTo(chance, offset(1e-9));
        assertThat(JsonPath.<Boolean>read(defense, "$.payload.banners.standardTheft.stolen"))
                .isTrue();
        assertThat(defender.bannersGainedIn("DEFENSE"))
                .containsExactly(Map.of("type", "AXE_STANDARD", "tier", 3));
        // the attacker only learns that it is gone
        String attack = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Map<String, Object>>read(attack, "$.payload.banners.standardTheft"))
                .containsEntry("stolen", true)
                .containsEntry("thieves", null)
                .containsEntry("chance", null);
        assertThat(attacker.bannersGainedIn("ATTACK")).isEmpty();
    }

    @Test
    void aFailedRollOrTheSwitchLeaveTheStandardWithTheAttacker() throws Exception {
        long axe = fly(attacker, attacker.villageId, BannerType.AXE_STANDARD, 1);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.PALADIN, 1));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100, UnitType.BANNER_THIEF, 10));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"PALADIN\":1}"));

        assertThat(ownerOf(axe)).isEqualTo(attacker.playerId);
        assertThat(villageOf(axe)).isEqualTo(attacker.villageId);
        assertThat(
                        JsonPath.<Boolean>read(
                                defender.newestReport("DEFENSE"),
                                "$.payload.banners.standardTheft.stolen"))
                .isFalse();
        assertThat(
                        (Object)
                                JsonPath.read(
                                        attacker.newestReport("ATTACK"),
                                        "$.payload.banners.standardTheft"))
                .isNull();

        // the host switched it off: no roll at all
        dice.use(FixedRandomSource.constant(ALWAYS));
        jdbc.sql("update world_settings set standard_theft_enabled = false").update();
        worldService.reload();
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.PALADIN, 1));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100, UnitType.BANNER_THIEF, 10));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"PALADIN\":1}"));
        assertThat(ownerOf(axe)).isEqualTo(attacker.playerId);
        assertThat(
                        (Object)
                                JsonPath.read(
                                        defender.newestReport("DEFENSE"),
                                        "$.payload.banners.standardTheft"))
                .isNull();
    }

    @Test
    void aSuccessfulScoutSeesTheStandard() throws Exception {
        fly(defender, defender.villageId, BannerType.KNIGHT_STANDARD, 2);
        setUnits(attacker, Map.of(UnitType.SCOUT, 5));

        arriveAt(attack(attacker, defender, "{\"SCOUT\":5}"));

        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                attacker.newestReport("SCOUT"), "$.payload.scouting.standard"))
                .isEqualTo(Map.of("type", "KNIGHT_STANDARD", "tier", 2));
    }

    // ---- helpers ----

    private Instant attack(TestPlayer from, TestPlayer to, String unitsJson) throws Exception {
        String village = body(to.village());
        Coordinates target =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        String sentCommand =
                body(from.command("ATTACK", target, unitsJson).andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sentCommand, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    private ResultActions equip(TestPlayer player, long bannerId) throws Exception {
        return player.browser.put("/api/villages/" + player.villageId + "/banner/" + bannerId, "");
    }

    private ResultActions fuse(TestPlayer who, String type, long... ids) throws Exception {
        StringBuilder list = new StringBuilder();
        for (long id : ids) {
            list.append(list.isEmpty() ? "" : ",").append(id);
        }
        return who.browser.post(
                "/api/banners/fuse", "{\"bannerIds\":[" + list + "],\"type\":\"" + type + "\"}");
    }

    /** A banner or standard in the player's stash. */
    private long give(TestPlayer owner, BannerType type, int tier) {
        return insertBanner(owner, type, tier, null);
    }

    /** A banner or standard equipped in a village of the player's. */
    private long fly(TestPlayer owner, long villageId, BannerType type, int tier) {
        return insertBanner(owner, type, tier, villageId);
    }

    private long insertBanner(TestPlayer owner, BannerType type, int tier, Long villageId) {
        return jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, ?, ?, ?, case when ?::bigint is null then null else now() end,
                                'GRANTED', now())
                        returning id
                        """)
                .params(owner.playerId, type.name(), tier, villageId, villageId)
                .query(Long.class)
                .single();
    }

    /** A finished Hall training of the player's paladin or thief. */
    private void research(TestPlayer player, UnitType unit, int level, Instant endAt) {
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
                        Timestamp.from(endAt.minusSeconds(100)),
                        Timestamp.from(endAt),
                        Timestamp.from(clock.instant()))
                .update();
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

    private Long villageOf(long bannerId) {
        return jdbc.sql("select equipped_village_id from banner where id = ?")
                .param(bannerId)
                .query((rs, n) -> rs.getObject("equipped_village_id", Long.class))
                .list()
                .getFirst();
    }

    private long ownerOf(long bannerId) {
        return jdbc.sql("select owner_id from banner where id = ?")
                .param(bannerId)
                .query(Long.class)
                .single();
    }

    private int achievementLevel(TestPlayer player, String key) {
        return jdbc.sql(
                        "select coalesce(max(level), 0) from player_achievement where player_id = ?"
                                + " and achievement = ?")
                .params(player.playerId, key)
                .query(Integer.class)
                .single();
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

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
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

    private static Map<String, Integer> losses(String report) {
        return JsonPath.read(report, "$.payload.attackerLosses");
    }
}
