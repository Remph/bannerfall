package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductionTest {

    private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

    // 02-game-rules §3 reference table (speed 1)
    @ParameterizedTest(name = "level {0} → {1}/h")
    @CsvSource({"0, 5.00", "1, 30.00", "5, 55.26", "10, 118.59", "20, 546.15", "30, 2515.20"})
    void perHourMatchesTheRulebook(int level, double expected) {
        assertThat(Production.perHour(level, WorldSettings.defaults()))
                .isCloseTo(expected, within(0.005));
    }

    @Test
    void speedAndOwnerFactorScaleEveryLevelIncludingZero() {
        assertThat(Production.perHour(1, 3.0, 1.0)).isCloseTo(90.0, within(1e-9));
        assertThat(Production.perHour(0, 3.0, 1.0)).isCloseTo(15.0, within(1e-9));
        assertThat(Production.perHour(1, 1.0, 0.5)).isCloseTo(15.0, within(1e-9));
        assertThat(Production.perHour(1, WorldSettings.defaults().withSpeed(3)))
                .isCloseTo(90.0, within(1e-9));
    }

    @Test
    void negativeLevelIsRejected() {
        assertThatThrownBy(() -> Production.perHour(-1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advanceAddsProductionOverTime() {
        assertThat(Production.advance(100, 30, 1000, T0, T0.plusSeconds(2 * 3600)))
                .isCloseTo(160.0, within(1e-9));
        assertThat(Production.advance(100, 30, 1000, T0, T0.plusSeconds(90 * 60)))
                .isCloseTo(145.0, within(1e-9));
    }

    @Test
    void advanceStopsAtCapacity() {
        assertThat(Production.advance(990, 30, 1000, T0, T0.plusSeconds(2 * 3600)))
                .isEqualTo(1000.0);
    }

    @Test
    void advanceNeverReducesStockAboveCapacity() {
        assertThat(Production.advance(1500, 30, 1000, T0, T0.plusSeconds(3600))).isEqualTo(1500.0);
        assertThat(Production.advance(1000, 30, 1000, T0, T0.plusSeconds(3600))).isEqualTo(1000.0);
    }

    @Test
    void advanceIntoThePastProducesNothing() {
        assertThat(Production.advance(100, 30, 1000, T0, T0.minusSeconds(3600))).isEqualTo(100.0);
    }

    @Test
    void anchorNeverMovesBackwards() {
        assertThat(Production.newAnchor(T0, T0.plusSeconds(60))).isEqualTo(T0.plusSeconds(60));
        assertThat(Production.newAnchor(T0, T0.minusSeconds(60))).isEqualTo(T0);
    }

    @Test
    void incomingResourcesFillUpToCapacityAndTheRestIsLost() {
        assertThat(Production.addIncoming(100, 50, 1000)).isEqualTo(150.0);
        assertThat(Production.addIncoming(900, 200, 1000)).isEqualTo(1000.0);
    }

    @Test
    void incomingResourcesNeverReduceStockAboveCapacity() {
        assertThat(Production.addIncoming(1200, 50, 1000)).isEqualTo(1200.0);
    }
}
