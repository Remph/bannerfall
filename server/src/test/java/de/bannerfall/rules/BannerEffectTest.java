package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.BannerType;
import org.junit.jupiter.api.Test;

/** The village banner and the guild banner add up where they touch the same number (§16.4). */
class BannerEffectTest {

    private static final GuildEffect GUILD = new GuildEffect(0.05, 0.10, 0.05);

    @Test
    void harvestAndTheGuildProductionAddUp() {
        BannerEffect harvest = new BannerEffect(BannerType.HARVEST, 3, 3, 0.15).withGuild(GUILD);

        assertThat(harvest.production())
                .isCloseTo(0.20, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(harvest.harvest()).isEqualTo(0.15); // the banner's own number stays pure
    }

    @Test
    void bulwarkAndTheGuildDefenceAddUp() {
        BannerEffect bulwark = new BannerEffect(BannerType.BULWARK, 3, 3, 0.10).withGuild(GUILD);

        assertThat(bulwark.defence()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(bulwark.bulwark()).isEqualTo(0.10);
    }

    @Test
    void aVillageWithoutABannerStillGetsTheGuildsNumbers() {
        BannerEffect only = BannerEffect.NONE.withGuild(GUILD);

        assertThat(only.production()).isEqualTo(0.05);
        assertThat(only.defence()).isEqualTo(0.05);
        assertThat(only.harvest()).isZero();
        assertThat(BannerEffect.NONE.production()).isZero();
    }
}
