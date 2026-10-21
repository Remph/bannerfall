package de.bannerfall.combat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
 * Morale in real battles (1.6, 02-game-rules §8.2). Luck is off. The players' points are set
 * directly; the battle reads them at the arrival, the preview when it is asked.
 */
@IntegrationTest
class MoraleFlowTest {

    private static final int BARBARIAN_ROW = TestWorldConfiguration.MAP_SIZE - 23;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;

    private WorldSettings before;
    private TestPlayer attacker;
    private TestPlayer defender;
    private final List<Long> barbarianVillages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        before = worldService.settings();
        jdbc.sql("update world_settings set luck_range = 0").update();
        setMorale(true, 30);
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        for (long id : barbarianVillages) {
            jdbc.sql(
                            "delete from movement where home_village_id = :id or"
                                    + " source_village_id = :id or target_village_id = :id")
                    .param("id", id)
                    .update();
            jdbc.sql(
                            "delete from unit_stack where home_village_id = :id"
                                    + " or location_village_id = :id")
                    .param("id", id)
                    .update();
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        barbarianVillages.clear();
        jdbc.sql(
                        "update world_settings set luck_range = ?, morale_enabled = ?,"
                                + " morale_floor = ?")
                .params(before.luckRange(), before.moraleEnabled(), before.moraleFloor())
                .update();
        worldService.reload();
    }

    @Test
    void anAttackOnAMuchSmallerPlayerHitsWeakerAndBothReportsSaySo() throws Exception {
        setPoints(attacker, 10_000);
        setPoints(defender, 1_000);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        Coordinates target = coordinates(defender);
        // 0.3 + 3 × 0.1 = 60 %, already before the attack is sent
        String preview =
                body(
                        attacker.previewCommand("ATTACK", target, "{\"AXEMAN\":50}")
                                .andExpect(status().isOk()));
        assertThat(JsonPath.<Double>read(preview, "$.morale")).isEqualTo(0.6);

        // A = 2000 × 0.6 = 1200 against D = 1500 + 20 = 1520: lost (2000 would have won)
        arriveAt(attack(target, "{\"AXEMAN\":50}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("DEFENDER_WON");
        assertThat(JsonPath.<Double>read(report, "$.payload.morale")).isEqualTo(0.6);
        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Double>read(defense, "$.payload.morale")).isEqualTo(0.6);
        // (1200 / 1520)^1.5 = 0.7015 of the spearmen die
        assertThat(JsonPath.<Integer>read(defense, "$.payload.defenderSide.losses.SPEARMAN"))
                .isEqualTo(70);
    }

    @Test
    void theHostsFloorHolds() throws Exception {
        setMorale(true, 50);
        setPoints(attacker, 10_000);
        setPoints(defender, 100);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 10));

        // 0.3 + 3 × 0.01 = 33 %, raised to the floor
        arriveAt(attack(coordinates(defender), "{\"AXEMAN\":50}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Double>read(report, "$.payload.morale")).isEqualTo(0.5);
    }

    @Test
    void aSmallerAttackerAndAWorldWithoutMoraleFightAtFullStrength() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        Coordinates target = coordinates(defender);

        // the smaller one attacks: never above 100 %
        setPoints(attacker, 1_000);
        setPoints(defender, 10_000);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        String up =
                body(
                        attacker.previewCommand("ATTACK", target, "{\"AXEMAN\":50}")
                                .andExpect(status().isOk()));
        assertThat((Object) JsonPath.read(up, "$.morale")).isNull();
        arriveAt(attack(target, "{\"AXEMAN\":50}"));
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(report, "$.payload.morale")).isNull();

        // the bigger one attacks in a world with morale off
        setMorale(false, 30);
        setPoints(attacker, 10_000);
        setPoints(defender, 1_000);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        String off =
                body(
                        attacker.previewCommand("ATTACK", target, "{\"AXEMAN\":50}")
                                .andExpect(status().isOk()));
        assertThat((Object) JsonPath.read(off, "$.morale")).isNull();
        arriveAt(attack(target, "{\"AXEMAN\":50}"));
        String switchedOff = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(switchedOff, "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(switchedOff, "$.payload.morale")).isNull();
    }

    @Test
    void theBattleReadsThePointsAtTheArrival() throws Exception {
        setPoints(attacker, 10_000);
        setPoints(defender, 1_000);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        Coordinates target = coordinates(defender);
        // 60 % when sent (the preview says so) ...
        String preview =
                body(
                        attacker.previewCommand("ATTACK", target, "{\"AXEMAN\":50}")
                                .andExpect(status().isOk()));
        assertThat(JsonPath.<Double>read(preview, "$.morale")).isEqualTo(0.6);
        Instant arrival = attack(target, "{\"AXEMAN\":50}");

        // ... but the defender grows to a quarter of the attacker's points while it walks
        setPoints(defender, 2_500);
        arriveAt(arrival);

        // A = 2000 at full strength against D = 1520: won
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(report, "$.payload.morale")).isNull();
    }

    @Test
    void barbarianVillagesAreAlwaysAttackedAtFullStrength() throws Exception {
        setPoints(attacker, 10_000);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10));
        long barbarian = barbarianVillage();
        Coordinates target = coordinatesOf(barbarian);
        String preview =
                body(
                        attacker.previewCommand("ATTACK", target, "{\"AXEMAN\":1}")
                                .andExpect(status().isOk()));
        assertThat((Object) JsonPath.read(preview, "$.morale")).isNull();

        // A = 40 against the base defence 20: won; at the floor (12) it would be lost
        arriveAt(attack(target, "{\"AXEMAN\":1}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(report, "$.payload.morale")).isNull();
    }

    @Test
    void aSupportAndScoutsShowNoMorale() throws Exception {
        setPoints(attacker, 10_000);
        setPoints(defender, 1_000);
        setUnits(attacker, Map.of(UnitType.SCOUT, 5, UnitType.SPEARMAN, 5));
        Coordinates target = coordinates(defender);

        String support =
                body(
                        attacker.previewCommand("SUPPORT", target, "{\"SPEARMAN\":5}")
                                .andExpect(status().isOk()));
        assertThat((Object) JsonPath.read(support, "$.morale")).isNull();

        // scouts alone fight no main battle (§7.1), so morale doesn't touch them
        arriveAt(attack(target, "{\"SCOUT\":5}"));
        String report = attacker.newestReport("SCOUT");
        assertThat((Object) JsonPath.read(report, "$.payload.morale")).isNull();
    }

    @Test
    void anAttackTrainPreviewShowsItToo() throws Exception {
        setPoints(attacker, 10_000);
        setPoints(defender, 1_000);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));

        String preview =
                body(
                        attacker.previewTrain(
                                        coordinates(defender), "{\"AXEMAN\":10}", "{\"AXEMAN\":10}")
                                .andExpect(status().isOk()));

        assertThat(JsonPath.<Double>read(preview, "$.morale")).isEqualTo(0.6);
    }

    // ---- helpers ----

    private void setMorale(boolean enabled, int floor) {
        jdbc.sql("update world_settings set morale_enabled = ?, morale_floor = ?")
                .params(enabled, floor)
                .update();
        worldService.reload();
    }

    private void setPoints(TestPlayer player, int points) {
        jdbc.sql("update player set points = ? where id = ?")
                .params(points, player.playerId)
                .update();
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

    /** A barbarian village (points 0, no owner) with nobody in it. */
    private long barbarianVillage() {
        for (int x = 10; x < TestWorldConfiguration.MAP_SIZE; x += 3) {
            Long id =
                    jdbc.sql(
                                    """
                                    insert into village (player_id, name, x, y, wood, clay, iron,
                                                         resources_updated_at, loyalty_updated_at,
                                                         points, created_at)
                                    values (null, null, :x, :y, 0, 0, 0, now(), now(), 0, now())
                                    on conflict (x, y) do nothing
                                    returning id
                                    """)
                            .param("x", x)
                            .param("y", BARBARIAN_ROW)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                barbarianVillages.add(id);
                jdbc.sql(
                                "insert into unit_stack (home_village_id, location_village_id)"
                                        + " values (?, ?)")
                        .params(id, id)
                        .update();
                jdbc.sql("update village set b_warehouse = 1, b_headquarters = 1 where id = ?")
                        .param(id)
                        .update();
                return id;
            }
        }
        throw new IllegalStateException("no free field for a barbarian village");
    }

    private Coordinates coordinatesOf(long villageId) {
        return jdbc.sql("select x, y from village where id = ?")
                .param(villageId)
                .query((rs, n) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                .single();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
