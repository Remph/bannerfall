package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.ResourceAmounts;
import org.junit.jupiter.api.Test;

/** Lord shipments (09-npc-lords §5.5): what a village ships, what a receiver takes. */
class NpcShipmentsTest {

    private static final int CAPACITY = 10_000;

    // ---- spare: only what would overflow ----

    @Test
    void aResourceAtNinetyPercentShipsDownToSeventyPercent() {
        ResourceAmounts spare =
                NpcShipments.spare(
                        new ResourceAmounts(9_000, 10_000, 8_999), CAPACITY, ResourceAmounts.ZERO);

        // wood at 90 % and clay full ship down to 7,000; iron just below 90 % stays
        assertThat(spare).isEqualTo(new ResourceAmounts(2_000, 3_000, 0));
    }

    @Test
    void theNextBuildStepsPriceStaysWhenItIsAboveSeventyPercent() {
        ResourceAmounts spare =
                NpcShipments.spare(
                        new ResourceAmounts(10_000, 10_000, 10_000),
                        CAPACITY,
                        new ResourceAmounts(8_500, 6_000, 10_000));

        assertThat(spare).isEqualTo(new ResourceAmounts(1_500, 3_000, 0));
    }

    @Test
    void nothingShipsFromAStockBelowNinetyPercent() {
        assertThat(
                        NpcShipments.spare(
                                new ResourceAmounts(8_999, 5_000, 0),
                                CAPACITY,
                                ResourceAmounts.ZERO))
                .isEqualTo(ResourceAmounts.ZERO);
    }

    // ---- room: up to eighty percent ----

    @Test
    void theReceiverIsFilledUpToEightyPercentCountingWhatIsOnTheWay() {
        ResourceAmounts room =
                NpcShipments.room(
                        new ResourceAmounts(1_000, 7_000, 9_000),
                        new ResourceAmounts(2_000, 500, 0),
                        CAPACITY);

        assertThat(room).isEqualTo(new ResourceAmounts(5_000, 500, 0));
    }

    // ---- load: merchants and the smallest trip ----

    @Test
    void theLoadIsTheSmallerOfSpareAndRoom() {
        ResourceAmounts load =
                NpcShipments.load(
                        new ResourceAmounts(3_000, 1_000, 0),
                        new ResourceAmounts(2_000, 4_000, 5_000),
                        10);

        assertThat(load).isEqualTo(new ResourceAmounts(2_000, 1_000, 0));
    }

    @Test
    void tooFewMerchantsScaleEveryResourceDownEvenly() {
        ResourceAmounts load =
                NpcShipments.load(
                        new ResourceAmounts(6_000, 3_000, 3_000),
                        new ResourceAmounts(8_000, 8_000, 8_000),
                        6);

        assertThat(load).isEqualTo(new ResourceAmounts(3_000, 1_500, 1_500));
        assertThat(load.total()).isLessThanOrEqualTo(6_000);
    }

    @Test
    void lessThanOneFullMerchantIsNotWorthATrip() {
        assertThat(
                        NpcShipments.load(
                                new ResourceAmounts(5_000, 5_000, 5_000),
                                new ResourceAmounts(400, 300, 299),
                                10))
                .isEqualTo(ResourceAmounts.ZERO);
        assertThat(
                        NpcShipments.load(
                                new ResourceAmounts(5_000, 5_000, 5_000),
                                new ResourceAmounts(400, 300, 300),
                                10))
                .isEqualTo(new ResourceAmounts(400, 300, 300));
        assertThat(
                        NpcShipments.load(
                                new ResourceAmounts(5_000, 5_000, 5_000),
                                new ResourceAmounts(5_000, 5_000, 5_000),
                                0))
                .isEqualTo(ResourceAmounts.ZERO);
    }
}
