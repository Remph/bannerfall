package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The court of a lord guild (2.1 S2b, 12-solo-and-allies §6). */
class CourtNamesTest {

    @Test
    void everyLordGuildNameHasACourt() {
        for (NpcGuildForming.Name name : NpcGuildForming.NAMES) {
            assertThat(CourtNames.of(name.name())).as(name.name()).isPresent();
        }
        assertThat(CourtNames.KEYS).hasSameSizeAs(NpcGuildForming.NAMES);
    }

    @Test
    void theNumeralOfALaterRoundStaysWithTheCourt() {
        assertThat(CourtNames.of("Ordo Corvi")).contains(new CourtNames.Court("RAVEN", null));
        assertThat(CourtNames.of("Ordo Ursi II")).contains(new CourtNames.Court("BEAR", "II"));
        assertThat(CourtNames.of("Ordo Lupi XIV")).contains(new CourtNames.Court("WOLF", "XIV"));
    }

    @Test
    void aNameOffTheListHasNoCourtKey() {
        assertThat(CourtNames.of("Ordo Testium")).isEmpty();
        assertThat(CourtNames.of("The Lords")).isEmpty();
        assertThat(CourtNames.of(null)).isEmpty();
    }
}
