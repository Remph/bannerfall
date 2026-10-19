package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What stays open in the pre-game and in an ended world (M14g, v1.3). */
class PregameGateFilterTest {

    @Test
    void linkingTheAccountIsAnAccountActionLikeThePassword() {
        assertThat(PregameGateFilter.isClosed("/api/me/link")).isFalse();
        assertThat(PregameGateFilter.isOpenWhenEnded("/api/me/link")).isTrue();
    }

    @Test
    void theAccountServerCanEndSessionsOnAnEndedWorld() {
        // a ban or deletion there must still log the player out here (1.5)
        assertThat(PregameGateFilter.isOpenWhenEnded("/api/auth/backchannel-logout")).isTrue();
        assertThat(PregameGateFilter.isClosed("/api/auth/backchannel-logout")).isFalse();
    }

    @Test
    void joiningStaysClosedOnAnEndedWorldLikeRegistering() {
        assertThat(PregameGateFilter.isOpenWhenEnded("/api/auth/register")).isFalse();
        assertThat(PregameGateFilter.isOpenWhenEnded("/api/auth/join")).isFalse();
    }
}
