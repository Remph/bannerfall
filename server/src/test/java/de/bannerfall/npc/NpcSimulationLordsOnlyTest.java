package de.bannerfall.npc;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The NPC lords alone (09 §11). A class of its own, so that {@code simulationTest} runs it in a
 * second JVM (with a database of its own) next to the other scenario. See {@link
 * NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationLordsOnlyTest extends NpcSimulationTest {

    @Test
    void npcLordsOnly() throws Exception {
        run("NPC lords only", 0);
    }
}
