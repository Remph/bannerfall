package de.bannerfall.paladin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
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
 * The paladin moves into the Banner Hall (1.4, 02-game-rules §5.5): once, every paladin whose home
 * village has no Banner Hall leaves, wherever he is, and the home village gets his cost back in
 * full. The tests share the database, so they look at their own villages only, not at the count the
 * move returns.
 */
@IntegrationTest
class PaladinHallMoveFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired PaladinHallMove move;

    private TestPlayer player;
    private TestPlayer friend;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        allowTheMove();
        player = TestPlayer.register(mockMvc);
        friend = TestPlayer.register(mockMvc);
        setLevel(player.villageId, BuildingType.HEADQUARTERS, 8);
        setLevel(player.villageId, BuildingType.BARRACKS, 5);
        setLevel(player.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(player.villageId, BuildingType.FARM, 15);
        setLevel(player.villageId, BuildingType.WAREHOUSE, 20);
        setLevel(player.villageId, BuildingType.RALLY_POINT, 1);
        setStock(30_000);
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("delete from game_event").update();
        allowTheMove();
    }

    @Test
    void paladinsWithoutAHallLeaveWhereverTheyAreAndArePaidBack() throws Exception {
        Coordinates friendAt = coordinates(friend);
        // in training, with a thief order behind him in the hall's queue
        player.recruitOk(UnitType.PALADIN, 1);
        player.recruitOk(UnitType.BANNER_THIEF, 2);
        // two supports on the way: one of a paladin alone, one with spearmen
        setHomeUnits("u_paladin", 2);
        setHomeUnits("u_spearman", 10);
        player.command("SUPPORT", friendAt, "{\"PALADIN\":1}").andExpect(status().isCreated());
        player.command("SUPPORT", friendAt, "{\"PALADIN\":1,\"SPEARMAN\":5}")
                .andExpect(status().isCreated());
        // one in a support that stands at the friend's, one waiting to be dismissed, one at home
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_paladin)"
                                + " values (?, ?, 1)")
                .params(player.villageId, friend.villageId)
                .update();
        setHomeUnits("u_paladin", 2);
        player.dismissOk(UnitType.PALADIN, 1);
        // then the hall is gone (what a village from before 1.4 looks like)
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        setStock(10_000);
        Instant now = clock.instant();

        assertThat(move.run()).isGreaterThanOrEqualTo(6);

        // six paladins, 1000 / 1000 / 1000 each, in full
        assertThat(stock()).containsExactly(16_000, 16_000, 16_000);
        player.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
        assertThat(paladinsAnywhere()).isZero();
        // the support of the paladin alone is called off, the other walks on with its spearmen
        assertThat(
                        jdbc.sql(
                                        "select u_spearman from movement where home_village_id = ?"
                                                + " order by id")
                                .param(player.villageId)
                                .query(Integer.class)
                                .list())
                .containsExactly(5);
        assertThat(
                        jdbc.sql("select count(*) from game_event where kind = 'MOVEMENT_ARRIVE'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        // the stack of the paladin alone at the friend's is gone
        assertThat(
                        jdbc.sql(
                                        "select count(*) from unit_stack where home_village_id = ?"
                                                + " and location_village_id = ?")
                                .params(player.villageId, friend.villageId)
                                .query(Integer.class)
                                .single())
                .isZero();
        // the thieves move up to now in the hall's queue
        List<Timestamp> thieves =
                jdbc.sql(
                                "select start_at from recruit_order where village_id = ?"
                                        + " and unit = 'BANNER_THIEF'")
                        .param(player.villageId)
                        .query(Timestamp.class)
                        .list();
        assertThat(thieves).singleElement().isEqualTo(Timestamp.from(now));
        player.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(5));
    }

    @Test
    void aBarracksOrderFromBeforeTheMoveIsPaidBackAndTheBarracksQueueMovesUp() throws Exception {
        // what Realm 1's lords have: a paladin ordered in the Barracks under 1.3.2, half done,
        // spearmen waiting behind him
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        Instant now = clock.instant();
        jdbc.sql(
                        """
                        insert into recruit_order (village_id, building, unit, count,
                          completed_count, seconds_per_unit, start_at, end_at, unit_cost_wood,
                          unit_cost_clay, unit_cost_iron, unit_pop, created_at)
                        values (?, 'BARRACKS', 'PALADIN', 1, 0, 3600, ?, ?, 1000, 1000, 1000, 10, ?)
                        """)
                .params(
                        player.villageId,
                        Timestamp.from(now.minusSeconds(1800)),
                        Timestamp.from(now.plusSeconds(1800)),
                        Timestamp.from(now.minusSeconds(1800)))
                .update();
        player.recruitOk(UnitType.SPEARMAN, 2);
        setStock(10_000);

        move.run();

        assertThat(stock()).containsExactly(11_000, 11_000, 11_000);
        assertThat(paladinsAnywhere()).isZero();
        assertThat(
                        jdbc.sql(
                                        "select start_at from recruit_order where village_id = ?"
                                                + " and unit = 'SPEARMAN'")
                                .param(player.villageId)
                                .query(Timestamp.class)
                                .single())
                .isEqualTo(Timestamp.from(now));
    }

    @Test
    void paladinsInAVillageWithAHallStay() throws Exception {
        player.recruitOk(UnitType.PALADIN, 1);
        setHomeUnits("u_paladin", 1);

        move.run();

        assertThat(paladinsAnywhere()).isEqualTo(2);
        assertThat(stock()).containsExactly(29_000, 29_000, 29_000);
    }

    @Test
    void theMoveRunsOnce() throws Exception {
        move.run();
        setLevel(player.villageId, BuildingType.BANNER_HALL, 0);
        setHomeUnits("u_paladin", 1);

        // later a hall can fall to catapults: his paladin stays, as for any building requirement
        assertThat(move.run()).isEqualTo(-1);
        assertThat(paladinsAnywhere()).isEqualTo(1);
    }

    private void allowTheMove() {
        jdbc.sql("delete from one_time_job where name = ?").param(PaladinHallMove.JOB).update();
    }

    /** The player's paladins at home, in supports, on the way and in recruit or dismiss orders. */
    private int paladinsAnywhere() {
        return jdbc.sql(
                        """
                        select (select coalesce(sum(u_paladin), 0) from unit_stack
                                where home_village_id = :v)
                             + (select coalesce(sum(u_paladin), 0) from movement
                                where home_village_id = :v)
                             + (select coalesce(sum(count - completed_count), 0) from recruit_order
                                where village_id = :v and unit = 'PALADIN')
                             + (select coalesce(sum(count - completed_count), 0) from dismiss_order
                                where village_id = :v and unit = 'PALADIN')
                        """)
                .param("v", player.villageId)
                .query(Integer.class)
                .single();
    }

    private List<Integer> stock() {
        return jdbc.sql(
                        "select floor(wood)::int, floor(clay)::int, floor(iron)::int from village"
                                + " where id = ?")
                .param(player.villageId)
                .query((rs, n) -> List.of(rs.getInt(1), rs.getInt(2), rs.getInt(3)))
                .single();
    }

    private Coordinates coordinates(TestPlayer p) {
        return jdbc.sql("select x, y from village where id = ?")
                .param(p.villageId)
                .query((rs, n) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                .single();
    }

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, villageId)
                .update();
    }

    private void setStock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    /** The column name is a constant of this test, never user input. */
    private void setHomeUnits(String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }
}
