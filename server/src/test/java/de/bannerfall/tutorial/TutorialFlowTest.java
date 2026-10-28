package de.bannerfall.tutorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The tutorial (1.7, 02-game-rules §27): {@code /api/tutorial/**}. */
@IntegrationTest
class TutorialFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired WorldService worldService;

    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        // guilds are off by default; the 11th step needs them
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void restoreWorld() {
        jdbc.sql(
                        "update world_settings set tutorial_enabled = true, guilds_enabled = false,"
                                + " unit_levels_enabled = false, banners_enabled = true,"
                                + " automation_villages = 3")
                .update();
        worldService.reload();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private ResultActions sync(TestPlayer who) throws Exception {
        return who.browser.post("/api/tutorial/sync", "");
    }

    private String syncBody() throws Exception {
        return TestPlayer.body(sync(player).andExpect(status().isOk()));
    }

    private void level(String column, int level) {
        jdbc.sql("update village set " + column + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void stock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private int wood() {
        return jdbc.sql("select floor(wood)::int from village where id = ?")
                .param(player.villageId)
                .query(Integer.class)
                .single();
    }

    private void spearmen(int count) {
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman)"
                            + " values (?, ?, ?) on conflict (home_village_id, location_village_id)"
                            + " do update set u_spearman = excluded.u_spearman")
                .params(player.villageId, player.villageId, count)
                .update();
    }

    private String current(String body) {
        return JsonPath.<String>read(body, "$.current.key");
    }

    private ResultActions post(String path) throws Exception {
        return player.browser.post(path, "");
    }

    // ---- tests -----------------------------------------------------------------------------

    @Test
    void aNewPlayerHasTheTutorialOnAndStartsAtTheWoodcutter() throws Exception {
        String body = syncBody();
        assertThat(JsonPath.<Boolean>read(body, "$.enabled")).isTrue();
        assertThat(JsonPath.<Boolean>read(body, "$.active")).isTrue();
        assertThat(current(body)).isEqualTo("WOODCUTTER");
        assertThat(JsonPath.<String>read(body, "$.current.kind")).isEqualTo("STEP");
        assertThat(JsonPath.<Integer>read(body, "$.current.index")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.current.total")).isEqualTo(11);
        assertThat(JsonPath.<Integer>read(body, "$.current.refund.wood")).isEqualTo(63);
        assertThat(JsonPath.<List<String>>read(body, "$.refunds")).isEmpty();
        // 11 steps, no lesson of a feature the world lacks (unit levels are off by default)
        assertThat(JsonPath.<List<String>>read(body, "$.steps[?(@.kind=='STEP')].key")).hasSize(11);
        assertThat(JsonPath.<List<String>>read(body, "$.steps[?(@.kind=='LESSON')].key"))
                .containsExactly("LESSON_CONQUEST", "LESSON_AUTOMATION", "LESSON_BANNER_HALL");
    }

    @Test
    void aPlayerFromBeforeTheTutorialHasNoCardUntilHeStartsIt() throws Exception {
        jdbc.sql("delete from tutorial_state where player_id = ?").param(player.playerId).update();
        sync(player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.steps").isEmpty());
        level("b_woodcutter", 2);
        sync(player).andExpect(jsonPath("$.active").value(false));
        assertThat(rows()).isZero();

        post("/api/tutorial/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.current.key").value("HEADQUARTERS"));
    }

    @Test
    void aFinishedStepRefundsItsPriceOnceIntoTheVillage() throws Exception {
        stock(100);
        level("b_woodcutter", 2);
        String body = syncBody();
        assertThat(current(body)).isEqualTo("HEADQUARTERS");
        assertThat(JsonPath.<List<String>>read(body, "$.refunds[*].key"))
                .containsExactly("WOODCUTTER");
        assertThat(JsonPath.<Integer>read(body, "$.refunds[0].amount.wood")).isEqualTo(63);
        assertThat(JsonPath.<Integer>read(body, "$.refunds[0].amount.clay")).isEqualTo(75);
        assertThat(JsonPath.<Integer>read(body, "$.refunds[0].amount.iron")).isEqualTo(50);
        assertThat(JsonPath.<Number>read(body, "$.refunds[0].villageId").longValue())
                .isEqualTo(player.villageId);
        assertThat(wood()).isEqualTo(163);

        // asking again pays nothing, and neither does losing the level
        String again = syncBody();
        assertThat(JsonPath.<List<String>>read(again, "$.refunds")).isEmpty();
        level("b_woodcutter", 1);
        sync(player).andExpect(jsonPath("$.refunds").isEmpty());
        assertThat(wood()).isEqualTo(163);
        assertThat(JsonPath.<List<String>>read(again, "$.steps[?(@.key=='WOODCUTTER')].status"))
                .containsExactly("DONE");
    }

    @Test
    void theRefundStopsAtTheWarehouse() throws Exception {
        stock(1000); // a level 1 warehouse holds 1000
        level("b_woodcutter", 2);
        String body = syncBody();
        assertThat(JsonPath.<Integer>read(body, "$.refunds[0].amount.wood")).isZero();
        assertThat(wood()).isEqualTo(1000);
    }

    @Test
    void oneStepOpensTheNextAndTheChainPaysEveryRefundInOneCall() throws Exception {
        stock(0);
        level("b_woodcutter", 2);
        level("b_headquarters", 3);
        level("b_barracks", 1);
        spearmen(5);
        String body = syncBody();
        assertThat(JsonPath.<List<String>>read(body, "$.refunds[*].key"))
                .containsExactly("WOODCUTTER", "BARRACKS", "SPEARMEN");
        // woodcutter 63 + barracks 180 + 5 spearmen 200
        assertThat(wood()).isEqualTo(63 + 180 + 200);
        assertThat(current(body)).isEqualTo("MAP_BARBARIAN");
        assertThat(JsonPath.<Integer>read(body, "$.current.index")).isEqualTo(5);
    }

    @Test
    void aRecruitOrderIsNotEnoughForTheSpearmen() throws Exception {
        level("b_woodcutter", 2);
        level("b_headquarters", 3);
        level("b_barracks", 1);
        jdbc.sql(
                        "insert into recruit_order (village_id, building, unit, count,"
                                + " completed_count, seconds_per_unit, start_at, end_at,"
                                + " unit_cost_wood, unit_cost_clay, unit_cost_iron, unit_pop,"
                                + " created_at) values (?, 'BARRACKS', 'SPEARMAN', 5, 0, 60, ?, ?,"
                                + " 40, 35, 15, 1, ?)")
                .params(
                        player.villageId,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant().plusSeconds(300)),
                        Timestamp.from(clock.instant()))
                .update();
        // the order can still be cancelled and its cost comes back: only trained spearmen count
        assertThat(current(syncBody())).isEqualTo("SPEARMEN");
        spearmen(5);
        assertThat(current(syncBody())).isEqualTo("MAP_BARBARIAN");
    }

    @Test
    void aWorldWithoutBarbarianVillagesHasNoStepForThem() throws Exception {
        List<Integer> before =
                jdbc.sql(
                                "select barbarian_initial_count, barbarians_per_new_player"
                                        + " from world_settings")
                        .query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2)))
                        .single();
        boolean growth =
                jdbc.sql("select barbarian_growth_enabled from world_settings")
                        .query(Boolean.class)
                        .single();
        try {
            jdbc.sql(
                            "update world_settings set barbarian_initial_count = 0,"
                                    + " barbarians_per_new_player = 0,"
                                    + " barbarian_growth_enabled = false")
                    .update();
            worldService.reload();
            level("b_woodcutter", 2);
            level("b_headquarters", 3);
            level("b_barracks", 1);
            spearmen(5);

            String body = syncBody();
            assertThat(JsonPath.<Integer>read(body, "$.current.total")).isEqualTo(10);
            assertThat(JsonPath.<List<String>>read(body, "$.steps[*].key"))
                    .doesNotContain("MAP_BARBARIAN");
            assertThat(current(body)).isEqualTo("ATTACK");
        } finally {
            jdbc.sql(
                            "update world_settings set barbarian_initial_count = ?,"
                                + " barbarians_per_new_player = ?, barbarian_growth_enabled = ?")
                    .params(before.get(0), before.get(1), growth)
                    .update();
            worldService.reload();
        }
    }

    @Test
    void aSkippedStepNeverPaysEvenIfDoneLater() throws Exception {
        stock(0);
        post("/api/tutorial/steps/WOODCUTTER/skip")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.key").value("HEADQUARTERS"));
        level("b_woodcutter", 2);
        String body = syncBody();
        assertThat(JsonPath.<List<String>>read(body, "$.refunds")).isEmpty();
        assertThat(wood()).isZero();
        assertThat(JsonPath.<List<String>>read(body, "$.steps[?(@.key=='WOODCUTTER')].status"))
                .containsExactly("SKIPPED");
        // skipping twice is fine
        post("/api/tutorial/steps/WOODCUTTER/skip").andExpect(status().isOk());
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void theClientTicksOffWhatTheServerCannotSee() throws Exception {
        level("b_woodcutter", 2);
        level("b_headquarters", 3);
        level("b_barracks", 1);
        spearmen(5);
        assertThat(current(syncBody())).isEqualTo("MAP_BARBARIAN");

        // the simulator comes later in the chain
        post("/api/tutorial/steps/SIMULATOR/done")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTORIAL_STEP_NOT_OPEN"));
        post("/api/tutorial/steps/MAP_BARBARIAN/done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.key").value("ATTACK"));
        // ticking it again changes nothing
        post("/api/tutorial/steps/MAP_BARBARIAN/done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.key").value("ATTACK"));
    }

    @Test
    void aStepTheServerSeesByItselfCannotBeTickedAndAnUnknownKeyIs404() throws Exception {
        post("/api/tutorial/steps/WOODCUTTER/done")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        post("/api/tutorial/steps/NOPE/done")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTORIAL_STEP_NOT_FOUND"));
        post("/api/tutorial/steps/NOPE/skip")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTORIAL_STEP_NOT_FOUND"));
    }

    @Test
    void aLessonComesUpWhenItsBuildingOpensAndBeforeTheNextStep() throws Exception {
        assertThat(current(syncBody())).isEqualTo("WOODCUTTER");
        level("b_academy", 1);
        String body = syncBody();
        assertThat(current(body)).isEqualTo("LESSON_CONQUEST");
        assertThat(JsonPath.<String>read(body, "$.current.kind")).isEqualTo("LESSON");
        assertThat(JsonPath.<Integer>read(body, "$.current.index")).isZero();
        post("/api/tutorial/steps/LESSON_CONQUEST/done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.key").value("WOODCUTTER"));
        // a lesson that is not open yet cannot be ticked
        post("/api/tutorial/steps/LESSON_BANNER_HALL/done")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTORIAL_STEP_NOT_OPEN"));
    }

    @Test
    void aLessonOfAFeatureTheWorldLacksNeverShows() throws Exception {
        level("b_smithy", 1);
        level("b_banner_hall", 1);
        jdbc.sql("update world_settings set unit_levels_enabled = false, banners_enabled = false")
                .update();
        worldService.reload();
        String body = syncBody();
        assertThat(current(body)).isEqualTo("WOODCUTTER");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].key"))
                .doesNotContain("LESSON_UNIT_LEVELS", "LESSON_BANNER_HALL");

        jdbc.sql("update world_settings set unit_levels_enabled = true, banners_enabled = true")
                .update();
        worldService.reload();
        assertThat(current(syncBody())).isEqualTo("LESSON_UNIT_LEVELS");
    }

    @Test
    void theGuildStepIsMissingInAWorldWithoutGuilds() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = false").update();
        worldService.reload();
        String body = syncBody();
        assertThat(JsonPath.<Integer>read(body, "$.current.total")).isEqualTo(10);
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].key")).doesNotContain("GUILD");
    }

    @Test
    void stoppingAndStartingAgainTicksWhatHappenedMeanwhileWithoutARefund() throws Exception {
        stock(0);
        post("/api/tutorial/stop")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.current").doesNotExist());
        level("b_woodcutter", 2);
        // nothing is recorded while it is off
        sync(player).andExpect(jsonPath("$.active").value(false));
        assertThat(rows()).isZero();

        post("/api/tutorial/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.refunds").isEmpty())
                .andExpect(jsonPath("$.current.key").value("HEADQUARTERS"));
        assertThat(wood()).isZero();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void aWorldWithTheTutorialOffHasNoCardAndNoRefunds() throws Exception {
        jdbc.sql("update world_settings set tutorial_enabled = false").update();
        worldService.reload();
        stock(0);
        level("b_woodcutter", 2);
        sync(player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.current").doesNotExist());
        post("/api/tutorial/start").andExpect(jsonPath("$.enabled").value(false));
        post("/api/tutorial/steps/WOODCUTTER/skip").andExpect(status().isOk());
        assertThat(wood()).isZero();
        assertThat(rows()).isZero();
    }

    @Test
    void anAttackAndItsReportAreSeenFromTheGameState() throws Exception {
        level("b_woodcutter", 2);
        level("b_headquarters", 3);
        level("b_barracks", 1);
        spearmen(5);
        post("/api/tutorial/steps/MAP_BARBARIAN/done").andExpect(status().isOk());
        assertThat(current(syncBody())).isEqualTo("ATTACK");

        long reportId = insertAttackReport(false);
        assertThat(current(syncBody())).isEqualTo("REPORT");
        jdbc.sql("update report set read = true where id = ?").param(reportId).update();
        assertThat(current(syncBody())).isEqualTo("SIMULATOR");
    }

    @Test
    void aSharedAttackReportIsNotYourOwnAttack() throws Exception {
        level("b_woodcutter", 2);
        level("b_headquarters", 3);
        level("b_barracks", 1);
        spearmen(5);
        post("/api/tutorial/steps/MAP_BARBARIAN/done").andExpect(status().isOk());

        // a guild mate sent his report: same kind, read, but not an attack of this player
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, read, payload, created_at,
                                            shared_by_id, shared_by_name)
                        values (?, 'ATTACK', ?, true, '{}'::jsonb, ?, ?, 'Mate')
                        """)
                .params(
                        player.playerId,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant()),
                        player.playerId + 1)
                .update();
        assertThat(current(syncBody())).isEqualTo("ATTACK");
    }

    @Test
    void aNewAccountsTutorialIsOnlyThereWhenTheWorldHasIt() throws Exception {
        jdbc.sql("update world_settings set tutorial_enabled = false").update();
        worldService.reload();
        TestPlayer late = TestPlayer.register(mockMvc);
        assertThat(
                        jdbc.sql("select count(*) from tutorial_state where player_id = ?")
                                .param(late.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    private long insertAttackReport(boolean read) {
        return jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, read, payload, created_at)
                        values (?, 'ATTACK', ?, ?, '{}'::jsonb, ?) returning id
                        """)
                .params(
                        player.playerId,
                        Timestamp.from(clock.instant()),
                        read,
                        Timestamp.from(clock.instant()))
                .query(Long.class)
                .single();
    }

    private long rows() {
        return jdbc.sql("select count(*) from tutorial_progress where player_id = ?")
                .param(player.playerId)
                .query(Long.class)
                .single();
    }
}
