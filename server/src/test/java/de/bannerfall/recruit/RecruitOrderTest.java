package de.bannerfall.recruit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecruitOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final ResourceAmounts SPEARMAN_COST = new ResourceAmounts(50, 30, 10);

    /** Five spearmen, 100 s each. */
    private static RecruitOrder order(Instant startAt) {
        return RecruitOrder.create(
                7,
                BuildingType.BARRACKS,
                UnitType.SPEARMAN,
                5,
                100,
                startAt,
                SPEARMAN_COST,
                1,
                NOW);
    }

    @Test
    void theOrderEndsWhenItsLastUnitIsDone() {
        RecruitOrder order = order(NOW.plusSeconds(30));

        assertThat(order.getStartAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(order.getEndAt()).isEqualTo(NOW.plusSeconds(30 + 500));
        assertThat(order.getVillageId()).isEqualTo(7);
        assertThat(order.getBuilding()).isEqualTo(BuildingType.BARRACKS);
        assertThat(order.getUnit()).isEqualTo(UnitType.SPEARMAN);
        assertThat(order.getCount()).isEqualTo(5);
        assertThat(order.getCompletedCount()).isZero();
        assertThat(order.getSecondsPerUnit()).isEqualTo(100);
        assertThat(order.unitCost()).isEqualTo(SPEARMAN_COST);
        assertThat(order.getUnitPop()).isEqualTo(1);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void anOrderNeedsUnitsAndTime() {
        assertThatThrownBy(
                        () ->
                                RecruitOrder.create(
                                        7,
                                        BuildingType.BARRACKS,
                                        UnitType.SPEARMAN,
                                        0,
                                        100,
                                        NOW,
                                        SPEARMAN_COST,
                                        1,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                RecruitOrder.create(
                                        7,
                                        BuildingType.BARRACKS,
                                        UnitType.SPEARMAN,
                                        5,
                                        0,
                                        NOW,
                                        SPEARMAN_COST,
                                        1,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitsAreCompletedOneByOneAndOnlyOnce() {
        RecruitOrder order = order(NOW);

        assertThat(order.completeUpTo(NOW.plusSeconds(99))).isZero();
        assertThat(order.completeUpTo(NOW.plusSeconds(100))).isEqualTo(1);
        assertThat(order.completeUpTo(NOW.plusSeconds(100))).isZero(); // already counted
        assertThat(order.completeUpTo(NOW.plusSeconds(350))).isEqualTo(2);
        assertThat(order.getCompletedCount()).isEqualTo(3);
        assertThat(order.remaining()).isEqualTo(2);
        assertThat(order.isComplete()).isFalse();

        assertThat(order.completeUpTo(NOW.plusSeconds(10_000))).isEqualTo(2);
        assertThat(order.getCompletedCount()).isEqualTo(5);
        assertThat(order.remaining()).isZero();
        assertThat(order.isComplete()).isTrue();
    }

    @Test
    void anEarlierTimeChangesNothing() {
        RecruitOrder order = order(NOW);
        order.completeUpTo(NOW.plusSeconds(300));

        // e.g. the event worker catches the village up to an event that was due earlier
        assertThat(order.completeUpTo(NOW.plusSeconds(150))).isZero();
        assertThat(order.getCompletedCount()).isEqualTo(3);
    }

    @Test
    void theRefundIsTheCostOfTheUnitsStillInTraining() {
        RecruitOrder order = order(NOW);
        assertThat(order.refund()).isEqualTo(new ResourceAmounts(250, 150, 50));

        order.completeUpTo(NOW.plusSeconds(200));

        assertThat(order.refund()).isEqualTo(new ResourceAmounts(150, 90, 30));
    }

    @Test
    void movingAnOrderKeepsItsDuration() {
        RecruitOrder order = order(NOW.plusSeconds(3600));

        order.moveTo(NOW.plusSeconds(60));

        assertThat(order.getStartAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(order.getEndAt()).isEqualTo(NOW.plusSeconds(60 + 500));
    }

    @Test
    void anOrderWithUnitsDoneCanNotBeMoved() {
        RecruitOrder order = order(NOW);
        order.completeUpTo(NOW.plusSeconds(100));

        assertThatThrownBy(() -> order.moveTo(NOW.plusSeconds(50)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStartAt()).isEqualTo(NOW);
    }

    @Test
    void finishingNowCompletesTheRest() {
        RecruitOrder order = order(NOW);
        order.completeUpTo(NOW.plusSeconds(100));

        assertThat(order.finishNow()).isEqualTo(4);
        assertThat(order.isComplete()).isTrue();
        assertThat(order.remaining()).isZero();
    }
}
