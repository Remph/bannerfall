package de.bannerfall.notify;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.sql.Timestamp;
import java.time.Duration;
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
 * The game's services notify the right players (04-api <i>WebSocket</i>): build complete, incoming
 * attack, battle (reports, changed villages), support arrived, units home, cancel, withdraw. What
 * is recorded is what the server hands to the broker ({@link SentMessages}).
 */
@IntegrationTest
class GameNotificationFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired SentMessages sent;

    private TestPlayer attacker;
    private TestPlayer defender;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void aFinishedBuildingIsAnnouncedToItsOwnerOnly() throws Exception {
        attacker.enqueueOk(BuildingType.IRON_MINE);
        assertThat(sent.toPlayer(attacker.playerId)).isEmpty();

        runWorker(clock.instant().plus(Duration.ofDays(1)));

        assertThat(sent.toPlayer(attacker.playerId))
                .containsExactly(
                        "{\"type\":\"BUILD_COMPLETE\",\"villageId\":%d,\"building\":\"IRON_MINE\",\"level\":2,\"demolished\":false}"
                                .formatted(attacker.villageId));
        assertThat(sent.toPlayer(defender.playerId)).isEmpty();
    }

    @Test
    void anAttackIsAnnouncedWhenSentAndItsBattleTellsBothSides() throws Exception {
        setHomeUnits(attacker, UnitType.AXEMAN, 10);
        String movement = send(attacker, "ATTACK", defender, "{\"AXEMAN\":10}");
        Instant arriveAt = Instant.parse(JsonPath.read(movement, "$.arriveAt"));

        List<String> incoming = sent.toPlayer(defender.playerId);
        assertThat(incoming).hasSize(1);
        assertThat(JsonPath.<String>read(incoming.get(0), "$.type")).isEqualTo("INCOMING_ATTACK");
        assertThat(number(incoming.get(0), "$.targetVillageId").longValue())
                .isEqualTo(defender.villageId);
        assertThat(number(incoming.get(0), "$.sourceVillage.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(JsonPath.<String>read(incoming.get(0), "$.sourcePlayerName"))
                .isEqualTo(JsonPath.<String>read(incoming.get(0), "$.sourceVillage.playerName"))
                .isNotNull();
        assertThat(Instant.parse(JsonPath.read(incoming.get(0), "$.arriveAt"))).isEqualTo(arriveAt);
        assertThat(sent.toPlayer(attacker.playerId)).isEmpty();

        runWorker(arriveAt);

        // 10 axemen beat the starting village: a counted win (02-game-rules §19)
        assertThat(sent.toPlayer(attacker.playerId))
                .containsExactlyInAnyOrder(
                        reportCreated(attacker, "ATTACK"),
                        villageUpdated(attacker.villageId),
                        "{\"type\":\"ACHIEVEMENT_EARNED\",\"achievement\":\"FIRST_BLOOD\",\"leveled\":false,\"level\":1}");
        assertThat(sent.toPlayer(defender.playerId))
                .containsExactly(
                        incoming.get(0),
                        reportCreated(defender, "DEFENSE"),
                        villageUpdated(defender.villageId));
    }

    @Test
    void aBattleAlsoTellsTheSupporters() throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setHomeUnits(supporter, UnitType.SPEARMAN, 10);
        String support = send(supporter, "SUPPORT", defender, "{\"SPEARMAN\":10}");
        runWorker(Instant.parse(JsonPath.read(support, "$.arriveAt")));
        setHomeUnits(attacker, UnitType.AXEMAN, 10);
        String attack = send(attacker, "ATTACK", defender, "{\"AXEMAN\":10}");
        int before = sent.toPlayer(supporter.playerId).size();

        runWorker(Instant.parse(JsonPath.read(attack, "$.arriveAt")));

        assertThat(sent.toPlayer(supporter.playerId).subList(before, before + 2))
                .containsExactly(
                        reportCreated(supporter, "DEFENSE"), villageUpdated(supporter.villageId));
    }

    @Test
    void anArrivingSupportTellsBothOwnersAboutTheirOwnVillage() throws Exception {
        setHomeUnits(attacker, UnitType.SPEARMAN, 10);
        String support = send(attacker, "SUPPORT", defender, "{\"SPEARMAN\":10}");
        assertThat(sent.toPlayer(defender.playerId)).isEmpty();

        runWorker(Instant.parse(JsonPath.read(support, "$.arriveAt")));

        assertThat(sent.toPlayer(defender.playerId))
                .containsExactly(
                        reportCreated(defender, "SUPPORT_ARRIVED"),
                        "{\"type\":\"SUPPORT_ARRIVED\",\"villageId\":%d}"
                                .formatted(defender.villageId));
        assertThat(sent.toPlayer(attacker.playerId))
                .containsExactly(
                        reportCreated(attacker, "SUPPORT_ARRIVED"),
                        "{\"type\":\"SUPPORT_ARRIVED\",\"villageId\":%d}"
                                .formatted(attacker.villageId));
    }

    @Test
    void aSupportBetweenTwoOwnVillagesTellsAboutBoth() throws Exception {
        Coordinates second = coordinates(defender);
        // the defender's village becomes the attacker's second village
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(attacker.playerId, defender.villageId)
                .update();
        setHomeUnits(attacker, UnitType.SPEARMAN, 10);
        String support =
                body(
                        attacker.command("SUPPORT", second, "{\"SPEARMAN\":10}")
                                .andExpect(status().isCreated()));

        runWorker(Instant.parse(JsonPath.read(support, "$.arriveAt")));

        assertThat(sent.toPlayer(attacker.playerId))
                .containsExactly(
                        reportCreated(attacker, "SUPPORT_ARRIVED"),
                        "{\"type\":\"SUPPORT_ARRIVED\",\"villageId\":%d}"
                                .formatted(defender.villageId),
                        "{\"type\":\"SUPPORT_ARRIVED\",\"villageId\":%d}"
                                .formatted(attacker.villageId));
        assertThat(sent.toPlayer(defender.playerId)).isEmpty();
    }

    @Test
    void sendingBackASupportTellsItsOwnerAndUnitsComingHomeToo() throws Exception {
        setHomeUnits(attacker, UnitType.SPEARMAN, 10);
        String support = send(attacker, "SUPPORT", defender, "{\"SPEARMAN\":10}");
        runWorker(Instant.parse(JsonPath.read(support, "$.arriveAt")));
        long stackId = number(body(defender.troops()), "$.supportsHere[0].stackId").longValue();
        int before = sent.toPlayer(attacker.playerId).size();

        String back =
                body(
                        defender.returnStack(stackId, "{\"SPEARMAN\":10}")
                                .andExpect(status().isCreated()));

        assertThat(sent.toPlayer(attacker.playerId).subList(before, before + 2))
                .containsExactly(
                        villageUpdated(attacker.villageId),
                        reportCreated(attacker, "SUPPORT_RETURNED"));

        runWorker(Instant.parse(JsonPath.read(back, "$.arriveAt")));

        assertThat(sent.toPlayer(attacker.playerId))
                .last()
                .isEqualTo(villageUpdated(attacker.villageId));
    }

    @Test
    void aCancelledAttackTellsTheTarget() throws Exception {
        setHomeUnits(attacker, UnitType.AXEMAN, 10);
        String movement = send(attacker, "ATTACK", defender, "{\"AXEMAN\":10}");

        attacker.cancelMovement(number(movement, "$.id").longValue()).andExpect(status().isOk());

        assertThat(sent.toPlayer(defender.playerId))
                .last()
                .isEqualTo(villageUpdated(defender.villageId));
    }

    @Test
    void aRefusedCommandSendsNothing() throws Exception {
        setHomeUnits(attacker, UnitType.AXEMAN, 5);

        attacker.command("ATTACK", coordinates(defender), "{\"AXEMAN\":10}")
                .andExpect(status().isConflict());

        assertThat(sent.toPlayer(defender.playerId)).isEmpty();
        assertThat(sent.toPlayer(attacker.playerId)).isEmpty();
    }

    // ---- helpers ----

    private String send(TestPlayer from, String kind, TestPlayer to, String unitsJson)
            throws Exception {
        return body(from.command(kind, coordinates(to), unitsJson).andExpect(status().isCreated()));
    }

    private void runWorker(Instant until) {
        clock.set(until);
        processor.processDue(until);
    }

    /** The REPORT_CREATED notification of the player's newest report of this kind. */
    private static String reportCreated(TestPlayer player, String kind) throws Exception {
        List<Number> ids =
                JsonPath.read(body(player.reports()), "$.items[?(@.kind == '" + kind + "')].id");
        assertThat(ids).isNotEmpty();
        return "{\"type\":\"REPORT_CREATED\",\"reportId\":%d,\"kind\":\"%s\""
                        .formatted(ids.get(0).longValue(), kind)
                + ",\"sharedBy\":null}";
    }

    private static String villageUpdated(long villageId) {
        return "{\"type\":\"VILLAGE_UPDATED\",\"villageId\":" + villageId + "}";
    }

    private void setHomeUnits(TestPlayer player, UnitType type, int count) {
        jdbc.sql(
                        "update unit_stack set u_"
                                + type.name().toLowerCase(Locale.ROOT)
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
