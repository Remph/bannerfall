package de.bannerfall.npc;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The parked check of 2.1 S1 (12-solo-and-allies §10): a world of the setup wizard's Solo preset
 * (speed 4, eight lords in lord guilds of three, domination 60 % held 48 hours, solo mode: no quiet
 * hours) with one human who never plays. The lord guilds must not win by domination in the first
 * weeks. The simulation runs game time from event to event, so it is what a solo world lives
 * through while its player is awake, without the sleep. A class of its own for a JVM of its own,
 * see {@link NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationSoloTest extends NpcSimulationTest {

    @Override
    void scenarioSettings() {
        jdbc.sql(
                        """
                        update world_settings set speed = 4, npc_lord_count = 8,
                            barbarian_initial_count = 40, guilds_enabled = true,
                            npc_guilds_enabled = true, npc_guild_max_members = 3,
                            victory_mode = 'DOMINATION', domination_percent = 60,
                            victory_hold_hours = 48, night_bonus_enabled = false,
                            solo_mode = true
                        """)
                .update();
    }

    @Test
    void withOneSleepingHumanInASoloWorld() throws Exception {
        run("solo world, one sleeping human", 1);
    }
}
