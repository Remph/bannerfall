package de.bannerfall.npc;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The NPC lords next to scripted human players who attack (09 §11, {@link SimulatedHumans}), with
 * archers and learning lords switched on: do the lords learn from humans' attacks and wins, do they
 * recruit archers, do the caps hold against players who really play. A class of its own, so that
 * {@code simulationTest} runs it in a JVM and a database of its own. See {@link NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationActiveHumansTest extends NpcSimulationTest {

    @Override
    boolean activeHumans() {
        return true;
    }

    @Test
    void withActiveHumans() throws Exception {
        run("active humans", 0);
    }
}
