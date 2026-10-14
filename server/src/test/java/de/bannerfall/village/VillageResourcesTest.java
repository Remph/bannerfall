package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.ResourceAmounts;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Paying, refunding, receiving and the whole-number view of the stock (02-game-rules §3, §4.3). */
class VillageResourcesTest {

    private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

    private static Village village() {
        return new Village(null, "Test", new Coordinates(50, 50), 500, new BuildingLevels(), 0, T0);
    }

    @Test
    void theStockIsSeenRoundedDown() {
        Village village = village();
        village.setResources(62.999, 63.0, 0.4, T0);

        assertThat(village.floorResources()).isEqualTo(new ResourceAmounts(62, 63, 0));
    }

    @Test
    void anAffordableCostFitsIntoTheRoundedDownStock() {
        Village village = village();
        village.setResources(62.999, 100, 100, T0);

        assertThat(new ResourceAmounts(63, 1, 1).fitsInto(village.floorResources())).isFalse();
        assertThat(new ResourceAmounts(62, 1, 1).fitsInto(village.floorResources())).isTrue();
    }

    @Test
    void spendingTakesTheCostAndKeepsTheAnchor() {
        Village village = village();
        village.setResources(100.5, 200.25, 300, T0);

        village.spend(new ResourceAmounts(100, 200, 300));

        assertThat(village.getWood()).isCloseTo(0.5, within(1e-9));
        assertThat(village.getClay()).isCloseTo(0.25, within(1e-9));
        assertThat(village.getIron()).isCloseTo(0, within(1e-9));
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void addingIgnoresTheWarehouseCapacity() {
        Village village = village(); // no warehouse at all: capacity is not looked at
        village.setResources(900.5, 0, 0, T0);

        village.addResources(new ResourceAmounts(5000, 1, 0));

        assertThat(village.getWood()).isCloseTo(5900.5, within(1e-9));
        assertThat(village.getClay()).isCloseTo(1, within(1e-9));
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void receivedResourcesFillUpToTheCapacityAndTheRestIsLost() {
        Village village = village();
        village.setResources(950.5, 400, 1200, T0); // iron is already above the capacity

        village.receiveResources(new ResourceAmounts(63, 63, 63), 1000);

        assertThat(village.getWood()).isCloseTo(1000, within(1e-9)); // 13.5 lost
        assertThat(village.getClay()).isCloseTo(463, within(1e-9)); // fits completely
        assertThat(village.getIron()).isCloseTo(1200, within(1e-9)); // stays, nothing added
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T0);
    }
}
