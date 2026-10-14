package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Village points from the building levels, player points as the sum over the villages. */
@IntegrationTest
class PointsServiceTest {

    private static final String TEST_VILLAGE_NAME = "PointsServiceTest";
    private static final AtomicInteger counter = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired PointsService pointsService;
    @Autowired VillageRepository villages;
    @Autowired PlayerRepository players;
    @Autowired JdbcClient jdbc;

    private TransactionTemplate transaction;

    @Autowired
    void useTransactions(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    /** The extra villages are far outside the map, but don't leave them for other tests anyway. */
    @AfterEach
    void removeTheExtraVillages() {
        jdbc.sql("delete from village where name = ?").param(TEST_VILLAGE_NAME).update();
    }

    @Test
    void aPlayerIsWorthTheSumOfHisVillages() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);

        transaction.executeWithoutResult(
                status -> {
                    Village first = villages.findById(player.villageId).orElseThrow();
                    Player owner = players.getReferenceById(player.playerId);
                    // a second village; its stored 100 points are only there to be summed up
                    villages.save(
                            new Village(
                                    owner,
                                    TEST_VILLAGE_NAME,
                                    farAway(),
                                    500,
                                    new BuildingLevels(),
                                    100,
                                    clock.instant()));
                    // woodcutter 1 → 2: 6 points become 13 (02-game-rules §4.2)
                    first.getBuildings().add(BuildingType.WOODCUTTER, 1);

                    pointsService.recalculate(first);
                });

        assertThat(villages.findById(player.villageId).orElseThrow().getPoints()).isEqualTo(46);
        assertThat(players.findById(player.playerId).orElseThrow().getPoints()).isEqualTo(146);
    }

    @Test
    void aVillageIsWorthTheSumOfItsBuildings() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);

        transaction.executeWithoutResult(
                status -> {
                    Village village = villages.findById(player.villageId).orElseThrow();
                    // a points value that is wrong on purpose: the levels are the truth
                    village.setPoints(5);

                    pointsService.recalculate(village);
                });

        assertThat(villages.findById(player.villageId).orElseThrow().getPoints()).isEqualTo(39);
        assertThat(players.findById(player.playerId).orElseThrow().getPoints()).isEqualTo(39);
    }

    @Test
    void aBarbarianVillageHasPointsButNoOwnerToCreditThemTo() {
        Long id =
                transaction.execute(
                        status -> {
                            var levels = new BuildingLevels();
                            levels.set(BuildingType.HEADQUARTERS, 1);
                            Village village =
                                    villages.save(
                                            new Village(
                                                    null,
                                                    TEST_VILLAGE_NAME,
                                                    farAway(),
                                                    500,
                                                    levels,
                                                    0,
                                                    clock.instant()));

                            pointsService.recalculate(village);
                            return village.getId();
                        });

        assertThat(villages.findById(id).orElseThrow().getPoints()).isEqualTo(10);
    }

    private static Coordinates farAway() {
        return new Coordinates(1000 + counter.incrementAndGet(), 1000);
    }
}
