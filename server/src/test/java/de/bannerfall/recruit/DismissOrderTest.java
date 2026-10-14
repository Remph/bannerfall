package de.bannerfall.recruit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DismissOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    /** Five spearmen, 20 s each. */
    private static DismissOrder order(Instant startAt) {
        return DismissOrder.create(
                7, BuildingType.BARRACKS, UnitType.SPEARMAN, 5, 20, startAt, NOW);
    }

    @Test
    void theOrderEndsWhenItsLastUnitIsGone() {
        DismissOrder order = order(NOW.plusSeconds(10));

        assertThat(order.getStartAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(order.getEndAt()).isEqualTo(NOW.plusSeconds(10 + 100));
        assertThat(order.getCompletedCount()).isZero();
        assertThat(order.remaining()).isEqualTo(5);
    }

    @Test
    void anOrderNeedsUnitsAndTime() {
        assertThatThrownBy(
                        () ->
                                DismissOrder.create(
                                        7,
                                        BuildingType.BARRACKS,
                                        UnitType.SPEARMAN,
                                        0,
                                        20,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                DismissOrder.create(
                                        7,
                                        BuildingType.BARRACKS,
                                        UnitType.SPEARMAN,
                                        1,
                                        0,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitsGoOneByOneAndNeverComeBack() {
        DismissOrder order = order(NOW);

        order.completeUpTo(NOW.plusSeconds(19));
        assertThat(order.getCompletedCount()).isZero();
        order.completeUpTo(NOW.plusSeconds(45));
        assertThat(order.getCompletedCount()).isEqualTo(2);
        assertThat(order.remaining()).isEqualTo(3);
        // an earlier time changes nothing
        order.completeUpTo(NOW.plusSeconds(5));
        assertThat(order.getCompletedCount()).isEqualTo(2);
        assertThat(order.isComplete()).isFalse();

        order.completeUpTo(NOW.plusSeconds(10_000));
        assertThat(order.getCompletedCount()).isEqualTo(5);
        assertThat(order.isComplete()).isTrue();
        assertThat(order.remaining()).isZero();
    }

    @Test
    void onlyAnOrderThatHasNotStartedCanMove() {
        DismissOrder waiting = order(NOW.plusSeconds(100));
        waiting.moveTo(NOW.plusSeconds(40));
        assertThat(waiting.getStartAt()).isEqualTo(NOW.plusSeconds(40));
        assertThat(waiting.getEndAt()).isEqualTo(NOW.plusSeconds(140));

        DismissOrder running = order(NOW);
        running.completeUpTo(NOW.plusSeconds(25));
        assertThatThrownBy(() -> running.moveTo(NOW.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }
}
