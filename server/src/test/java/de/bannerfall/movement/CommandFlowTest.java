package de.bannerfall.movement;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sending, cancelling, arriving and withdrawing troops (M7a) between two registered players; the
 * clock is moved by hand and the event worker is called directly. The attacker has 100 spearmen (18
 * min/field), 50 axemen and 10 light cavalry (10 min/field) at home; the defender has no beginner
 * protection. Villages are at least one field apart, so spearmen are on the way for more than the
 * cancel window (600 s).
 */
@IntegrationTest
class CommandFlowTest {

    private static final double EPSILON = 1e-6;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired MovementRepository movements;
    @Autowired VillageRepository villages;
    @Autowired EventScheduler scheduler;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer attacker;
    private TestPlayer defender;
    private Coordinates attackerAt;
    private Coordinates defenderAt;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        attackerAt = coordinates(attacker);
        defenderAt = coordinates(defender);
        setHomeUnits(attacker, "u_spearman", 100);
        setHomeUnits(attacker, "u_axeman", 50);
        setHomeUnits(attacker, "u_light_cavalry", 10);
        setProtectedUntil(defender, clock.instant().minusSeconds(1));
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- sending ----

    @Test
    void aCommandWalksAtTheSpeedOfItsSlowestUnitAndLeavesHomeAtOnce() throws Exception {
        int populationBefore = populationUsed(attacker);
        Instant now = clock.instant();

        String sent = sendOk("SUPPORT", defenderAt, "{\"SPEARMAN\":10,\"LIGHT_CAVALRY\":5}");

        assertThat(JsonPath.<String>read(sent, "$.kind")).isEqualTo("SUPPORT");
        assertThat(instant(sent, "$.departAt")).isEqualTo(now);
        assertThat(instant(sent, "$.arriveAt")).isEqualTo(now.plusSeconds(travelSeconds(18)));
        assertThat(instant(sent, "$.cancellableUntil")).isEqualTo(now.plusSeconds(600));
        attacker.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(90))
                .andExpect(jsonPath("$.unitsHome.LIGHT_CAVALRY").value(5))
                // units on the way still belong to the village
                .andExpect(jsonPath("$.population.used").value(populationBefore));
        attacker.troops()
                .andExpect(jsonPath("$.home.SPEARMAN").value(90))
                .andExpect(jsonPath("$.outgoing", hasSize(1)))
                .andExpect(jsonPath("$.outgoing[0].id").value(id(sent)))
                .andExpect(jsonPath("$.outgoing[0].targetVillage.id").value(defender.villageId));
        defender.troops()
                .andExpect(jsonPath("$.incoming", hasSize(1)))
                .andExpect(jsonPath("$.incoming[0].kind").value("SUPPORT"))
                .andExpect(jsonPath("$.incoming[0].sourceVillage.id").value(attacker.villageId))
                .andExpect(jsonPath("$.incoming[0].units.SPEARMAN").value(10));
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(0));
    }

    @Test
    void thePreviewShowsTheTripAndEveryError() throws Exception {
        Instant now = clock.instant();

        String preview =
                body(
                        attacker.previewCommand("ATTACK", defenderAt, "{\"SPEARMAN\":10}")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.targetVillage.id").value(defender.villageId))
                                .andExpect(
                                        jsonPath("$.targetVillage.playerId")
                                                .value(defender.playerId))
                                .andExpect(
                                        jsonPath("$.distance")
                                                .value(
                                                        closeTo(
                                                                Travel.distance(
                                                                        attackerAt, defenderAt),
                                                                1e-9)))
                                .andExpect(jsonPath("$.travelSeconds").value(travelSeconds(18)))
                                .andExpect(jsonPath("$.errors").isEmpty()));
        assertThat(instant(preview, "$.arriveAt")).isEqualTo(now.plusSeconds(travelSeconds(18)));

        attacker.previewCommand("SUPPORT", attackerAt, "{\"SPEARMAN\":101,\"NOBLE\":1}")
                .andExpect(
                        jsonPath(
                                "$.errors",
                                contains(
                                        "NOT_ENOUGH_UNITS",
                                        "SAME_VILLAGE",
                                        "NOBLE_ONLY_IN_ATTACK")));
        attacker.previewCommand("ATTACK", defenderAt, "{}")
                .andExpect(jsonPath("$.errors", contains("EMPTY_COMMAND")))
                .andExpect(jsonPath("$.travelSeconds").value(nullValue()))
                .andExpect(jsonPath("$.arriveAt").value(nullValue()));
        attacker.previewCommand("ATTACK", new Coordinates(-5, -5), "{\"SPEARMAN\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        // the preview changes nothing
        attacker.troops().andExpect(jsonPath("$.outgoing").isEmpty());
    }

    @Test
    void sendAnswersWithTheFirstErrorAndChangesNothing() throws Exception {
        attacker.command("SUPPORT", attackerAt, "{\"SPEARMAN\":101,\"NOBLE\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        attacker.command("ATTACK", new Coordinates(-5, -5), "{\"SPEARMAN\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        // a RETURN can't be sent
        attacker.command("RETURN", defenderAt, "{\"SPEARMAN\":1}")
                .andExpect(status().isBadRequest());

        attacker.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(100));
        attacker.troops().andExpect(jsonPath("$.outgoing").isEmpty());
    }

    @Test
    void onlyOwnVillagesSendTroops() throws Exception {
        defender.browser
                .post(
                        attacker.villagePath() + "/commands",
                        "{\"kind\":\"SUPPORT\",\"target\":{\"x\":"
                                + defenderAt.x()
                                + ",\"y\":"
                                + defenderAt.y()
                                + "},\"units\":{\"SPEARMAN\":1}}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        defender.browser
                .get(attacker.villagePath() + "/troops")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    // ---- attack trains (M37) ----

    @Test
    void aTrainWalksAtItsSlowestUnitAndArrivesTogetherInOrder() throws Exception {
        Instant now = clock.instant();
        long arrival = travelSeconds(18);

        String sent =
                body(
                        attacker.train(
                                        defenderAt,
                                        "{\"SPEARMAN\":60}",
                                        "{\"LIGHT_CAVALRY\":10}",
                                        "{\"AXEMAN\":5}")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$", hasSize(3))));

        // the light cavalry (10 min/field) walks with the spearmen (18)
        for (int i = 0; i < 3; i++) {
            assertThat(JsonPath.<String>read(sent, "$[" + i + "].kind")).isEqualTo("ATTACK");
            assertThat(instant(sent, "$[" + i + "].departAt")).isEqualTo(now);
            assertThat(instant(sent, "$[" + i + "].arriveAt")).isEqualTo(now.plusSeconds(arrival));
        }
        List<Number> ids = JsonPath.read(sent, "$[*].id");
        assertThat(ids.stream().map(Number::longValue).toList()).isSorted();
        attacker.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(40))
                .andExpect(jsonPath("$.unitsHome.LIGHT_CAVALRY").doesNotExist())
                .andExpect(jsonPath("$.unitsHome.AXEMAN").value(45));
        // the defender sees three attacks, in the order they arrive
        String incoming = body(defender.troops());
        assertThat(JsonPath.<List<Number>>read(incoming, "$.incoming[*].id"))
                .isEqualTo(ids.stream().map(Number::intValue).toList());
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(3));
        // each one is an attack of its own: it can be cancelled alone
        attacker.cancelMovement(ids.get(1).longValue()).andExpect(status().isOk());
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(2));
    }

    @Test
    void theTrainPreviewChecksEachAttackAgainstWhatTheOnesBeforeLeaveAtHome() throws Exception {
        Instant now = clock.instant();

        String preview =
                body(
                        attacker.previewTrain(
                                        defenderAt,
                                        "{\"AXEMAN\":40}",
                                        "{\"AXEMAN\":20}",
                                        "{}",
                                        "{\"AXEMAN\":10,\"LIGHT_CAVALRY\":11}")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.targetVillage.id").value(defender.villageId))
                                .andExpect(jsonPath("$.travelSeconds").value(travelSeconds(18)))
                                .andExpect(jsonPath("$.attacks", hasSize(4)))
                                .andExpect(jsonPath("$.attacks[0].errors").isEmpty())
                                // 40 of the 50 axemen are taken by the first one
                                .andExpect(
                                        jsonPath(
                                                "$.attacks[1].errors",
                                                contains("NOT_ENOUGH_UNITS")))
                                .andExpect(
                                        jsonPath("$.attacks[2].errors", contains("EMPTY_COMMAND")))
                                // the refused second one takes nothing: these 10 axemen fit, 11
                                // cavalry not
                                .andExpect(
                                        jsonPath(
                                                "$.attacks[3].errors",
                                                contains("NOT_ENOUGH_UNITS"))));
        // refused attacks count for the pace as well: what the train would walk once fixed
        assertThat(instant(preview, "$.arriveAt")).isEqualTo(now.plusSeconds(travelSeconds(18)));

        attacker.previewTrain(defenderAt, "{}")
                .andExpect(jsonPath("$.travelSeconds").value(nullValue()))
                .andExpect(jsonPath("$.arriveAt").value(nullValue()));
        attacker.previewTrain(new Coordinates(-5, -5), "{\"AXEMAN\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        attacker.troops().andExpect(jsonPath("$.outgoing").isEmpty());
    }

    @Test
    void aTrainIsSentWholeOrNotAtAll() throws Exception {
        attacker.train(defenderAt, "{\"AXEMAN\":40}", "{\"AXEMAN\":20}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        attacker.train(attackerAt, "{\"AXEMAN\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_VILLAGE"));
        // no attacks, and more than five
        attacker.train(defenderAt).andExpect(status().isBadRequest());
        String one = "{\"AXEMAN\":1}";
        attacker.train(defenderAt, one, one, one, one, one, one).andExpect(status().isBadRequest());

        attacker.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(50));
        attacker.troops().andExpect(jsonPath("$.outgoing").isEmpty());

        attacker.train(defenderAt, one, one, one, one, one).andExpect(status().isCreated());
        attacker.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(45));
    }

    @Test
    void aTrainOnAProtectedPlayerIsRefusedAndOneOnAPlayerEndsTheSendersProtection()
            throws Exception {
        Instant now = clock.instant();
        setProtectedUntil(defender, now.plus(Duration.ofDays(365)));
        attacker.train(defenderAt, "{\"AXEMAN\":1}", "{\"AXEMAN\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_TARGET"));

        setProtectedUntil(defender, now.minusSeconds(1));
        setProtectedUntil(attacker, now.plus(Duration.ofDays(1)));
        attacker.train(defenderAt, "{\"AXEMAN\":1}", "{\"AXEMAN\":1}")
                .andExpect(status().isCreated());
        assertThat(protectedUntil(attacker)).isCloseTo(now, within(1, ChronoUnit.MILLIS));
    }

    // ---- beginner protection ----

    @Test
    void aProtectedTargetCanBeSupportedButNotAttacked() throws Exception {
        // far beyond any arrival: the test villages can lie more than a day apart for axemen
        setProtectedUntil(defender, clock.instant().plus(Duration.ofDays(365)));

        attacker.command("ATTACK", defenderAt, "{\"AXEMAN\":10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_TARGET"));
        attacker.previewCommand("ATTACK", defenderAt, "{\"AXEMAN\":10}")
                .andExpect(jsonPath("$.errors", contains("PROTECTED_TARGET")));
        attacker.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(50));

        attacker.command("SUPPORT", defenderAt, "{\"AXEMAN\":10}").andExpect(status().isCreated());
    }

    @Test
    void attackingEndsTheAttackersOwnProtection() throws Exception {
        Instant now = clock.instant();
        setProtectedUntil(attacker, now.plus(Duration.ofDays(1)));

        // a support doesn't
        sendOk("SUPPORT", defenderAt, "{\"AXEMAN\":1}");
        assertThat(protectedUntil(attacker)).isAfter(now);

        sendOk("ATTACK", defenderAt, "{\"AXEMAN\":10}");
        assertThat(protectedUntil(attacker)).isCloseTo(now, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void attackingABarbarianVillageKeepsTheAttackersProtection() throws Exception {
        Instant now = clock.instant();
        Instant protectedUntil = now.plus(Duration.ofDays(1));
        setProtectedUntil(attacker, protectedUntil);
        jdbc.sql("update village set player_id = null where id = ?")
                .param(defender.villageId)
                .update();

        sendOk("ATTACK", defenderAt, "{\"AXEMAN\":10}");

        assertThat(protectedUntil(attacker))
                .isCloseTo(protectedUntil, within(1, ChronoUnit.MILLIS));
        // the attack itself went out
        attacker.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(40));
    }

    @Test
    void nobleAttacksOnABarbarianVillageStillEndTheAttackersProtection() throws Exception {
        // A conquest would otherwise hand the village to a protected player while attacks sent
        // at it as a barbarian village are on the way (PROTECTED_TARGET is checked at send time).
        Instant now = clock.instant();
        setProtectedUntil(attacker, now.plus(Duration.ofDays(1)));
        setHomeUnits(attacker, "u_noble", 1);
        jdbc.sql("update village set player_id = null where id = ?")
                .param(defender.villageId)
                .update();
        int nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        jdbc.sql("update world_settings set noble_max_distance = 1000").update();
        worldService.reload();
        try {
            sendOk("ATTACK", defenderAt, "{\"AXEMAN\":10,\"NOBLE\":1}");
        } finally {
            jdbc.sql("update world_settings set noble_max_distance = ?")
                    .param(nobleMaxDistanceBefore)
                    .update();
            worldService.reload();
        }

        assertThat(protectedUntil(attacker)).isCloseTo(now, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void attackingAnNpcLordStillEndsTheAttackersProtection() throws Exception {
        Instant now = clock.instant();
        setProtectedUntil(attacker, now.plus(Duration.ofDays(1)));
        jdbc.sql("update player set npc = true where id = ?").param(defender.playerId).update();
        try {
            sendOk("ATTACK", defenderAt, "{\"AXEMAN\":10}");
        } finally {
            // the database is shared: a leftover NPC lord would show up in the NPC tests
            jdbc.sql("update player set npc = false where id = ?")
                    .param(defender.playerId)
                    .update();
        }

        assertThat(protectedUntil(attacker)).isCloseTo(now, within(1, ChronoUnit.MILLIS));
    }

    // ---- arrival ----

    @Test
    void anArrivingSupportJoinsTheStackOfItsHomeInTheTarget() throws Exception {
        int populationBefore = populationUsed(attacker);
        Instant arrival = instant(sendOk("SUPPORT", defenderAt, "{\"SPEARMAN\":10}"), "$.arriveAt");

        clock.set(arrival.minusSeconds(1));
        processor.processDue(clock.instant());
        defender.troops().andExpect(jsonPath("$.supportsHere").isEmpty());

        clock.set(arrival);
        processor.processDue(clock.instant());
        String here = body(defender.troops());
        assertThat(number(here, "$.supportsHere[0].homeVillage.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(number(here, "$.supportsHere[0].units.SPEARMAN").intValue()).isEqualTo(10);
        assertThat(JsonPath.<List<?>>read(here, "$.incoming")).isEmpty();
        long stackId = number(here, "$.supportsHere[0].stackId").longValue();
        // both owners get a report
        for (TestPlayer player : List.of(attacker, defender)) {
            String report = player.newestReport("SUPPORT_ARRIVED");
            assertThat(instant(report, "$.happenedAt")).isEqualTo(arrival);
            assertThat(JsonPath.<String>read(report, "$.payload.type")).isEqualTo("SUPPORT");
            assertThat(number(report, "$.payload.from.id").longValue())
                    .isEqualTo(attacker.villageId);
            assertThat(number(report, "$.payload.to.id").longValue()).isEqualTo(defender.villageId);
            assertThat(JsonPath.<Map<String, Integer>>read(report, "$.payload.units"))
                    .isEqualTo(Map.of("SPEARMAN", 10));
        }
        attacker.troops()
                .andExpect(jsonPath("$.outgoing").isEmpty())
                .andExpect(jsonPath("$.ownUnitsElsewhere[0].stackId").value(stackId))
                .andExpect(
                        jsonPath("$.ownUnitsElsewhere[0].locationVillage.id")
                                .value(defender.villageId));

        // a second support joins the same stack
        Instant second =
                instant(
                        sendOk("SUPPORT", defenderAt, "{\"SPEARMAN\":5,\"AXEMAN\":3}"),
                        "$.arriveAt");
        clock.set(second);
        processor.processDue(clock.instant());
        defender.troops()
                .andExpect(jsonPath("$.supportsHere", hasSize(1)))
                .andExpect(jsonPath("$.supportsHere[0].stackId").value(stackId))
                .andExpect(jsonPath("$.supportsHere[0].units.SPEARMAN").value(15))
                .andExpect(jsonPath("$.supportsHere[0].units.AXEMAN").value(3));
        attacker.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(85))
                .andExpect(jsonPath("$.population.used").value(populationBefore));
    }

    /** The battle itself is BattleFlowTest's; here only the way there and back. */
    @Test
    void anAttackFightsOnArrivalAndItsSurvivorsWalkHome() throws Exception {
        setHomeUnits(attacker, "u_catapult", 2);
        Instant now = clock.instant();

        String attack = sendOk("ATTACK", defenderAt, "{\"AXEMAN\":10,\"CATAPULT\":2}");
        assertThat(JsonPath.<String>read(attack, "$.catapultTarget")).isEqualTo("HEADQUARTERS");
        Instant arrival = instant(attack, "$.arriveAt");
        assertThat(arrival).isEqualTo(now.plusSeconds(travelSeconds(30)));
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(1));
        // the same count per village in /api/me (the web's red counter in the top bar)
        defender.browser
                .get("/api/me")
                .andExpect(jsonPath("$.villages[0].incomingAttacks").value(1));
        attacker.browser
                .get("/api/me")
                .andExpect(jsonPath("$.villages[0].incomingAttacks").value(0));
        defender.troops()
                .andExpect(jsonPath("$.incoming[0].kind").value("ATTACK"))
                .andExpect(jsonPath("$.incoming[0].units").value(nullValue()));

        clock.set(arrival);
        processor.processDue(clock.instant());
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(0));
        defender.browser
                .get("/api/me")
                .andExpect(jsonPath("$.villages[0].incomingAttacks").value(0));
        // an empty village: whatever the luck, A ≥ 450 against D = 20 kills nobody
        assertThat(attacker.reportKinds()).containsExactly("ATTACK");
        assertThat(defender.reportKinds()).containsExactly("DEFENSE");
        String back = body(attacker.troops());
        assertThat(JsonPath.<List<?>>read(back, "$.outgoing")).isEmpty();
        assertThat(JsonPath.<String>read(back, "$.incoming[0].kind")).isEqualTo("RETURN");
        assertThat(number(back, "$.incoming[0].sourceVillage.id").longValue())
                .isEqualTo(defender.villageId);
        assertThat(number(back, "$.incoming[0].units.AXEMAN").intValue()).isEqualTo(10);
        Instant returnAt = instant(back, "$.incoming[0].arriveAt");
        assertThat(returnAt).isEqualTo(arrival.plusSeconds(travelSeconds(30)));

        clock.set(returnAt);
        processor.processDue(clock.instant());
        attacker.village()
                .andExpect(jsonPath("$.unitsHome.AXEMAN").value(50))
                .andExpect(jsonPath("$.unitsHome.CATAPULT").value(2));
        attacker.troops().andExpect(jsonPath("$.incoming").isEmpty());
    }

    @Test
    void aReturnJoinsTheHomeStackAndItsLootIsCappedAtTheWarehouse() throws Exception {
        Instant now = clock.instant();
        int capacity = number(body(attacker.village()), "$.storageCapacity").intValue();
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(
                        capacity - 100,
                        capacity - 100,
                        capacity - 100,
                        Timestamp.from(now),
                        attacker.villageId)
                .update();
        // Made by hand, so the loot and the cap are exact.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            Village home = villages.findById(attacker.villageId).orElseThrow();
                            Village from = villages.findById(defender.villageId).orElseThrow();
                            Movement back =
                                    movements.save(
                                            Movement.returning(
                                                    home,
                                                    from,
                                                    attacker.playerId,
                                                    UnitCounts.of(Map.of(UnitType.SPEARMAN, 10)),
                                                    new ResourceAmounts(1000, 50, 0),
                                                    now.minusSeconds(60),
                                                    now,
                                                    now.minusSeconds(60)));
                            scheduler.schedule(GameEventKind.MOVEMENT_ARRIVE, back.getId(), now);
                        });

        assertThat(processor.processDue(now)).isEqualTo(1);

        String village = body(attacker.village());
        assertThat(number(village, "$.unitsHome.SPEARMAN").intValue()).isEqualTo(110);
        assertThat(number(village, "$.resources.wood").doubleValue())
                .isCloseTo(capacity, within(EPSILON));
        assertThat(number(village, "$.resources.clay").doubleValue())
                .isCloseTo(capacity - 50, within(EPSILON));
        assertThat(number(village, "$.resources.iron").doubleValue())
                .isCloseTo(capacity - 100, within(EPSILON));
        attacker.troops().andExpect(jsonPath("$.incoming").isEmpty());
    }

    // ---- cancel ----

    @Test
    void aCancelInsideTheWindowReturnsAfterTheTimeItWasOnTheWay() throws Exception {
        String attack = sendOk("ATTACK", defenderAt, "{\"SPEARMAN\":10}");
        long id = id(attack);
        Instant depart = instant(attack, "$.departAt");
        Instant originalArrival = instant(attack, "$.arriveAt");

        clock.set(depart.plusSeconds(100));
        String cancelled = body(attacker.cancelMovement(id).andExpect(status().isOk()));

        assertThat(JsonPath.<String>read(cancelled, "$.kind")).isEqualTo("RETURN");
        assertThat(instant(cancelled, "$.departAt")).isEqualTo(depart.plusSeconds(100));
        assertThat(instant(cancelled, "$.arriveAt")).isEqualTo(depart.plusSeconds(200));
        assertThat(number(cancelled, "$.sourceVillage.id").longValue())
                .isEqualTo(defender.villageId);
        assertThat(number(cancelled, "$.targetVillage.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(JsonPath.<Object>read(cancelled, "$.cancellableUntil")).isNull();
        defender.village().andExpect(jsonPath("$.incomingAttacks").value(0));
        attacker.troops()
                .andExpect(jsonPath("$.outgoing").isEmpty())
                .andExpect(jsonPath("$.incoming[0].id").value(id));

        clock.set(depart.plusSeconds(199));
        assertThat(processor.processDue(clock.instant())).isZero();
        attacker.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(90));

        clock.set(depart.plusSeconds(200));
        assertThat(processor.processDue(clock.instant())).isEqualTo(1);
        attacker.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(100));

        // the event moved: nothing happens at the old arrival time
        clock.set(originalArrival);
        assertThat(processor.processDue(clock.instant())).isZero();
    }

    @Test
    void aCancelAfterTheWindowOrOfAReturnOrOfSomeoneElseIsRefused() throws Exception {
        String attack = sendOk("ATTACK", defenderAt, "{\"SPEARMAN\":10}");
        long id = id(attack);
        Instant depart = instant(attack, "$.departAt");

        defender.cancelMovement(id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVEMENT_NOT_FOUND"));
        attacker.cancelMovement(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVEMENT_NOT_FOUND"));

        clock.set(depart.plusSeconds(600));
        attacker.cancelMovement(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_OVER"));

        long support = id(sendOk("SUPPORT", defenderAt, "{\"SPEARMAN\":1}"));
        clock.advance(Duration.ofSeconds(10));
        attacker.cancelMovement(support).andExpect(status().isOk());
        attacker.cancelMovement(support)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_OVER"));
    }

    // ---- withdraw and send back ----

    @Test
    void aPartialWithdrawWalksHomeAndJoinsTheHomeStack() throws Exception {
        long stackId = arrivedSupport("{\"SPEARMAN\":10,\"LIGHT_CAVALRY\":5}");
        Instant now = clock.instant();

        String back =
                body(
                        attacker.returnStack(stackId, "{\"SPEARMAN\":4}")
                                .andExpect(status().isCreated()));

        assertThat(JsonPath.<String>read(back, "$.kind")).isEqualTo("RETURN");
        assertThat(number(back, "$.sourceVillage.id").longValue()).isEqualTo(defender.villageId);
        assertThat(number(back, "$.homeVillage.id").longValue()).isEqualTo(attacker.villageId);
        Instant arrival = instant(back, "$.arriveAt");
        assertThat(arrival).isEqualTo(now.plusSeconds(travelSeconds(18)));
        defender.troops()
                .andExpect(jsonPath("$.supportsHere[0].units.SPEARMAN").value(6))
                .andExpect(jsonPath("$.supportsHere[0].units.LIGHT_CAVALRY").value(5));
        attacker.troops()
                .andExpect(jsonPath("$.incoming[0].kind").value("RETURN"))
                .andExpect(jsonPath("$.incoming[0].units.SPEARMAN").value(4));
        // the supported player learns that units left; the one who withdrew them doesn't need to
        String report = defender.newestReport("SUPPORT_RETURNED");
        assertThat(instant(report, "$.happenedAt")).isEqualTo(now);
        assertThat(number(report, "$.payload.from.id").longValue()).isEqualTo(attacker.villageId);
        assertThat(JsonPath.<Map<String, Integer>>read(report, "$.payload.units"))
                .isEqualTo(Map.of("SPEARMAN", 4));
        assertThat(attacker.reportKinds()).doesNotContain("SUPPORT_RETURNED");

        clock.set(arrival);
        processor.processDue(clock.instant());
        attacker.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(94));

        // the rest: the stack is gone
        attacker.returnStack(stackId, "{\"SPEARMAN\":6,\"LIGHT_CAVALRY\":5}")
                .andExpect(status().isCreated());
        defender.troops().andExpect(jsonPath("$.supportsHere").isEmpty());
        attacker.troops().andExpect(jsonPath("$.ownUnitsElsewhere").isEmpty());
    }

    @Test
    void theLocationOwnerCanSendASupportBackButNobodyElse() throws Exception {
        long stackId = arrivedSupport("{\"SPEARMAN\":10}");
        TestPlayer stranger = TestPlayer.register(mockMvc);

        stranger.returnStack(stackId, "{\"SPEARMAN\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STACK_NOT_FOUND"));
        // the home stack is no support
        attacker.returnStack(homeStackId(attacker), "{\"SPEARMAN\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STACK_NOT_FOUND"));
        defender.returnStack(stackId, "{\"SPEARMAN\":11}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        defender.returnStack(stackId, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_COMMAND"));

        defender.returnStack(stackId, "{\"SPEARMAN\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.homeVillage.id").value(attacker.villageId));
        defender.troops().andExpect(jsonPath("$.supportsHere").isEmpty());
        attacker.troops().andExpect(jsonPath("$.incoming[0].units.SPEARMAN").value(10));
        // the owner of the units learns it, the one who sent them back doesn't need to
        String report = attacker.newestReport("SUPPORT_RETURNED");
        assertThat(number(report, "$.payload.to.id").longValue()).isEqualTo(defender.villageId);
        assertThat(JsonPath.<Map<String, Integer>>read(report, "$.payload.units"))
                .isEqualTo(Map.of("SPEARMAN", 10));
        assertThat(defender.reportKinds()).doesNotContain("SUPPORT_RETURNED");
        assertThat(stranger.reportKinds()).isEmpty();
    }

    // ---- helpers ----

    private String sendOk(String kind, Coordinates target, String unitsJson) throws Exception {
        return body(attacker.command(kind, target, unitsJson).andExpect(status().isCreated()));
    }

    /** Sends a support from the attacker to the defender, lets it arrive; returns the stack id. */
    private long arrivedSupport(String unitsJson) throws Exception {
        Instant arrival = instant(sendOk("SUPPORT", defenderAt, unitsJson), "$.arriveAt");
        clock.set(arrival);
        processor.processDue(clock.instant());
        return number(body(defender.troops()), "$.supportsHere[0].stackId").longValue();
    }

    /** Attacker to defender with this army speed, in the test database's world. */
    private long travelSeconds(double minutesPerField) {
        return Travel.travelSeconds(
                Travel.distance(attackerAt, defenderAt), minutesPerField, worldService.settings());
    }

    private int populationUsed(TestPlayer player) throws Exception {
        return number(body(player.village()), "$.population.used").intValue();
    }

    /** The column name is a constant of this test, never user input. */
    private void setHomeUnits(TestPlayer player, String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    private long homeStackId(TestPlayer player) {
        return jdbc.sql(
                        "select id from unit_stack"
                                + " where home_village_id = ? and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .query(Long.class)
                .single();
    }

    private void setProtectedUntil(TestPlayer player, Instant until) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(until), player.playerId)
                .update();
    }

    private Instant protectedUntil(TestPlayer player) {
        return jdbc.sql("select protected_until from player where id = ?")
                .params(player.playerId)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    private static long id(String json) {
        return number(json, "$.id").longValue();
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
