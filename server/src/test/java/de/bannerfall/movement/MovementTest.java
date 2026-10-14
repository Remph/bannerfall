package de.bannerfall.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.Village;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cancel window and cancel of a movement (02-game-rules §6), without database. */
class MovementTest {

    private static final Instant DEPART = Instant.parse("2026-10-04T12:00:00Z");
    private static final int WINDOW = 600;

    private final Village home = village(10, 10);
    private final Village target = village(20, 20);

    @Test
    void theWindowEndsAfterTheCancelWindowOrAtTheArrival() {
        assertThat(attackArriving(3600).cancellableUntil(WINDOW))
                .isEqualTo(DEPART.plusSeconds(WINDOW));
        assertThat(attackArriving(300).cancellableUntil(WINDOW)).isEqualTo(DEPART.plusSeconds(300));
    }

    @Test
    void cancellableOnlyBeforeTheEndOfTheWindow() {
        Movement attack = attackArriving(3600);

        assertThat(attack.isCancellableAt(DEPART, WINDOW)).isTrue();
        assertThat(attack.isCancellableAt(DEPART.plusSeconds(WINDOW - 1), WINDOW)).isTrue();
        assertThat(attack.isCancellableAt(DEPART.plusSeconds(WINDOW), WINDOW)).isFalse();
    }

    @Test
    void aReturnIsNeverCancellable() {
        Movement back =
                Movement.returning(
                        home,
                        target,
                        1,
                        units(),
                        ResourceAmounts.ZERO,
                        DEPART,
                        DEPART.plusSeconds(3600),
                        DEPART);

        assertThat(back.cancellableUntil(WINDOW)).isNull();
        assertThat(back.isCancellableAt(DEPART, WINDOW)).isFalse();
    }

    @Test
    void aCancelledCommandReturnsAfterTheTimeItWasOnTheWay() {
        Movement attack =
                Movement.command(
                        MovementKind.ATTACK,
                        home,
                        target,
                        1,
                        units(),
                        BuildingType.FARM,
                        DEPART,
                        DEPART.plusSeconds(3600));
        Instant now = DEPART.plusSeconds(100);

        attack.cancel(now, WINDOW);

        assertThat(attack.getKind()).isEqualTo(MovementKind.RETURN);
        assertThat(attack.getHomeVillage()).isSameAs(home);
        assertThat(attack.getSourceVillage()).isSameAs(target);
        assertThat(attack.getTargetVillage()).isSameAs(home);
        assertThat(attack.getCatapultTarget()).isNull();
        assertThat(attack.getDepartAt()).isEqualTo(now);
        assertThat(attack.getArriveAt()).isEqualTo(DEPART.plusSeconds(200));
        assertThat(attack.getUnits().get(UnitType.AXEMAN)).isEqualTo(10);
    }

    @Test
    void aCancelAfterTheWindowIsABug() {
        Movement attack = attackArriving(3600);

        assertThatThrownBy(() -> attack.cancel(DEPART.plusSeconds(WINDOW), WINDOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReturnIsNoCommand() {
        assertThatThrownBy(
                        () ->
                                Movement.command(
                                        MovementKind.RETURN,
                                        home,
                                        target,
                                        1,
                                        units(),
                                        null,
                                        DEPART,
                                        DEPART.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Movement attackArriving(long afterSeconds) {
        return Movement.command(
                MovementKind.ATTACK,
                home,
                target,
                1,
                units(),
                null,
                DEPART,
                DEPART.plusSeconds(afterSeconds));
    }

    private static UnitCounts units() {
        return UnitCounts.of(Map.of(UnitType.AXEMAN, 10));
    }

    private static Village village(int x, int y) {
        return new Village(null, null, new Coordinates(x, y), 0, new BuildingLevels(), 0, DEPART);
    }
}
