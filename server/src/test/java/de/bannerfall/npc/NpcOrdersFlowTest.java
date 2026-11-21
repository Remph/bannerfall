package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.victory.FrozenTime;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders to allied lords in a solo world (2.1 S3, 12-solo-and-allies §8) end to end: the order
 * sheet and the order through the API, the lord's answer by letter in its next round, the movement
 * it sends, "no" below a trust of 20, a lost estimate, the caps, "land together" with its planned
 * march, a support that stays, resources above the lord's next goal, hold. The scene lies far off
 * the map (x ≥ 2000) like {@link NpcAlliesFlowTest}'s and is removed afterwards.
 */
@IntegrationTest
class NpcOrdersFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AtomicInteger AREA = new AtomicInteger();

    /** A few defenders seen: an attack wins easily, but it is no raid target. */
    private static final Map<UnitType, Integer> FEW = Map.of(UnitType.SPEARMAN, 5);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired GuildService guildService;
    @Autowired NpcBrainRepository brains;
    @Autowired CommandService commands;
    @Autowired FrozenTime frozenTime;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NpcOrderStore store;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2000;
    private final List<long[]> movedHumans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        """
                        update world_settings set barbarians_per_new_player = 0,
                            npc_difficulty = 'NORMAL', speed = 1.0, solo_mode = true,
                            guilds_enabled = true, npc_guilds_enabled = true,
                            guild_max_members = 5, court_guild_id = null,
                            night_bonus_enabled = false
                        """)
                .update();
        worldService.reload();
        guildService.setInviteInterval(Duration.ZERO);
        clock.set(
                LocalDate.now(clock).plusDays(1).atTime(LocalTime.NOON).atZone(BERLIN).toInstant());
        baseX = 2000 + 200 * AREA.getAndIncrement();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from npc_order").update();
        String scene = "select id from village where x >= 2000";
        jdbc.sql(
                        ("delete from trade_transport where source_village_id in (%1$s)"
                                        + " or target_village_id in (%1$s)")
                                .formatted(scene))
                .update();
        jdbc.sql(
                        ("delete from movement where home_village_id in (%1$s)"
                                        + " or source_village_id in (%1$s)"
                                        + " or target_village_id in (%1$s)")
                                .formatted(scene))
                .update();
        jdbc.sql(
                        ("delete from unit_stack where location_village_id in (%1$s)"
                                        + " and home_village_id <> location_village_id")
                                .formatted(scene))
                .update();
        for (long[] moved : movedHumans) {
            jdbc.sql("update village set x = ?, y = ? where id = ?")
                    .params(moved[1], moved[2], moved[0])
                    .update();
        }
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from unit_stack where home_village_id in (%1$s) or location_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from movement where owner_player_id in (select id from player where npc)")
                .update();
        jdbc.sql(
                        "delete from trade_transport where source_village_id in (%s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from report where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from guild").update();
        jdbc.sql("delete from player where npc").update();
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        """
                        update world_settings set npc_lord_count = 0, speed = 1.0,
                            barbarian_initial_count = 30, barbarians_per_new_player = 0,
                            npc_guilds_enabled = false, guilds_enabled = false,
                            solo_mode = false, court_guild_id = null, guild_max_members = 10,
                            victory_leader = null, victory_leader_since = null,
                            night_bonus_enabled = false
                        """)
                .update();
        guildService.setInviteInterval(Duration.ofMinutes(1));
        worldService.reload();
    }

    // ---- the sheet and the API ----

    @Test
    void theSheetListsTheAlliesWithTheirReachAndTheKindsTheTargetTakes() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(20, 0);
        guildOf(human);
        ally(lords.get(0), human, 64);
        units(villageOf(lords.get(0)), Map.of(UnitType.AXEMAN, 100, UnitType.SPEARMAN, 50));

        human.browser
                .get("/api/diplomacy/orders/" + enemy.villageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.id").value(enemy.villageId))
                .andExpect(jsonPath("$.kinds[0]").value("ATTACK"))
                .andExpect(jsonPath("$.kinds[1]").value("HOLD"))
                .andExpect(jsonPath("$.ownAttackArrivesAt").value(nullValue()))
                .andExpect(jsonPath("$.allies.length()").value(1))
                .andExpect(jsonPath("$.allies[0].playerId").value(lords.get(0)))
                .andExpect(jsonPath("$.allies[0].trust").value(64))
                .andExpect(jsonPath("$.allies[0].fields").value(20.0))
                .andExpect(jsonPath("$.allies[0].attackSeconds").isNumber())
                .andExpect(jsonPath("$.allies[0].supportSeconds").isNumber())
                .andExpect(jsonPath("$.allies[0].order").value(nullValue()));
        // the human's own village: support and resources
        human.browser
                .get("/api/diplomacy/orders/" + human.villageId)
                .andExpect(jsonPath("$.kinds[0]").value("SUPPORT"))
                .andExpect(jsonPath("$.kinds[1]").value("SEND"));
        // an ally's village: support only, and that ally isn't on its own sheet
        human.browser
                .get("/api/diplomacy/orders/" + villageOf(lords.get(0)))
                .andExpect(jsonPath("$.kinds.length()").value(1))
                .andExpect(jsonPath("$.kinds[0]").value("SUPPORT"))
                .andExpect(jsonPath("$.allies").isEmpty());
    }

    @Test
    void ordersGoOnlyToAlliesAndOnlyWhereTheTargetTakesThem() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(20, 0);
        guildOf(human);
        ally(lords.get(0), human, 60);

        human.browser
                .post("/api/diplomacy/orders", order("ATTACK", enemy.villageId, lords.get(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AN_ALLY"));
        human.browser
                .post("/api/diplomacy/orders", order("ATTACK", human.villageId, lords.get(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_TARGET_INVALID"));
        human.browser
                .post("/api/diplomacy/orders", order("SEND", enemy.villageId, lords.get(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_TARGET_INVALID"));
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        human.browser
                .post("/api/diplomacy/orders", order("ATTACK", enemy.villageId, lords.get(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_SOLO"));
        assertThat(orderState(lords.get(0))).isNull();
    }

    // ---- attack ----

    @Test
    void anAllyAttacksOnOrderInItsNextRoundWithItsShareAndWritesALetter() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        seen(lord, enemy, FEW);

        human.browser
                .post("/api/diplomacy/orders", order("ATTACK", enemy.villageId, lord))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allies[0].order.kind").value("ATTACK"))
                .andExpect(jsonPath("$.allies[0].order.state").value("OPEN"));
        assertThat(attacksFrom(lord)).isEmpty(); // it answers in its round

        round(lord);

        // BALANCED: 60 % of what may leave (500 less the 40 % home guard = 300)
        assertThat(attacksFrom(lord)).containsExactly(enemy.villageId);
        assertThat(sentUnits(lord, UnitType.AXEMAN)).isEqualTo(180);
        assertThat(letters(human, lord)).containsExactly("ORDER_ATTACK");
        assertThat(letterField(human, lord, "units")).contains("\"AXEMAN\": 180");
        assertThat(letterField(human, lord, "arriveAt")).isNotNull();
        human.browser
                .get("/api/diplomacy")
                .andExpect(jsonPath("$.allies[0].order.state").value("ACTIVE"))
                .andExpect(jsonPath("$.allies[0].order.answer").value("ORDER_ATTACK"))
                .andExpect(jsonPath("$.allies[0].order.arriveAt").isString());
        // the order is done: the next round sends nothing more for it
        clock.advance(Duration.ofMinutes(20));
        round(lord);
        assertThat(letters(human, lord)).containsExactly("ORDER_ATTACK");
    }

    @Test
    void belowATrustOf20TheAllySaysNo() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 15);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        seen(lord, enemy, FEW);

        give(human, "ATTACK", enemy.villageId, lord);
        round(lord);

        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(letters(human, lord)).containsExactly("ORDER_REFUSED_TRUST");
        assertThat(orderState(lord)).isEqualTo("REFUSED");
    }

    @Test
    void anAttackItWouldLoseIsANo() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 100));
        seen(lord, enemy, Map.of(UnitType.SPEARMAN, 400));

        give(human, "ATTACK", enemy.villageId, lord);
        round(lord);

        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(letters(human, lord)).containsExactly("ORDER_TOO_STRONG");
    }

    @Test
    void anAllyWontAttackItsOwnGuild() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lords.get(0), human, 60);
        units(villageOf(lords.get(0)), Map.of(UnitType.AXEMAN, 500));
        // the other lord's village is an ATTACK target for the human, but the order is a no once
        // that lord has joined the guild too
        give(human, "ATTACK", villageOf(lords.get(1)), lords.get(0));
        ally(lords.get(1), human, 60);

        round(lords.get(0));

        assertThat(letters(human, lords.get(0))).containsExactly("ORDER_REFUSED_BOUND");
    }

    @Test
    void attackOrdersOnAHumanCountAgainstTheCaps() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        seen(lord, enemy, FEW);
        // NORMAL: a lord sends at most 4 commands a day to one player
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    for (int i = 0; i < 4; i++) {
                        draft.attacksSent.add(
                                new NpcMemory.SentAttack(
                                        enemy.playerId, clock.instant().minusSeconds(60L * i)));
                    }
                    brain.setMemory(draft.build());
                });

        give(human, "ATTACK", enemy.villageId, lord);
        round(lord);

        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(letters(human, lord)).containsExactly("ORDER_CAPS");
    }

    @Test
    void anOrderedAttackEntersTheCaps() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        seen(lord, enemy, FEW);

        give(human, "ATTACK", enemy.villageId, lord);
        round(lord);

        assertThat(
                        brains.findById(lord).orElseThrow().getMemory().attacksSent().stream()
                                .filter(a -> a.targetPlayerId() == enemy.playerId)
                                .count())
                .isEqualTo(1);
    }

    @Test
    void anUnseenTargetIsScoutedFirstAndWithoutScoutsTheAllyCantSeeIt() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lords.get(0), human, 60);
        ally(lords.get(1), human, 60);
        units(villageOf(lords.get(0)), Map.of(UnitType.AXEMAN, 500, UnitType.SCOUT, 10));
        units(villageOf(lords.get(1)), Map.of(UnitType.AXEMAN, 500));

        give(human, "ATTACK", enemy.villageId, lords.get(0), lords.get(1));
        round(lords.get(0));
        round(lords.get(1));

        assertThat(orderState(lords.get(0))).isEqualTo("SCOUTING");
        assertThat(sentUnits(lords.get(0), UnitType.SCOUT)).isEqualTo(5);
        assertThat(letters(human, lords.get(0))).isEmpty();
        assertThat(letters(human, lords.get(1))).containsExactly("ORDER_UNSEEN");
    }

    @Test
    void aNewOrderReplacesTheOpenOne() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        seen(lord, enemy, Map.of());

        give(human, "ATTACK", enemy.villageId, lord);
        give(human, "HOLD", enemy.villageId, lord);
        assertThat(jdbc.sql("select count(*) from npc_order").query(Integer.class).single())
                .isEqualTo(1);
        round(lord);

        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(letters(human, lord)).containsExactly("ORDER_HOLD");
        assertThat(orderState(lord)).isEqualTo("ACTIVE");
        // taken back
        human.browser.delete("/api/diplomacy/orders/" + lord).andExpect(status().isOk());
        assertThat(orderState(lord)).isNull();
    }

    @Test
    void theLordsAnswerNeverOverwritesAnOrderTheHumanChangedMeanwhile() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);

        give(human, "ATTACK", enemy.villageId, lord);
        NpcOrderStore.Order seen = store.find(lord).orElseThrow(); // what the lord's round read
        assertThat(unchanged(seen)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        give(human, "HOLD", enemy.villageId, lord); // replaced while the lord weighs the attack
        assertThat(unchanged(seen)).isFalse();

        NpcOrderStore.Order hold = store.find(lord).orElseThrow();
        human.browser.delete("/api/diplomacy/orders/" + lord).andExpect(status().isOk());
        assertThat(unchanged(hold)).isFalse();
    }

    @Test
    void aLordThatHoldsOffAVillageDoesntFarmIt() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        TestPlayer farm = human(3, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.LIGHT_CAVALRY, 200));
        seen(lord, farm, Map.of());

        give(human, "HOLD", farm.villageId, lord);
        round(lord);
        assertThat(attacksFrom(lord)).doesNotContain(farm.villageId);

        // without the hold the same round raids it (fresh intel, no defenders)
        human.browser.delete("/api/diplomacy/orders/" + lord).andExpect(status().isOk());
        seen(lord, farm, Map.of());
        round(lord);
        assertThat(attacksFrom(lord)).contains(farm.villageId);
    }

    // ---- land together ----

    @Test
    void landTogetherPlansTheMarchToLandRightAfterTheHumansAttack() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(-30, 0); // far: his attack walks long
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        units(human.villageId, Map.of(UnitType.AXEMAN, 50));
        seen(lord, enemy, FEW);
        Instant own = humanAttack(human, enemy);

        human.browser
                .get("/api/diplomacy/orders/" + enemy.villageId)
                .andExpect(jsonPath("$.ownAttackArrivesAt").isString());
        give(human, "ATTACK", enemy.villageId, true, lord);
        round(lord);

        assertThat(orderState(lord)).isEqualTo("PLANNED");
        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(letters(human, lord)).containsExactly("ORDER_ATTACK_PLANNED");
        Instant depart = departAt(lord);
        assertThat(depart).isAfter(clock.instant());
        assertThat(pending(GameEventKind.NPC_ORDER_MARCH, lord)).isEqualTo(depart);
        // its village keeps the units for the march: the next round doesn't send them
        clock.advance(Duration.ofMinutes(20));
        round(lord);
        assertThat(attacksFrom(lord)).isEmpty();

        clock.set(depart);
        processor.processDue(clock.instant());

        assertThat(orderState(lord)).isEqualTo("ACTIVE");
        Instant arrival = arrivalOfAttack(lord);
        assertThat(arrival).isAfter(own).isBeforeOrEqualTo(own.plusSeconds(60));
        assertThat(sentUnits(lord, UnitType.AXEMAN)).isEqualTo(340); // 0.8 × (500 − 15 %)
        assertThat(failedEvents()).isZero();
    }

    @Test
    void landTogetherTooLateOrWithoutAnAttackOfHisIsANo() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.OFFENSIVE);
        TestPlayer human = human(9, 0); // next to the target: his attack lands soon
        TestPlayer enemy = human(10, 0);
        TestPlayer other = human(-10, 0);
        guildOf(human);
        ally(lords.get(0), human, 60);
        ally(lords.get(1), human, 60);
        units(villageOf(lords.get(0)), Map.of(UnitType.AXEMAN, 500));
        units(villageOf(lords.get(1)), Map.of(UnitType.AXEMAN, 500));
        units(human.villageId, Map.of(UnitType.AXEMAN, 50));
        seen(lords.get(0), enemy, FEW);
        seen(lords.get(1), other, FEW);
        humanAttack(human, enemy);

        give(human, "ATTACK", enemy.villageId, true, lords.get(0));
        give(human, "ATTACK", other.villageId, true, lords.get(1));
        round(lords.get(0));
        round(lords.get(1));

        assertThat(letters(human, lords.get(0))).containsExactly("ORDER_NOT_IN_TIME");
        assertThat(letters(human, lords.get(1))).containsExactly("ORDER_NOT_IN_TIME");
        assertThat(attacksFrom(lords.get(0))).isEmpty();
    }

    @Test
    void aPlannedMarchWhoseUnitsAreGoneDoesntLeave() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(-30, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        units(human.villageId, Map.of(UnitType.AXEMAN, 50));
        seen(lord, enemy, FEW);
        humanAttack(human, enemy);
        give(human, "ATTACK", enemy.villageId, true, lord);
        round(lord);
        Instant depart = departAt(lord);

        units(villageOf(lord), Map.of(UnitType.AXEMAN, 100)); // lost in a battle meanwhile
        clock.set(depart);
        processor.processDue(clock.instant());

        assertThat(attacksFrom(lord)).isEmpty();
        assertThat(orderState(lord)).isEqualTo("REFUSED");
        assertThat(letters(human, lord))
                .containsExactly("ORDER_ATTACK_PLANNED", "ORDER_TOO_STRONG");
        assertThat(failedEvents()).isZero();
    }

    // ---- support ----

    @Test
    void anOrderedSupportTakesTheDoubledShareAndStaysWhileItIsTheOrder() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 500));

        give(human, "SUPPORT", human.villageId, lord);
        round(lord);

        // BALANCED: support share 0.25 doubled = 0.5 of what may leave (500 less 40 %)
        assertThat(letters(human, lord)).containsExactly("ORDER_SUPPORT");
        assertThat(supportsFrom(lord)).containsExactly(human.villageId);
        assertThat(sentSupport(lord, UnitType.SPEARMAN)).isEqualTo(150);

        clock.advance(Duration.ofHours(2));
        processor.processDue(clock.instant());
        assertThat(standing(villageOf(lord), human.villageId)).isEqualTo(150);
        // no attack is due there, but it stays: it is the order
        round(lord);
        assertThat(standing(villageOf(lord), human.villageId)).isEqualTo(150);
        // taken back: the lord brings it home in its next round
        human.browser.delete("/api/diplomacy/orders/" + lord).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(20));
        round(lord);
        assertThat(standing(villageOf(lord), human.villageId)).isZero();
    }

    @Test
    void anOffensiveAllySendsAFifthOnASupportOrder() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.SPEARMAN, 200));

        give(human, "SUPPORT", human.villageId, lord);
        round(lord);

        // its own share is 0; the floor of 0.2 (decided in S3) of 200 less 15 % = 34
        assertThat(sentSupport(lord, UnitType.SPEARMAN)).isEqualTo(34);
    }

    @Test
    void anAllyWithoutDefendersSaysItHasNoneToSpare() throws Exception {
        long lord = lord(NpcPersonality.DEFENSIVE);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.LIGHT_CAVALRY, 50));

        give(human, "SUPPORT", human.villageId, lord);
        round(lord);

        assertThat(letters(human, lord)).containsExactly("ORDER_NO_TROOPS");
    }

    // ---- resources ----

    @Test
    void anAllySendsOnlyWhatItHoldsAboveItsNextGoal() throws Exception {
        List<Long> lords = lords(2, NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lords.get(0), human, 60);
        ally(lords.get(1), human, 60);
        rich(villageOf(lords.get(0)), 20_000);
        rich(villageOf(lords.get(1)), 300);
        rich(human.villageId, 0); // room in his warehouse

        give(human, "SEND", human.villageId, lords.get(0), lords.get(1));
        round(lords.get(0));
        round(lords.get(1));

        assertThat(letters(human, lords.get(0))).containsExactly("ORDER_SEND");
        assertThat(letterField(human, lords.get(0), "resources")).isNotNull();
        assertThat(
                        jdbc.sql("select count(*) from trade_transport where source_village_id = ?")
                                .param(villageOf(lords.get(0)))
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        assertThat(letters(human, lords.get(1))).containsExactly("ORDER_NO_SURPLUS");
    }

    @Test
    void anAllySaysSoWhenTheHumansWarehouseIsFull() throws Exception {
        long lord = lord(NpcPersonality.BALANCED);
        TestPlayer human = human(5, 0);
        guildOf(human);
        ally(lord, human, 60);
        rich(villageOf(lord), 20_000);
        rich(human.villageId, 1_000_000); // capped at his warehouse: full

        give(human, "SEND", human.villageId, lord);
        round(lord);

        assertThat(letters(human, lord)).containsExactly("ORDER_NO_ROOM");
        assertThat(
                        jdbc.sql("select count(*) from trade_transport where source_village_id = ?")
                                .param(villageOf(lord))
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    void theWorldsClockMovesAPlannedMarch() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(-30, 0);
        TestPlayer enemy = human(10, 0);
        guildOf(human);
        ally(lord, human, 60);
        units(villageOf(lord), Map.of(UnitType.AXEMAN, 500));
        units(human.villageId, Map.of(UnitType.AXEMAN, 50));
        seen(lord, enemy, FEW);
        humanAttack(human, enemy);
        give(human, "ATTACK", enemy.villageId, true, lord);
        round(lord);
        Instant depart = departAt(lord);

        transaction.executeWithoutResult(
                status -> frozenTime.shift(Duration.ofHours(1), clock.instant()));

        assertThat(departAt(lord)).isEqualTo(depart.plus(Duration.ofHours(1)));
        assertThat(pending(GameEventKind.NPC_ORDER_MARCH, lord))
                .isEqualTo(depart.plus(Duration.ofHours(1)));
    }

    // ---- helpers ----

    private String order(String kind, long villageId, long... lords) {
        return order(kind, villageId, false, lords);
    }

    private String order(String kind, long villageId, boolean landTogether, long... lords) {
        StringBuilder ids = new StringBuilder();
        for (long lord : lords) {
            ids.append(ids.isEmpty() ? "" : ",").append(lord);
        }
        return "{\"kind\":\"%s\",\"villageId\":%d,\"lordIds\":[%s],\"landTogether\":%s}"
                .formatted(kind, villageId, ids, landTogether);
    }

    /** Whether the lord's order is still {@code seen}, as the lord's answer checks it. */
    private boolean unchanged(NpcOrderStore.Order seen) {
        return Boolean.TRUE.equals(transaction.execute(status -> store.lockUnchanged(seen)));
    }

    private void give(TestPlayer human, String kind, long villageId, long... lords)
            throws Exception {
        give(human, kind, villageId, false, lords);
    }

    private void give(
            TestPlayer human, String kind, long villageId, boolean landTogether, long... lords)
            throws Exception {
        human.browser
                .post("/api/diplomacy/orders", order(kind, villageId, landTogether, lords))
                .andExpect(status().isOk());
    }

    /** The human's attack on the target, sent now; its arrival. */
    private Instant humanAttack(TestPlayer human, TestPlayer target) {
        Village to = villages.findById(target.villageId).orElseThrow();
        return transaction.execute(
                status ->
                        commands.send(
                                        human.villageId,
                                        human.playerId,
                                        new CommandRequest(
                                                CommandKind.ATTACK,
                                                new Coordinates(to.getX(), to.getY()),
                                                Map.of(UnitType.AXEMAN, 50),
                                                null))
                                .arriveAt());
    }

    /** The lord saw the target a minute ago with these defenders, wall 0. */
    private void seen(long lord, TestPlayer target, Map<UnitType, Integer> defenders) {
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    draft.intel.put(
                            target.villageId,
                            new NpcMemory.Intel(
                                    clock.instant().minusSeconds(60),
                                    target.playerId,
                                    defenders,
                                    0,
                                    Map.of(),
                                    null,
                                    null,
                                    false,
                                    null,
                                    0,
                                    null,
                                    null));
                    brain.setMemory(draft.build());
                });
    }

    private String orderState(long lord) {
        return jdbc.sql("select state from npc_order where lord_id = ?")
                .param(lord)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private Instant departAt(long lord) {
        return jdbc.sql("select depart_at from npc_order where lord_id = ?")
                .param(lord)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private Instant pending(GameEventKind kind, long refId) {
        return jdbc.sql(
                        "select due_at from game_event where kind = ? and ref_id = ? and status ="
                                + " 'PENDING'")
                .params(kind.name(), refId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    private int failedEvents() {
        return jdbc.sql("select count(*) from game_event where status = 'FAILED'")
                .query(Integer.class)
                .single();
    }

    private List<Long> attacksFrom(long lord) {
        return jdbc.sql(
                        "select target_village_id from movement where owner_player_id = ? and kind"
                                + " = 'ATTACK' order by id")
                .param(lord)
                .query(Long.class)
                .list();
    }

    private List<Long> supportsFrom(long lord) {
        return jdbc.sql(
                        "select target_village_id from movement where owner_player_id = ? and kind"
                                + " = 'SUPPORT' order by id")
                .param(lord)
                .query(Long.class)
                .list();
    }

    private Instant arrivalOfAttack(long lord) {
        return jdbc.sql(
                        "select arrive_at from movement where owner_player_id = ? and kind ="
                                + " 'ATTACK'")
                .param(lord)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private int sentUnits(long lord, UnitType type) {
        return sent(lord, type, "ATTACK");
    }

    private int sentSupport(long lord, UnitType type) {
        return sent(lord, type, "SUPPORT");
    }

    private int sent(long lord, UnitType type, String kind) {
        return jdbc.sql(
                        "select coalesce(sum(u_%s), 0) from movement where owner_player_id = ? and"
                                        .formatted(type.name().toLowerCase(Locale.ROOT))
                                + " kind = ?")
                .params(lord, kind)
                .query(Integer.class)
                .single();
    }

    private int standing(long home, long location) {
        return jdbc.sql(
                        "select coalesce(sum(u_spearman), 0) from unit_stack where"
                                + " home_village_id = ? and location_village_id = ?")
                .params(home, location)
                .query(Integer.class)
                .single();
    }

    /** The kinds of the lord's letters to the human, oldest first. */
    private List<String> letters(TestPlayer human, long lord) {
        return jdbc.sql(
                        """
                        select letter ->> 'kind' from private_message
                        where sender_id = ? and recipient_id = ? and letter is not null
                        order by id
                        """)
                .params(lord, human.playerId)
                .query(String.class)
                .list();
    }

    /** One field of the lord's last letter to the human, as JSON text. */
    private String letterField(TestPlayer human, long lord, String field) {
        return jdbc.sql(
                        """
                        select (letter -> ?)::text from private_message
                        where sender_id = ? and recipient_id = ? and letter is not null
                        order by id desc limit 1
                        """)
                .params(field, lord, human.playerId)
                .query(String.class)
                .optional()
                .filter(json -> !"null".equals(json))
                .orElse(null);
    }

    /** Puts the lord into the human's guild with this trust in him. */
    private void ally(long lord, TestPlayer human, double trustValue) {
        setTrust(lord, human.playerId, 100);
        long invitation = guildService.inviteLord(human.playerId, lord);
        assertThat(guildService.acceptForLord(lord, invitation)).isTrue();
        setTrust(lord, human.playerId, trustValue);
    }

    private long guildOf(TestPlayer human) throws Exception {
        Long id = guildIdOf(human.playerId);
        if (id != null) {
            return id;
        }
        human.browser
                .post("/api/guilds", "{\"name\":\"Testers\",\"tag\":\"TST\"}")
                .andExpect(status().isCreated());
        return guildIdOf(human.playerId);
    }

    private Long guildIdOf(long playerId) {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private void setTrust(long lord, long player, double value) {
        jdbc.sql(
                        """
                        insert into npc_trust (lord_id, player_id, trust, trust_at)
                        values (?, ?, ?, ?)
                        on conflict (lord_id, player_id) do update
                            set trust = excluded.trust, trust_at = excluded.trust_at
                        """)
                .params(lord, player, value, Timestamp.from(clock.instant()))
                .update();
    }

    private void round(long lord) {
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lord, clock.instant()));
        processor.processDue(clock.instant());
    }

    private long lord(NpcPersonality personality) {
        return lords(1, personality).getFirst();
    }

    /** {@code count} lords, their villages side by side in the scene. */
    private List<Long> lords(int count, NpcPersonality personality) {
        jdbc.sql(
                        "update world_settings set initialized_at = null, barbarian_initial_count ="
                                + " 0, npc_lord_count = ?")
                .param(count)
                .update();
        worldService.reload();
        initializer.initialize();
        List<Long> ids =
                players.findAll().stream()
                        .filter(Player::isNpc)
                        .map(Player::getId)
                        .sorted(Comparator.naturalOrder())
                        .toList();
        int i = 0;
        for (long id : ids) {
            jdbc.sql("update npc_brain set personality = ?, template = ? where player_id = ?")
                    .params(personality.name(), personality.name(), id)
                    .update();
            long village = villageOf(id);
            jdbc.sql("update village set x = ?, y = ?, name = 'Lord village' where id = ?")
                    .params(baseX + 40 * i, baseY + 40, village)
                    .update();
            i++;
        }
        jdbc.sql("delete from game_event").update();
        return ids;
    }

    private long villageOf(long playerId) {
        return villages.findIdsByPlayerId(playerId).getFirst();
    }

    /** A registered human, his village moved into the scene and out of protection. */
    private TestPlayer human(int dx, int dy) {
        TestPlayer human;
        try {
            human = TestPlayer.register(mockMvc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Village village = villages.findById(human.villageId).orElseThrow();
        movedHumans.add(new long[] {human.villageId, village.getX(), village.getY()});
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .params(baseX + dx, baseY + 40 + dy, human.villageId)
                .update();
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(3600)), human.playerId)
                .update();
        return human;
    }

    /** A village with a market of 10 and {@code each} of every resource in a big warehouse. */
    private void rich(long villageId, int each) {
        jdbc.sql(
                        """
                        update village set b_market = 10, b_warehouse = 25, wood = ?, clay = ?,
                            iron = ?, resources_updated_at = ? where id = ?
                        """)
                .params(each, each, each, Timestamp.from(clock.instant()), villageId)
                .update();
    }

    private void units(long villageId, Map<UnitType, Integer> units) {
        StringBuilder sql = new StringBuilder("update unit_stack set ");
        for (UnitType type : UnitType.values()) {
            sql.append("u_")
                    .append(type.name().toLowerCase(Locale.ROOT))
                    .append(" = ")
                    .append(units.getOrDefault(type, 0))
                    .append(", ");
        }
        sql.append("version = version + 1 where home_village_id = ? and location_village_id = ?");
        jdbc.sql(sql.toString()).params(villageId, villageId).update();
    }
}
