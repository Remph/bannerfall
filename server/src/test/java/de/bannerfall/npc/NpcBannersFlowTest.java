package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.banner.BannerService;
import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldInitializer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * Lord banners (1.4, 09-npc-lords §14) end to end: drops, conquest, theft on retaliations,
 * standards taken from beaten attacks, choosing and fusing, Hall training, and the switch. Like
 * {@code NpcWarfareFlowTest}, every scene lies far off the map (x ≥ 2000) and is removed after.
 */
@IntegrationTest
class NpcBannersFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AtomicInteger AREA = new AtomicInteger();
    private static final double ALWAYS = 0.0;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired WorldInitializer initializer;
    @Autowired NpcBrainRepository brains;
    @Autowired PlayerRepository players;
    @Autowired VillageRepository villages;
    @Autowired UnitStackRepository stacks;
    @Autowired GameData gameData;
    @Autowired TestRandomSource random;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CommandService commands;
    @Autowired BannerService bannerService;
    @Autowired VillageBanners villageBanners;

    private TransactionTemplate transaction;
    private int baseX;
    private final int baseY = 2400;
    private final List<long[]> movedHumans = new ArrayList<>();
    private final List<Long> humans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set barbarians_per_new_player = 0,"
                                + " npc_difficulty = 'NORMAL', speed = 1.0, banners_enabled ="
                                + " true, npc_banners_enabled = true")
                .update();
        worldService.reload();
        clock.set(nextBerlin(LocalTime.NOON));
        baseX = 2000 + 200 * AREA.getAndIncrement();
    }

    @AfterEach
    void cleanUp() {
        random.reset();
        jdbc.sql("delete from game_event").update();
        String scene = "select id from village where x >= 2000";
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
                    .param(moved[1])
                    .param(moved[2])
                    .param(moved[0])
                    .update();
        }
        for (long human : humans) {
            jdbc.sql("delete from banner where owner_id = ?").param(human).update();
        }
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from movement where home_village_id in (%1$s) or target_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql(
                        "delete from unit_stack where home_village_id in (%1$s) or location_village_id in (%1$s)"
                                .formatted(lordVillages))
                .update();
        jdbc.sql("delete from movement where owner_player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from report where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from player where npc").update();
        jdbc.sql("delete from unit_stack where home_village_id in (" + scene + ")").update();
        jdbc.sql("delete from village where x >= 2000").update();
        jdbc.sql(
                        "update world_settings set npc_lord_count = 0, npc_difficulty = 'NORMAL',"
                                + " npc_custom = null, speed = 1.0, barbarian_initial_count = 30,"
                                + " barbarians_per_new_player = 0, banners_enabled = true,"
                                + " banner_drop_factor = 1.0, npc_banners_enabled = false")
                .update();
        worldService.reload();
    }

    // ---- getting banners (§14.1) ----

    @Test
    void aLordFindsABannerBeatingABarbarianAndFliesItOnceTheHallStands() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.AXEMAN, 300));
        long barbarian = barbarian(3, 0);
        units(barbarian, Map.of(UnitType.SPEARMAN, 30)); // 30 population: just enough

        random.use(FixedRandomSource.constant(ALWAYS));
        arriveAt(sendAttack(lord, village, barbarian, Map.of(UnitType.AXEMAN, 200)));
        random.reset();

        List<String> found = bannersOf(lord);
        assertThat(found).hasSize(1).allMatch(b -> b.endsWith(":FOUND:-"));
        // no Banner Hall yet: it waits in the stash
        round(lord);
        assertThat(bannersOf(lord).getFirst()).endsWith(":-");

        buildings(village, Map.of(BuildingType.BANNER_HALL, 1));
        round(lord);
        assertThat(bannersOf(lord).getFirst()).endsWith(":" + village);
        assertThat(effectOf(village).effectiveTier()).isEqualTo(1);
    }

    @Test
    void withLordBannersOffALordFindsNothingAndItsBannersSleep() {
        switchLordBanners(false);
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.BANNER_HALL, 1));
        units(village, Map.of(UnitType.AXEMAN, 300));
        long barbarian = barbarian(3, 0);
        units(barbarian, Map.of(UnitType.SPEARMAN, 30));
        long flying = insertBanner(lord, BannerType.HARVEST, 2, village);
        long stash = insertBanner(lord, BannerType.WAR, 3, null);

        random.use(FixedRandomSource.constant(ALWAYS));
        arriveAt(sendAttack(lord, village, barbarian, Map.of(UnitType.AXEMAN, 200)));
        random.reset();
        round(lord);

        assertThat(bannersOf(lord))
                .containsExactly(
                        "HARVEST:2:GRANTED:" + village, "WAR:3:GRANTED:-"); // nothing found
        assertThat(effectOf(village)).isEqualTo(BannerEffect.NONE);

        // switched on, the flying one works and the better one goes up at the next round
        switchLordBanners(true);
        assertThat(effectOf(village).type()).isEqualTo(BannerType.HARVEST);
        round(lord);
        assertThat(villageOfBanner(stash)).isEqualTo(village);
        assertThat(villageOfBanner(flying)).isNull();
    }

    @Test
    void aLordsConquestTakesTheBannerIntoItsStash() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        TestPlayer human = human(5, 0);
        buildings(human.villageId, Map.of(BuildingType.BANNER_HALL, 1));
        long banner = insertBanner(human.playerId, BannerType.BULWARK, 2, human.villageId);

        transaction.executeWithoutResult(
                status -> {
                    Village village = villages.findById(human.villageId).orElseThrow();
                    Player conqueror = players.findById(lord).orElseThrow();
                    jdbc.sql("update village set player_id = ? where id = ?")
                            .param(lord)
                            .param(human.villageId)
                            .update();
                    bannerService.villageConquered(village, conqueror, clock.instant());
                });

        assertThat(ownerOfBanner(banner)).isEqualTo(lord);
        assertThat(villageOfBanner(banner)).isNull();
        // put the village back for the clean-up
        jdbc.sql("update village set player_id = ? where id = ?")
                .param(human.playerId)
                .param(human.villageId)
                .update();
    }

    @Test
    void anAttackOnItsWayKeepsTheSlotAsItIsAndTheTurnGoesOn() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.BANNER_HALL, 1));
        long stash = insertBanner(lord, BannerType.WAR, 1, null);
        TestPlayer human = human(10, 0);
        units(human.villageId, Map.of(UnitType.AXEMAN, 10));
        Instant arrival = humanAttack(human, village, "{\"AXEMAN\":10}");

        round(lord);

        // BANNER_LOCKED: nothing goes up, and the village turn isn't rolled back for it
        assertThat(villageOfBanner(stash)).isNull();
        assertThat(
                        jdbc.sql("select count(*) from build_order where village_id = ?")
                                .param(village)
                                .query(Long.class)
                                .single())
                .isPositive();

        arriveAt(arrival);
        round(lord);
        assertThat(villageOfBanner(stash)).isEqualTo(village);
    }

    // ---- thieves and the paladin on retaliations (§14.4, §14.5) ----

    @Test
    void aRetaliationTakesThePaladinAndTheThievesAndStealsTheBanner() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.BANNER_HALL, 1));
        units(
                village,
                Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 10, UnitType.PALADIN, 1));
        // 30 fields: beyond the farm radius, only a retaliation goes there
        TestPlayer human = human(30, 0);
        buildings(human.villageId, Map.of(BuildingType.BANNER_HALL, 1));
        long banner = insertBanner(human.playerId, BannerType.HARVEST, 1, human.villageId);
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.intel.put(
                            human.villageId,
                            intel(now, human.playerId, new ScoutedBanner(BannerType.HARVEST, 1)));
                    m.grudges.put(
                            human.playerId,
                            new NpcMemory.Grudge(
                                    now.plus(Duration.ofHours(10)), human.villageId, false, 0));
                });

        round(lord);

        List<Map<String, Object>> sent = attacksTo(lord, human.villageId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst())
                .containsEntry("u_banner_thief", 10)
                .containsEntry("u_paladin", 1);

        random.use(FixedRandomSource.constant(ALWAYS));
        arriveAt(((Timestamp) sent.getFirst().get("arrive_at")).toInstant());
        assertThat(ownerOfBanner(banner)).isEqualTo(lord);
        assertThat(villageOfBanner(banner)).isNull();
    }

    @Test
    void withoutABannerInTheIntelTheThievesStayHome() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(village, Map.of(BuildingType.BANNER_HALL, 1));
        units(village, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 10));
        TestPlayer human = human(30, 0);
        Instant now = clock.instant();
        remember(
                lord,
                m -> {
                    m.intel.put(human.villageId, intel(now, human.playerId, null));
                    m.grudges.put(
                            human.playerId,
                            new NpcMemory.Grudge(
                                    now.plus(Duration.ofHours(10)), human.villageId, false, 0));
                });

        round(lord);

        List<Map<String, Object>> sent = attacksTo(lord, human.villageId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst()).containsEntry("u_banner_thief", 0);
    }

    @Test
    void aPlayersBeatenPaladinAttackLosesItsStandardToTheLordsThieves() throws Exception {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        units(village, Map.of(UnitType.SPEARMAN, 100, UnitType.BANNER_THIEF, 10));
        TestPlayer human = human(3, 0);
        buildings(human.villageId, Map.of(BuildingType.BANNER_HALL, 1));
        long axe = insertBanner(human.playerId, BannerType.AXE_STANDARD, 1, human.villageId);
        units(human.villageId, Map.of(UnitType.AXEMAN, 10, UnitType.PALADIN, 1));

        random.use(FixedRandomSource.constant(ALWAYS));
        arriveAt(humanAttack(human, village, "{\"AXEMAN\":10,\"PALADIN\":1}"));

        assertThat(ownerOfBanner(axe)).isEqualTo(lord);
        assertThat(villageOfBanner(axe)).isNull();
    }

    // ---- fusing and Hall training (§14.3, §14.6) ----

    @Test
    void aLordFusesItsLeastFavouriteBannersIntoItsBestOfThem() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        insertBanner(lord, BannerType.OATH, 1, null);
        insertBanner(lord, BannerType.BULWARK, 1, null);
        insertBanner(lord, BannerType.HARVEST, 1, null);
        insertBanner(lord, BannerType.WAR, 1, null);

        round(lord);

        // OFFENSIVE: Oath, Bulwark and Harvest are its least favourite, Harvest the best of them
        assertThat(bannersOf(lord)).containsExactly("WAR:1:GRANTED:-", "HARVEST:2:FUSED:-");
    }

    @Test
    void aLordTrainsItsPaladinAndKeepsItsThieves() {
        long lord = lord(NpcPersonality.OFFENSIVE);
        long village = villageOf(lord);
        buildings(
                village,
                Map.of(
                        BuildingType.HEADQUARTERS, 15,
                        BuildingType.BARRACKS, 10,
                        BuildingType.BANNER_HALL, 2,
                        BuildingType.WAREHOUSE, 25,
                        BuildingType.FARM, 25));
        jdbc.sql("update village set wood = 100000, clay = 100000, iron = 100000 where id = ?")
                .param(village)
                .update();
        // far behind on troops: the recruit step comes first, with a budget that pays it all
        remember(lord, m -> m.ledger.put(village, new NpcMemory.Ledger(1_000_000, 0)));

        round(lord);

        assertThat(
                        jdbc.sql(
                                        "select level from research_order where player_id = ? and"
                                                + " unit = 'PALADIN'")
                                .param(lord)
                                .query(Integer.class)
                                .list())
                .containsExactly(2);
        assertThat(
                        jdbc.sql(
                                        "select coalesce(sum(count), 0) from recruit_order where"
                                                + " village_id = ? and unit = 'BANNER_THIEF'")
                                .param(village)
                                .query(Integer.class)
                                .single())
                .isEqualTo(10);
    }

    // ---- helpers: scene ----

    private long lord(NpcPersonality personality) {
        jdbc.sql(
                        "update world_settings set initialized_at = null, barbarian_initial_count ="
                                + " 0, npc_lord_count = 1")
                .update();
        worldService.reload();
        initializer.initialize();
        long id =
                players.findAll().stream()
                        .filter(Player::isNpc)
                        .map(Player::getId)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
        jdbc.sql("update npc_brain set personality = ?, template = ? where player_id = ?")
                .param(personality.name())
                .param(personality.name())
                .param(id)
                .update();
        place(villageOf(id), 0, 0);
        jdbc.sql("delete from game_event").update();
        return id;
    }

    private long villageOf(long playerId) {
        return villages.findIdsByPlayerId(playerId).getFirst();
    }

    private void place(long villageId, int dx, int dy) {
        jdbc.sql("update village set x = ?, y = ? where id = ?")
                .param(baseX + dx)
                .param(baseY + dy)
                .param(villageId)
                .update();
    }

    /** A registered human, protection over, with his village moved into the scene. */
    private TestPlayer human(int dx, int dy) throws Exception {
        TestPlayer human = TestPlayer.register(mockMvc);
        Village village = villages.findById(human.villageId).orElseThrow();
        movedHumans.add(new long[] {human.villageId, village.getX(), village.getY()});
        humans.add(human.playerId);
        place(human.villageId, dx, dy);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .param(Timestamp.from(clock.instant().minusSeconds(3600)))
                .param(human.playerId)
                .update();
        return human;
    }

    private long barbarian(int dx, int dy) {
        return transaction.execute(
                status -> {
                    BuildingLevels levels = new BuildingLevels();
                    for (BuildingType type : BuildingType.values()) {
                        levels.set(type, gameData.building(type).startLevel());
                    }
                    levels.set(BuildingType.WAREHOUSE, 5);
                    int points = BuildRules.villagePoints(gameData, levels.toMap());
                    Village village =
                            villages.save(
                                    new Village(
                                            null,
                                            null,
                                            new Coordinates(baseX + dx, baseY + dy),
                                            1000,
                                            levels,
                                            points,
                                            clock.instant()));
                    stacks.save(UnitStack.homeOf(village));
                    return village.getId();
                });
    }

    /** Sets the village's own units at home (all other types 0). */
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
        jdbc.sql(sql.toString()).param(villageId).param(villageId).update();
    }

    private void buildings(long villageId, Map<BuildingType, Integer> levels) {
        levels.forEach(
                (type, level) ->
                        jdbc.sql(
                                        "update village set b_%s = ? where id = ?"
                                                .formatted(type.name().toLowerCase(Locale.ROOT)))
                                .param(level)
                                .param(villageId)
                                .update());
    }

    private void switchLordBanners(boolean on) {
        jdbc.sql("update world_settings set npc_banners_enabled = ?").param(on).update();
        worldService.reload();
    }

    // ---- helpers: banners ----

    private long insertBanner(long owner, BannerType type, int tier, Long villageId) {
        return jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, ?, ?, ?, case when ?::bigint is null then null else now() end,
                                'GRANTED', now())
                        returning id
                        """)
                .params(owner, type.name(), tier, villageId, villageId)
                .query(Long.class)
                .single();
    }

    /** "TYPE:tier:origin:village" of every banner the player owns ("-" in the stash). */
    private List<String> bannersOf(long playerId) {
        return jdbc.sql(
                        "select type || ':' || tier || ':' || origin || ':' ||"
                                + " coalesce(equipped_village_id::text, '-') from banner where"
                                + " owner_id = ? order by id")
                .param(playerId)
                .query(String.class)
                .list();
    }

    private Long villageOfBanner(long bannerId) {
        return jdbc.sql("select equipped_village_id from banner where id = ?")
                .param(bannerId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private long ownerOfBanner(long bannerId) {
        return jdbc.sql("select owner_id from banner where id = ?")
                .param(bannerId)
                .query(Long.class)
                .single();
    }

    private BannerEffect effectOf(long villageId) {
        return transaction.execute(
                status -> villageBanners.effectOf(villages.findById(villageId).orElseThrow()));
    }

    // ---- helpers: memory, rounds, attacks ----

    private void remember(long lord, java.util.function.Consumer<NpcMemory.Draft> change) {
        transaction.executeWithoutResult(
                status -> {
                    NpcBrain brain = brains.findById(lord).orElseThrow();
                    NpcMemory.Draft draft = brain.getMemory().draft();
                    change.accept(draft);
                    brain.setMemory(draft.build());
                });
    }

    private static Intel intel(Instant at, long ownerId, ScoutedBanner banner) {
        return new Intel(
                at,
                ownerId,
                Map.of(),
                0,
                Map.of(BuildingType.BANNER_HALL, 1),
                null,
                null,
                false,
                BattleOutcome.ATTACKER_WON,
                0,
                null,
                banner);
    }

    /** One round of the lord now, through the event worker. */
    private void round(long lord) {
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.NPC_THINK, lord, clock.instant()));
        processor.processDue(clock.instant());
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    /** An attack of the lord sent like a player's; returns its arrival. */
    private Instant sendAttack(
            long lord, long from, long targetVillageId, Map<UnitType, Integer> units) {
        Village target = villages.findById(targetVillageId).orElseThrow();
        return commands.send(
                        from,
                        lord,
                        new CommandRequest(CommandKind.ATTACK, target.coordinates(), units, null))
                .arriveAt();
    }

    private Instant humanAttack(TestPlayer human, long targetVillageId, String unitsJson)
            throws Exception {
        Village target = villages.findById(targetVillageId).orElseThrow();
        String sent =
                human.command("ATTACK", target.coordinates(), unitsJson)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return Instant.parse(JsonPath.read(sent, "$.arriveAt"));
    }

    private List<Map<String, Object>> attacksTo(long lord, long villageId) {
        return jdbc.sql(
                        """
                        select u_axeman, u_banner_thief, u_paladin, arrive_at from movement
                        where owner_player_id = ? and target_village_id = ? and kind = 'ATTACK'
                        order by arrive_at, id
                        """)
                .param(lord)
                .param(villageId)
                .query()
                .listOfRows();
    }

    /** The next moment (not before now) at which the clock in Berlin shows {@code time}. */
    private Instant nextBerlin(LocalTime time) {
        LocalDateTime candidate = clock.instant().atZone(BERLIN).toLocalDate().atTime(time);
        Instant at = candidate.atZone(BERLIN).toInstant();
        return at.isBefore(clock.instant()) ? candidate.plusDays(1).atZone(BERLIN).toInstant() : at;
    }
}
