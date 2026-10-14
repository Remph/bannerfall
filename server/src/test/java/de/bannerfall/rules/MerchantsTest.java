package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.world.WorldSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantsTest {

    @ParameterizedTest(name = "market {0} → {1} merchants")
    @CsvSource({"0, 0", "1, 1", "10, 10", "11, 11", "12, 14", "15, 35", "25, 235"})
    void merchantCountMatchesTheRulebook(int marketLevel, int expected) {
        assertThat(Merchants.total(marketLevel)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} resources need {1} merchants")
    @CsvSource({"0, 0", "1, 1", "999, 1", "1000, 1", "1001, 2", "2500, 3", "2147483647, 2147484"})
    void merchantsNeededRoundUp(int totalResources, int expected) {
        assertThat(Merchants.needed(totalResources)).isEqualTo(expected);
    }

    @Test
    void negativeInputIsRejected() {
        assertThatThrownBy(() -> Merchants.total(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Merchants.needed(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void merchantsWalkSixMinutesPerField() {
        // 13 fields × 6 min = 78 min = 4680 s; world speed 3 divides it.
        assertThat(Merchants.travelSeconds(13, WorldSettings.defaults())).isEqualTo(4680);
        assertThat(Merchants.travelSeconds(13, WorldSettings.defaults().withSpeed(3)))
                .isEqualTo(1560);
    }

    @ParameterizedTest(name = "offer {0} × {1} reserves {2} merchants")
    @CsvSource({"1, 1, 1", "1000, 1, 1", "1001, 1, 2", "1001, 3, 6", "500, 10, 10", "5, 0, 0"})
    void anOfferReservesMerchantsPerUnit(int offerAmount, int count, int expected) {
        assertThat(Merchants.forOffer(offerAmount, count)).isEqualTo(expected);
    }

    @Test
    void freeMerchantsNeverGoBelowZero() {
        assertThat(Merchants.free(10, 3)).isEqualTo(7);
        assertThat(Merchants.free(10, 10)).isZero();
        // the market was shot down while merchants were away
        assertThat(Merchants.free(5, 8)).isZero();
    }

    @ParameterizedTest(name = "{0} for {1} at max ratio 3 → {2}")
    @CsvSource({
        "1000, 1000, true",
        "1000, 3000, true",
        "1000, 3001, false",
        "3000, 1000, true",
        "3001, 1000, false",
        "1, 3, true",
        "1, 4, false"
    })
    void theRatioIsLimitedBothWays(int offerAmount, int wantAmount, boolean allowed) {
        assertThat(Merchants.ratioAllowed(offerAmount, wantAmount, 3.0)).isEqualTo(allowed);
    }

    @Test
    void ratioNeedsPositiveAmounts() {
        assertThatThrownBy(() -> Merchants.ratioAllowed(0, 1, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Merchants.forOffer(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
