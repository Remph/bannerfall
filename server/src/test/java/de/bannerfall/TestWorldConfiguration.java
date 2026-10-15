package de.bannerfall;

import de.bannerfall.world.WorldService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The shared test world spawns no barbarian villages around new players: a full run registers over
 * 1,000 players, and three barbarians each would fill the map ({@code WORLD_FULL}) and put villages
 * next to every test player; it has no NPC lords either ({@code npc_lord_count = 0}). Tests of that
 * rule set the value themselves and put it back ({@link #BARBARIANS_PER_NEW_PLAYER}). The initial
 * barbarians aren't spawned either ({@code WorldInitializer} is switched off in {@link
 * IntegrationTest}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestWorldConfiguration {

    /**
     * {@code map_size} in the test database. The spawn ring has the radius {@code 8 + 2.5 ×
     * sqrt(player villages)}, so a map of size s keeps new players inside up to about {@code ((s/2
     * − 8) / 2.5)²} player villages; beyond that only the corners are left, and a registration
     * fails now and then with WORLD_FULL in whatever test happens to come then. A full run has
     * about 1,050 player villages (October 2026): 100 × 100 ran full at about 400 (M14g), 150 × 150
     * at about 720 (1.2.2). 300 × 300 keeps the ring inside up to about 3,200. The villages the
     * tests place by hand sit near the corners and edges ({@code MapControllerTest}, {@code
     * HostConsoleTest}).
     */
    public static final int MAP_SIZE = 300;

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
