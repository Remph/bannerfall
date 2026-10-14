package de.bannerfall;

import de.bannerfall.world.WorldService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The shared test world spawns no barbarian villages around new players: a full run registers about
 * 200 players, and three barbarians each would fill the map ({@code WORLD_FULL}) and put villages
 * next to every test player; it has no NPC lords either ({@code npc_lord_count = 0}). Tests of that
 * rule set the value themselves and put it back ({@link #BARBARIANS_PER_NEW_PLAYER}). The initial
 * barbarians aren't spawned either ({@code WorldInitializer} is switched off in {@link
 * IntegrationTest}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestWorldConfiguration {

    /**
     * {@code map_size} in the test database. A full run registers about 400 players around the
     * centre, which is all a 100 × 100 map holds (WORLD_FULL when the suite grew, M14g). The
     * villages the tests place by hand sit near the corners and edges ({@code MapControllerTest},
     * {@code HostConsoleTest}).
     */
    public static final int MAP_SIZE = 150;

    /** {@code barbarians_per_new_player} in the test database. */
    public static final int BARBARIANS_PER_NEW_PLAYER = 0;

    /**
     * Creating it adjusts the settings row once per context.
     *
     * @param seededBarbariansPerNewPlayer what the migration wrote, for {@code WorldServiceTest}
     */
    public record TestWorld(int seededBarbariansPerNewPlayer) {}

    @Bean
    TestWorld testWorld(JdbcClient jdbc, WorldService worldService) {
        int seeded =
                jdbc.sql("select barbarians_per_new_player from world_settings where id = 1")
                        .query(Integer.class)
                        .single();
        jdbc.sql(
                        "update world_settings set barbarians_per_new_player = ?, npc_lord_count ="
                                + " 0, map_size = ? where id = 1")
                .param(BARBARIANS_PER_NEW_PLAYER)
                .param(MAP_SIZE)
                .update();
        worldService.reload();
        return new TestWorld(seeded);
    }
}
