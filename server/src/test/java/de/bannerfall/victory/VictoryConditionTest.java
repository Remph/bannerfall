package de.bannerfall.victory;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.world.VictoryMode;
import de.bannerfall.world.WorldSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Who meets the victory condition (02-game-rules §22), from the standings alone. */
class VictoryConditionTest {

    private static final VictorySide GUILD =
            new VictorySide(SideKind.GUILD, 1, "Iron Wolves", "IW", false, 30, 40_000, 2);
    private static final VictorySide LONER =
            new VictorySide(SideKind.PLAYER, 7, "loner", null, false, 12, 50_000, 1);
    private static final VictorySide LORD =
            new VictorySide(SideKind.PLAYER, 9, "Lord Aldric", null, true, 5, 9_000, 0);

    private static WorldSettings world(VictoryMode mode, int dominationPercent, int keepPercent) {
        return WorldSettings.builder()
                .victoryMode(mode)
                .dominationPercent(dominationPercent)
                .keepPercent(keepPercent)
                .build();
    }

    private static Standings standings(int allVillages, int allKeeps, VictorySide... sides) {
        return new Standings(
                List.of(sides).stream().sorted(Standings.BY_POINTS).toList(),
                allVillages,
                allKeeps);
    }

    @Test
    void theSideWithMostVillagesDominatesWithItsShareOfAllVillages() {
        // 30 of 50 villages, barbarians included, is 60 %
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.DOMINATION, 60, 51),
                                standings(50, 3, GUILD, LONER, LORD)))
                .contains(GUILD);
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.DOMINATION, 60, 51),
                                standings(51, 3, GUILD, LONER, LORD)))
                .isEmpty();
    }

    @Test
    void aPlayerWithoutGuildCanDominateToo() {
        var big = new VictorySide(SideKind.PLAYER, 7, "loner", null, false, 31, 50_000, 0);
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.DOMINATION, 60, 51), standings(50, 0, big, LORD)))
                .contains(big);
    }

    @Test
    void twoSidesSharingTheTopMeetNothing() {
        var twin = new VictorySide(SideKind.PLAYER, 8, "twin", null, false, 30, 1, 0);
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.DOMINATION, 10, 51),
                                standings(60, 0, GUILD, twin)))
                .isEmpty();
    }

    @Test
    void keepsCountTheShareOfTheKeepsRoundedUp() {
        // 2 of 3 keeps: 51 % of 3 needs 2
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.KEEPS, 60, 51),
                                standings(100, 3, GUILD, LONER, LORD)))
                .contains(GUILD);
        // 99 % of 3 needs all 3
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.KEEPS, 60, 99),
                                standings(100, 3, GUILD, LONER, LORD)))
                .isEmpty();
        // no keeps on the map: nobody
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.KEEPS, 60, 51), standings(100, 0, LORD)))
                .isEmpty();
    }

    @Test
    void withoutAConditionNobodyWinsEarly() {
        assertThat(
                        VictoryService.meetsCondition(
                                world(VictoryMode.NONE, 10, 1), standings(31, 3, GUILD)))
                .isEmpty();
    }

    @Test
    void theEndDateGoesToTheMostPoints() {
        assertThat(standings(100, 3, GUILD, LONER, LORD).best()).contains(LONER);
        assertThat(standings(100, 3).best()).isEmpty();
    }
}
