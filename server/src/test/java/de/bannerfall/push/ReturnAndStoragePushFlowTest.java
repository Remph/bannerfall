package de.bannerfall.push;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FakePushService;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.rules.Storage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The 1.6 pushes (04-api <i>Push</i>): "notify me when they're back" for one attack, a train and a
 * farm list run, the bell on a movement, and "storage full". The attacker's device is the {@link
 * FakePushService}; a test push sent afterwards proves that nothing else was pushed before it (the
 * world sends on one thread, in order).
 */
@IntegrationTest
class ReturnAndStoragePushFlowTest {

    private static final FakePushService PUSH = FakePushService.get();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired StorageFullCheck storageCheck;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer attacker;
    private TestPlayer victim;
    private PushDevice device;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        attacker = TestPlayer.register(mockMvc);
        victim = TestPlayer.register(mockMvc);
        unprotect(victim);
        setUnits(attacker, "u_axeman", 30);
        device = PushDevice.create();
        attacker.browser
                .put("/api/push/subscription", device.subscription("en"))
                .andExpect(status().isNoContent());
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void theNewKindsAreListedReturnsOnAndStorageOff() throws Exception {
        String settings = body(attacker.browser.get("/api/push").andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(settings, "$.kinds[*].kind"))
                .contains("ATTACK_RETURNED", "STORAGE_FULL");
        assertThat(
                        JsonPath.<List<Boolean>>read(
                                settings, "$.kinds[?(@.kind=='ATTACK_RETURNED')].enabled"))
                .containsExactly(true);
        assertThat(
                        JsonPath.<List<Boolean>>read(
                                settings, "$.kinds[?(@.kind=='STORAGE_FULL')].enabled"))
                .containsExactly(false);
    }

    @Test
    void aWatchedAttackIsPushedOnceItIsHomeWithItsLoot() throws Exception {
        setResources(victim, 500);
        String sent = body(attack(victim, "{\"AXEMAN\":10}", true).andExpect(status().isCreated()));
        assertThat(JsonPath.<Boolean>read(sent, "$.notifyReturn")).isTrue();
        // an attack sent without it stays quiet
        body(attack(victim, "{\"AXEMAN\":10}", false).andExpect(status().isCreated()));

        runUntilNoMovements();

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(JsonPath.<String>read(back, "$.event.type")).isEqualTo("ATTACKS_RETURNED");
        assertThat(number(back, "$.event.villageId").longValue()).isEqualTo(attacker.villageId);
        assertThat(number(back, "$.event.target.id").longValue()).isEqualTo(victim.villageId);
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(1);
        assertThat(number(back, "$.event.lost").intValue()).isZero();
        assertThat(JsonPath.<Boolean>read(back, "$.event.farmRun")).isFalse();
        int loot =
                number(back, "$.event.loot.wood").intValue()
                        + number(back, "$.event.loot.clay").intValue()
                        + number(back, "$.event.loot.iron").intValue();
        assertThat(loot).isPositive();
        assertThat(number(back, "$.village.id").longValue()).isEqualTo(attacker.villageId);
        assertOnlyPushSoFar(1);
        assertThat(watches()).isZero();
    }

    @Test
    void anAttackNobodyCameBackFromIsPushedAtTheBattle() throws Exception {
        setUnits(victim, "u_spearman", 500);
        attack(victim, "{\"AXEMAN\":1}", true).andExpect(status().isCreated());

        runUntilNoMovements();

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(1);
        assertThat(number(back, "$.event.lost").intValue()).isEqualTo(1);
        assertOnlyPushSoFar(1);
    }

    @Test
    void aTrainIsOnePushForAllItsAttacks() throws Exception {
        String train =
                "{\"target\":%s,\"attacks\":[{\"units\":{\"AXEMAN\":5}},{\"units\":{\"AXEMAN\":5}}],\"notifyReturn\":true}"
                        .formatted(coordinatesJson(victim));
        attacker.browser
                .post(attacker.villagePath() + "/commands/train", train)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].notifyReturn").value(true))
                .andExpect(jsonPath("$[1].notifyReturn").value(true));

        runUntilNoMovements();

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(2);
        assertThat(number(back, "$.event.target.id").longValue()).isEqualTo(victim.villageId);
        assertOnlyPushSoFar(1);
    }

    @Test
    void aFarmListRunIsOnePushWhenItsLastAttackIsHome() throws Exception {
        TestPlayer second = TestPlayer.register(mockMvc);
        unprotect(second);
        // "send all" leaves players' villages out while the sender is protected
        unprotect(attacker);
        addFarmTarget(victim);
        addFarmTarget(second);
        // a lost one counts too
        setUnits(second, "u_spearman", 500);

        attacker.browser
                .post(attacker.villagePath() + "/farm-targets/send-all?notifyReturn=true", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent.length()").value(2));

        runUntilNoMovements();

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(JsonPath.<Boolean>read(back, "$.event.farmRun")).isTrue();
        assertThat(JsonPath.<Object>read(back, "$.event.target")).isNull();
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(2);
        assertThat(number(back, "$.event.lost").intValue()).isEqualTo(1);
        assertOnlyPushSoFar(1);
    }

    @Test
    void aCancelledAttackIsPushedWhenItIsHome() throws Exception {
        long id = number(body(attack(victim, "{\"AXEMAN\":10}", true)), "$.id").longValue();
        attacker.browser
                .post("/api/movements/" + id + "/cancel", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("RETURN"))
                .andExpect(jsonPath("$.notifyReturn").value(true));

        runUntilNoMovements();

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(1);
        assertThat(number(back, "$.event.lost").intValue()).isZero();
        assertOnlyPushSoFar(1);
        assertThat(watches()).isZero();
    }

    @Test
    void aRunWhoseLastAttackIsSwitchedOffIsPushedForTheOthers() throws Exception {
        TestPlayer second = TestPlayer.register(mockMvc);
        unprotect(second);
        unprotect(attacker);
        addFarmTarget(victim);
        addFarmTarget(second);
        attacker.browser
                .post(attacker.villagePath() + "/farm-targets/send-all?notifyReturn=true", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent.length()").value(2));

        // one attack of the run is home, the other one is still on the way
        long last = runUntilOneMovementIsLeft();
        bell(last, false).andExpect(status().isOk());

        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(JsonPath.<Boolean>read(back, "$.event.farmRun")).isTrue();
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(1);
        assertThat(watches()).isZero();
        runUntilNoMovements();
        assertOnlyPushSoFar(1);
    }

    @Test
    void theBellSwitchesOneAttackOnAndOff() throws Exception {
        long id = number(body(attack(victim, "{\"AXEMAN\":10}", false)), "$.id").longValue();

        bell(id, true).andExpect(status().isOk()).andExpect(jsonPath("$.notifyReturn").value(true));
        bell(id, true).andExpect(status().isOk());
        assertThat(watches()).isEqualTo(1);
        bell(id, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyReturn").value(false));
        assertThat(watches()).isZero();
        // nobody else's, and a support doesn't come back
        victim.browser
                .put("/api/movements/" + id + "/return-push", "{\"on\":true}")
                .andExpect(status().isNotFound());
        TestPlayer friend = TestPlayer.register(mockMvc);
        long support =
                number(
                                body(
                                        attacker.command(
                                                        "SUPPORT",
                                                        coordinates(friend),
                                                        "{\"AXEMAN\":1}")
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        bell(support, true).andExpect(status().isConflict());

        // switched on again: pushed when the units are home
        bell(id, true).andExpect(status().isOk());
        runUntilNoMovements();
        String back = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(number(back, "$.event.attacks").intValue()).isEqualTo(1);
    }

    @Test
    void aFullWarehouseIsPushedOnceUntilItWasBelowTheLimit() throws Exception {
        enableStorage(attacker, true);
        int capacity = Storage.warehouseCapacity(1, 0);
        setWarehouseLevel(attacker, 1);
        setResources(attacker, capacity);

        check();
        check();
        String full = device.decrypt(PUSH.await(device.endpoint, 1).get(0).body());
        assertThat(JsonPath.<String>read(full, "$.event.type")).isEqualTo("STORAGE_FULL");
        assertThat(JsonPath.<List<String>>read(full, "$.event.full"))
                .containsExactly("WOOD", "CLAY", "IRON");
        assertThat(number(full, "$.village.id").longValue()).isEqualTo(attacker.villageId);
        assertOnlyPushSoFar(1);

        // spent, then full again: a new push
        setResources(attacker, 0);
        check();
        setResources(attacker, capacity);
        check();
        assertThat(PUSH.await(device.endpoint, 3)).hasSize(3);

        // switched off: nothing, and the mark goes
        enableStorage(attacker, false);
        setResources(attacker, 0);
        check();
        setResources(attacker, capacity);
        check();
        assertThat(
                        jdbc.sql("select count(*) from storage_full_push where village_id = ?")
                                .param(attacker.villageId)
                                .query(Integer.class)
                                .single())
                .isZero();
        attacker.browser.post("/api/push/test", "").andExpect(status().isOk());
        assertThat(
                        JsonPath.<Boolean>read(
                                device.decrypt(PUSH.await(device.endpoint, 4).get(3).body()),
                                "$.test"))
                .isTrue();
    }

    @Test
    void theStorageCheckSchedulesItsNextLook() {
        jdbc.sql(
                        "insert into game_event (kind, ref_id, due_at, status, attempts,"
                                + " created_at) values ('STORAGE_CHECK', null, ?, 'PENDING', 0, ?)")
                .params(Timestamp.from(clock.instant()), Timestamp.from(clock.instant()))
                .update();

        processor.processDue(clock.instant());

        assertThat(
                        jdbc.sql(
                                        "select due_at from game_event where kind = 'STORAGE_CHECK'"
                                                + " and status = 'PENDING'")
                                .query(Timestamp.class)
                                .single()
                                .toInstant())
                .isEqualTo(clock.instant().plus(StorageFullCheck.INTERVAL));
    }

    /** The test push arrives next, so nothing else was pushed before it. */
    private void assertOnlyPushSoFar(int pushes) throws Exception {
        attacker.browser.post("/api/push/test", "").andExpect(status().isOk());
        List<FakePushService.Received> all = PUSH.await(device.endpoint, pushes + 1);
        assertThat(JsonPath.<Boolean>read(device.decrypt(all.get(pushes).body()), "$.test"))
                .isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions attack(
            TestPlayer target, String units, boolean notifyReturn) throws Exception {
        return attacker.browser.post(
                attacker.villagePath() + "/commands",
                "{\"kind\":\"ATTACK\",\"target\":%s,\"units\":%s,\"notifyReturn\":%s}"
                        .formatted(coordinatesJson(target), units, notifyReturn));
    }

    private org.springframework.test.web.servlet.ResultActions bell(long movementId, boolean on)
            throws Exception {
        return attacker.browser.put(
                "/api/movements/" + movementId + "/return-push", "{\"on\":" + on + "}");
    }

    private void addFarmTarget(TestPlayer target) throws Exception {
        attacker.browser
                .post(
                        attacker.villagePath() + "/farm-targets",
                        "{\"target\":%s,\"units\":{\"AXEMAN\":5}}"
                                .formatted(coordinatesJson(target)))
                .andExpect(status().isCreated());
    }

    private void enableStorage(TestPlayer player, boolean on) throws Exception {
        player.browser
                .put(
                        "/api/push/preferences",
                        "{\"kinds\":[{\"kind\":\"STORAGE_FULL\",\"enabled\":" + on + "}]}")
                .andExpect(status().isOk());
    }

    private void check() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> storageCheck.check(clock.instant()));
    }

    /** Handles every arrival of the attacker's units, the returns included, in order. */
    private void runUntilNoMovements() {
        for (int round = 0; round < 20; round++) {
            Timestamp next =
                    jdbc.sql("select min(arrive_at) from movement where owner_player_id = ?")
                            .param(attacker.playerId)
                            .query((rs, row) -> rs.getTimestamp(1))
                            .list()
                            .get(0);
            if (next == null) {
                return;
            }
            Instant at = next.toInstant();
            if (at.isAfter(clock.instant())) {
                clock.set(at);
            }
            processor.processDue(clock.instant());
        }
        throw new AssertionError("The attacker's movements never arrived");
    }

    /** Handles the attacker's arrivals one moment at a time until one movement is left: its id. */
    private long runUntilOneMovementIsLeft() {
        for (int round = 0; round < 20; round++) {
            List<Long> left =
                    jdbc.sql("select id from movement where owner_player_id = ?")
                            .param(attacker.playerId)
                            .query(Long.class)
                            .list();
            if (left.size() == 1) {
                return left.get(0);
            }
            assertThat(left).as("the two attacks of the run landed at once").isNotEmpty();
            Instant at =
                    jdbc.sql("select min(arrive_at) from movement where owner_player_id = ?")
                            .param(attacker.playerId)
                            .query(Timestamp.class)
                            .single()
                            .toInstant();
            if (at.isAfter(clock.instant())) {
                clock.set(at);
            }
            processor.processDue(clock.instant());
        }
        throw new AssertionError("Never only one movement left");
    }

    private int watches() {
        return jdbc.sql("select count(*) from return_watch where player_id = ?")
                .param(attacker.playerId)
                .query(Integer.class)
                .single();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    private static String coordinatesJson(TestPlayer player) throws Exception {
        Coordinates at = coordinates(player);
        return "{\"x\":%d,\"y\":%d}".formatted(at.x(), at.y());
    }

    private void unprotect(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    private void setUnits(TestPlayer player, String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    private void setResources(TestPlayer player, int each) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(each, each, each, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private void setWarehouseLevel(TestPlayer player, int level) {
        jdbc.sql("update village set b_warehouse = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }
}
