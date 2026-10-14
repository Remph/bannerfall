package de.bannerfall.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BuildOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private static BuildOrder order(Instant startAt) {
        return BuildOrder.create(
                7,
                BuildingType.WOODCUTTER,
                2,
                1011,
                startAt,
                new ResourceAmounts(63, 75, 50),
                1,
                NOW);
    }

    @Test
    void theOrderEndsOneDurationAfterItsStart() {
        BuildOrder order = order(NOW.plusSeconds(100));

        assertThat(order.getStartAt()).isEqualTo(NOW.plusSeconds(100));
        assertThat(order.getEndAt()).isEqualTo(NOW.plusSeconds(100 + 1011));
        assertThat(order.getDurationSeconds()).isEqualTo(1011);
        assertThat(order.getVillageId()).isEqualTo(7);
        assertThat(order.getBuilding()).isEqualTo(BuildingType.WOODCUTTER);
        assertThat(order.getTargetLevel()).isEqualTo(2);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void whatWasPaidIsKeptForTheRefund() {
        BuildOrder order = order(NOW);

        assertThat(order.cost()).isEqualTo(new ResourceAmounts(63, 75, 50));
        assertThat(order.getCostPop()).isEqualTo(1);
    }

    @Test
    void movingTheOrderKeepsItsDuration() {
        BuildOrder order = order(NOW.plusSeconds(3600));

        order.moveTo(NOW.plusSeconds(60));

        assertThat(order.getStartAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(order.getEndAt()).isEqualTo(NOW.plusSeconds(60 + 1011));
        assertThat(order.getDurationSeconds()).isEqualTo(1011);
    }

    @Test
    void finishingARunningOrderEndsItNow() {
        BuildOrder order = order(NOW.minusSeconds(100));

        order.finishNow(NOW);

        assertThat(order.getStartAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(order.getEndAt()).isEqualTo(NOW);
    }

    @Test
    void finishingAWaitingOrderLetsItStartAndEndNow() {
        BuildOrder order = order(NOW.plusSeconds(500));

        order.finishNow(NOW);

        assertThat(order.getStartAt()).isEqualTo(NOW);
        assertThat(order.getEndAt()).isEqualTo(NOW);
    }

    @Test
    void levelsLostInABattleLowerTheTargetLevelButNotTheCostOrTime() {
        BuildOrder order = order(NOW); // woodcutter level 2

        order.lowerTargetLevel(1);

        assertThat(order.getTargetLevel()).isEqualTo(1);
        assertThat(order.cost()).isEqualTo(new ResourceAmounts(63, 75, 50));
        assertThat(order.getDurationSeconds()).isEqualTo(1011);
    }

    @Test
    void theTargetLevelStaysAtLeastOne() {
        BuildOrder order = order(NOW); // woodcutter level 2

        assertThatThrownBy(() -> order.lowerTargetLevel(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.lowerTargetLevel(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getTargetLevel()).isEqualTo(2);
    }
}
