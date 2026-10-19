package de.bannerfall.push;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.FakePushService;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestPushConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Push notifications (1.5, 04-api <i>Push</i>): devices sign up, the kinds the player keeps on are
 * encrypted and sent to the push service with the right headers, and devices go when they should.
 * The push service is the {@link FakePushService}; the world sends on one thread, so pushes arrive
 * in the order they were caused.
 */
@IntegrationTest
class PushFlowTest {

    private static final FakePushService PUSH = FakePushService.get();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;

    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void theSettingsShowTheWorldsKeyAndTheDefaults() throws Exception {
        String settings = body(player.browser.get("/api/push").andExpect(status().isOk()));

        assertThat(JsonPath.<Boolean>read(settings, "$.available")).isTrue();
        assertThat(JsonPath.<String>read(settings, "$.publicKey"))
                .isEqualTo(TestPushConfiguration.PUBLIC_KEY);
        assertThat(number(settings, "$.devices").intValue()).isZero();
        assertThat(JsonPath.<List<String>>read(settings, "$.kinds[*].kind"))
                .containsExactly(
                        "ATTACK",
                        "VILLAGE_LOST",
                        "MESSAGE",
                        "GUILD_INVITE",
                        "WORLD_END",
                        "BUILDS_DONE");
        assertThat(JsonPath.<List<Boolean>>read(settings, "$.kinds[*].enabled"))
                .containsExactly(true, true, true, true, true, false);
    }

    @Test
    void onlyAddressesOfThePushServicesAndBrowserKeysAreTaken() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, PushDevice.at("https://evil.example.com/push/1"), "en")
                .andExpect(status().isBadRequest());
        // the private network, or the push service's host on another port
        subscribe(player, PushDevice.at("http://localhost:1/push/1"), "en")
                .andExpect(status().isBadRequest());
        subscribe(
                        player,
                        PushDevice.at("http://user@" + PUSH.origin().substring(7) + "/push/1"),
                        "en")
                .andExpect(status().isBadRequest());
        player.browser
                .put(
                        "/api/push/subscription",
                        device.subscription("en").replace(device.p256dh, "BAAAA"))
                .andExpect(status().isBadRequest());
        subscribe(player, device, "fr").andExpect(status().isBadRequest());
        assertThat(devices(player)).isZero();

        subscribe(player, device, "en").andExpect(status().isNoContent());
        subscribe(player, device, "de").andExpect(status().isNoContent());
        assertThat(devices(player)).isEqualTo(1);
        assertThat(
                        jdbc.sql("select language from push_subscription where endpoint = ?")
                                .param(device.endpoint)
                                .query(String.class)
                                .single())
                .isEqualTo("de");

        // somebody else logs in on the same browser: the device is his now
        TestPlayer other = TestPlayer.register(mockMvc);
        subscribe(other, device, "en").andExpect(status().isNoContent());
        assertThat(devices(player)).isZero();
        assertThat(devices(other)).isEqualTo(1);
    }

    @Test
    void anAttackIsPushedToTheDefendersDevicesWithTheCountSoFar() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "de").andExpect(status().isNoContent());
        TestPlayer attacker = TestPlayer.register(mockMvc);
        unprotect(player);
        setAxemen(attacker, 20);

        String first = attack(attacker, player, 10);
        Instant arriveAt = Instant.parse(JsonPath.read(first, "$.arriveAt"));

        FakePushService.Received push = PUSH.await(device.endpoint, 1).get(0);
        String message = device.decrypt(push.body());
        assertThat(JsonPath.<String>read(message, "$.lang")).isEqualTo("de");
        assertThat(JsonPath.<String>read(message, "$.world"))
                .isEqualTo(
                        jdbc.sql("select name from world_settings").query(String.class).single());
        assertThat(JsonPath.<String>read(message, "$.event.type")).isEqualTo("INCOMING_ATTACK");
        assertThat(number(message, "$.event.sourceVillage.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(number(message, "$.village.id").longValue()).isEqualTo(player.villageId);
        assertThat(JsonPath.<String>read(message, "$.village.name")).isNotBlank();
        assertThat(number(message, "$.attacks").intValue()).isEqualTo(1);
        assertThat(Instant.parse(JsonPath.read(message, "$.firstArrival"))).isEqualTo(arriveAt);
        assertThat(JsonPath.<Object>read(message, "$")).asString().doesNotContain("\"test\"");

        assertThat(push.headers())
                .containsEntry("content-encoding", "aes128gcm")
                .containsEntry("urgency", "high")
                .containsEntry("topic", "attack-" + player.villageId)
                .containsEntry(
                        "ttl",
                        Long.toString(Duration.between(clock.instant(), arriveAt).toSeconds()));
        assertThat(push.headers().get("authorization"))
                .startsWith("vapid t=")
                .endsWith(", k=" + TestPushConfiguration.PUBLIC_KEY);

        // a second attack: one notification that says two are on the way
        attack(attacker, player, 10);
        String second = device.decrypt(PUSH.await(device.endpoint, 2).get(1).body());
        assertThat(number(second, "$.attacks").intValue()).isEqualTo(2);
        assertThat(Instant.parse(JsonPath.read(second, "$.firstArrival"))).isEqualTo(arriveAt);
    }

    @Test
    void attacksWaitingForTheSenderBecomeOnePush() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        TestPlayer attacker = TestPlayer.register(mockMvc);
        unprotect(player);
        setAxemen(attacker, 30);
        // the world's one push thread hangs on another player's device meanwhile
        TestPlayer other = TestPlayer.register(mockMvc);
        PushDevice slow = PushDevice.create();
        subscribe(other, slow, "en").andExpect(status().isNoContent());
        PUSH.hold(slow.endpoint);
        try {
            other.browser.post("/api/push/test", "").andExpect(status().isOk());
            PUSH.await(slow.endpoint, 1);

            attack(attacker, player, 10);
            attack(attacker, player, 10);
            String last = attack(attacker, player, 10);
            // tapping "Send a test" again and again queues one test
            for (int i = 0; i < 5; i++) {
                player.browser.post("/api/push/test", "").andExpect(status().isOk());
            }
            PUSH.release(slow.endpoint);

            List<FakePushService.Received> pushes = PUSH.await(device.endpoint, 2);
            String attacks = device.decrypt(pushes.get(0).body());
            assertThat(number(attacks, "$.attacks").intValue()).isEqualTo(3);
            // kept until the last of them has landed
            Instant lastArrival = Instant.parse(JsonPath.read(last, "$.arriveAt"));
            assertThat(pushes.get(0).headers())
                    .containsEntry(
                            "ttl",
                            Long.toString(
                                    Duration.between(clock.instant(), lastArrival).toSeconds()));
            assertThat(JsonPath.<Boolean>read(device.decrypt(pushes.get(1).body()), "$.test"))
                    .isTrue();
            // the thread sends in order: a test sent now arrives after anything else for him
            player.browser.post("/api/push/test", "").andExpect(status().isOk());
            assertThat(PUSH.await(device.endpoint, 3)).hasSize(3);
        } finally {
            PUSH.release(slow.endpoint);
        }
    }

    @Test
    void aKindSwitchedOffIsNotPushedButATestAlwaysIs() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        player.browser
                .put(
                        "/api/push/preferences",
                        "{\"kinds\":[{\"kind\":\"ATTACK\",\"enabled\":false},"
                                + "{\"kind\":\"BUILDS_DONE\",\"enabled\":true}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kinds[0].kind").value("ATTACK"))
                .andExpect(jsonPath("$.kinds[0].enabled").value(false))
                .andExpect(jsonPath("$.kinds[1].enabled").value(true))
                .andExpect(jsonPath("$.kinds[5].enabled").value(true));
        TestPlayer attacker = TestPlayer.register(mockMvc);
        unprotect(player);
        setAxemen(attacker, 10);

        attack(attacker, player, 10);
        player.browser
                .post("/api/push/test", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices").value(1));

        // one thread, in order: had the attack been pushed, it would have come first
        List<FakePushService.Received> pushes = PUSH.await(device.endpoint, 1);
        String test = device.decrypt(pushes.get(0).body());
        assertThat(JsonPath.<Boolean>read(test, "$.test")).isTrue();
        assertThat(JsonPath.<Object>read(test, "$")).asString().doesNotContain("event");
        assertThat(pushes).hasSize(1);
    }

    @Test
    void aPrivateMessageIsPushed() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        TestPlayer sender = TestPlayer.register(mockMvc);

        sender.browser
                .post("/api/messages/with/" + player.playerId, "{\"text\":\"Hello there\"}")
                .andExpect(status().is2xxSuccessful());

        FakePushService.Received push = PUSH.await(device.endpoint, 1).get(0);
        String message = device.decrypt(push.body());
        assertThat(JsonPath.<String>read(message, "$.event.type")).isEqualTo("MESSAGE_RECEIVED");
        assertThat(number(message, "$.event.fromPlayerId").longValue()).isEqualTo(sender.playerId);
        // the text itself stays off the lock screen
        assertThat(message).doesNotContain("Hello there");
        assertThat(push.headers())
                .containsEntry("urgency", "normal")
                .containsEntry("topic", "message-" + sender.playerId)
                .containsEntry("ttl", "86400");
    }

    @Test
    void finishedBuildsArePushedOnlyWhenTheQueueRunsEmpty() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        player.browser
                .put(
                        "/api/push/preferences",
                        "{\"kinds\":[{\"kind\":\"BUILDS_DONE\",\"enabled\":true}]}")
                .andExpect(status().isOk());
        player.enqueueOk(BuildingType.IRON_MINE);
        player.enqueueOk(BuildingType.CLAY_PIT);
        List<Instant> ends =
                jdbc
                        .sql("select end_at from build_order where village_id = ? order by end_at")
                        .param(player.villageId)
                        .query(Timestamp.class)
                        .list()
                        .stream()
                        .map(Timestamp::toInstant)
                        .toList();

        runWorker(ends.get(0));
        player.browser.post("/api/push/test", "").andExpect(status().isOk());
        assertThat(
                        JsonPath.<Boolean>read(
                                device.decrypt(PUSH.await(device.endpoint, 1).get(0).body()),
                                "$.test"))
                .isTrue();

        runWorker(ends.get(1));
        String done = device.decrypt(PUSH.await(device.endpoint, 2).get(1).body());
        assertThat(JsonPath.<String>read(done, "$.event.type")).isEqualTo("BUILD_COMPLETE");
        assertThat(JsonPath.<String>read(done, "$.event.building")).isEqualTo("CLAY_PIT");
        assertThat(number(done, "$.village.id").longValue()).isEqualTo(player.villageId);
        assertThat(PUSH.to(device.endpoint)).hasSize(2);
    }

    @Test
    void aDeviceThePushServiceCallsGoneIsDeleted() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        PUSH.answer(device.endpoint, 410);

        player.browser.post("/api/push/test", "").andExpect(status().isOk());

        PUSH.await(device.endpoint, 1);
        awaitDevices(player, 0);
    }

    @Test
    void devicesGoOnLogoutAndWhenTheSessionsEnd() throws Exception {
        PushDevice device = PushDevice.create();
        subscribe(player, device, "en").andExpect(status().isNoContent());
        TestPlayer other = TestPlayer.register(mockMvc);
        // nobody else can remove it
        other.browser
                .post("/api/push/subscription/remove", remove(device))
                .andExpect(status().isNoContent());
        assertThat(devices(player)).isEqualTo(1);

        player.browser
                .post("/api/push/subscription/remove", remove(device))
                .andExpect(status().isNoContent());
        assertThat(devices(player)).isZero();

        subscribe(player, device, "en").andExpect(status().isNoContent());
        subscribe(player, PushDevice.create(), "en").andExpect(status().isNoContent());
        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\""
                                + Browser.PASSWORD
                                + "\",\"newPassword\":\"brand new pw\"}")
                .andExpect(status().is2xxSuccessful());
        assertThat(devices(player)).isZero();
    }

    @Test
    void aPlayerKeepsAtMostTenDevices() throws Exception {
        PushDevice first = PushDevice.create();
        subscribe(player, first, "en").andExpect(status().isNoContent());
        for (int i = 0; i < PushSubscriptionService.MAX_DEVICES; i++) {
            clock.advance(Duration.ofSeconds(1));
            subscribe(player, PushDevice.create(), "en").andExpect(status().isNoContent());
        }
        assertThat(devices(player)).isEqualTo(PushSubscriptionService.MAX_DEVICES);
        assertThat(
                        jdbc.sql("select count(*) from push_subscription where endpoint = ?")
                                .param(first.endpoint)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    private static org.springframework.test.web.servlet.ResultActions subscribe(
            TestPlayer player, PushDevice device, String language) throws Exception {
        return player.browser.put("/api/push/subscription", device.subscription(language));
    }

    private static String remove(PushDevice device) {
        return "{\"endpoint\":\"" + device.endpoint + "\"}";
    }

    private static int devices(TestPlayer player) throws Exception {
        return number(body(player.browser.get("/api/push")), "$.devices").intValue();
    }

    private static void awaitDevices(TestPlayer player, int count) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (devices(player) != count && System.nanoTime() < until) {
            Thread.sleep(20);
        }
        assertThat(devices(player)).isEqualTo(count);
    }

    private String attack(TestPlayer from, TestPlayer to, int axemen) throws Exception {
        String village = body(to.village());
        Coordinates target =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        return body(
                from.command("ATTACK", target, "{\"AXEMAN\":" + axemen + "}")
                        .andExpect(status().isCreated()));
    }

    private void unprotect(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    private void setAxemen(TestPlayer player, int count) {
        jdbc.sql(
                        "update unit_stack set u_axeman = ? where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    private void runWorker(Instant until) {
        clock.set(until);
        processor.processDue(until);
    }
}
