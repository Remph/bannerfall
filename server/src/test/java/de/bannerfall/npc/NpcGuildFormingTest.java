package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.common.Coordinates;
import de.bannerfall.npc.NpcGuildForming.Found;
import de.bannerfall.npc.NpcGuildForming.Join;
import de.bannerfall.npc.NpcGuildForming.Lord;
import de.bannerfall.npc.NpcGuildForming.Name;
import de.bannerfall.npc.NpcGuildForming.Stay;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Who takes part in a lord guild, whom it joins or founds one with, and the names (09 §13.1). */
class NpcGuildFormingTest {

    private static final double RADIUS = 15;
    private static final int MAX = 3;

    @Test
    void aLordTakesPartFromItsMidStageWithoutAGuildAndUnplaced() {
        assertThat(lord(1, 850, 0, 0).takesPart()).isTrue();
        assertThat(lord(1, 849, 0, 0).takesPart()).isFalse();
        assertThat(inGuild(lord(1, 900, 0, 0), 7, 2).takesPart()).isFalse();
        assertThat(placed(lord(1, 900, 0, 0)).takesPart()).isFalse();
        assertThat(new Lord(1, 900, null, 0, false, 850, List.of()).takesPart()).isFalse();
    }

    @Test
    void twoNeighboursThatTakePartFoundAGuildAndNeverAlone() {
        Lord me = lord(1, 900, 0, 0);

        assertThat(NpcGuildForming.choose(me, List.of(me, lord(2, 900, 15, 0)), RADIUS, MAX))
                .isEqualTo(new Found(2));
        // just outside the radius, or nobody near: no guild of one
        assertThat(NpcGuildForming.choose(me, List.of(me, lord(2, 900, 16, 0)), RADIUS, MAX))
                .isEqualTo(new Stay());
        assertThat(NpcGuildForming.choose(me, List.of(me), RADIUS, MAX)).isEqualTo(new Stay());
        // a neighbour below MID points doesn't found yet
        assertThat(NpcGuildForming.choose(me, List.of(me, lord(2, 500, 5, 0)), RADIUS, MAX))
                .isEqualTo(new Stay());
    }

    @Test
    void theNearestGuildWithRoomComesBeforeFounding() {
        Lord me = lord(1, 900, 0, 0);
        Lord free = lord(2, 900, 3, 0);
        Lord member = inGuild(lord(3, 900, 10, 0), 7, 2);
        Lord full = inGuild(lord(4, 900, 1, 0), 8, MAX);

        assertThat(NpcGuildForming.choose(me, List.of(me, free, member, full), RADIUS, MAX))
                .isEqualTo(new Join(7));
        assertThat(NpcGuildForming.choose(me, List.of(me, free, full), RADIUS, MAX))
                .isEqualTo(new Found(2));
    }

    @Test
    void aPlacedLordIsLeftAloneButItsGuildTakesOthers() {
        Lord me = lord(1, 900, 0, 0);
        Lord placedMember = placed(inGuild(lord(2, 900, 4, 0), 7, 1));
        Lord placedAlone = placed(lord(3, 900, 2, 0));

        assertThat(NpcGuildForming.choose(placed(me), List.of(me, lord(4, 900, 1, 0)), RADIUS, MAX))
                .isEqualTo(new Stay());
        // a placed lord without a guild doesn't found one with me; its neighbour in a guild does
        assertThat(NpcGuildForming.choose(me, List.of(me, placedAlone), RADIUS, MAX))
                .isEqualTo(new Stay());
        assertThat(NpcGuildForming.choose(me, List.of(me, placedAlone, placedMember), RADIUS, MAX))
                .isEqualTo(new Join(7));
    }

    @Test
    void distanceIsBetweenTheClosestVillagesAndTiesGoToTheLowerId() {
        Lord me =
                new Lord(
                        5,
                        900,
                        null,
                        0,
                        false,
                        850,
                        List.of(new Coordinates(0, 0), new Coordinates(40, 0)));
        Lord far = lord(9, 900, 52, 0); // 12 from my second village
        Lord tieHigh = lord(8, 900, 0, 12);
        Lord tieLow = lord(7, 900, 0, -12);

        assertThat(NpcGuildForming.distance(me, far)).isEqualTo(12);
        assertThat(NpcGuildForming.choose(me, List.of(far, tieHigh, tieLow, me), RADIUS, MAX))
                .isEqualTo(new Found(7));
    }

    @Test
    void namesComeFromTheListIgnoringCaseThenNumbered() {
        Set<String> names = new HashSet<>();
        Set<String> tags = new HashSet<>();

        assertThat(NpcGuildForming.nextName(names, tags))
                .isEqualTo(new Name("Ordo Corvi", "CORVI"));

        // a human guild with the tag takes the entry too
        tags.add("corvi");
        assertThat(NpcGuildForming.nextName(names, tags)).isEqualTo(new Name("Ordo Leonis", "LEO"));

        for (Name name : NpcGuildForming.NAMES) {
            names.add(name.name().toLowerCase(Locale.ROOT));
            tags.add(name.tag().toLowerCase(Locale.ROOT));
        }
        assertThat(NpcGuildForming.nextName(names, tags))
                .isEqualTo(new Name("Ordo Corvi II", "CORV2"));
        names.add("ordo corvi ii");
        assertThat(NpcGuildForming.nextName(names, tags))
                .isEqualTo(new Name("Ordo Leonis II", "LEO2"));
        assertThat(NpcGuildForming.roman(14)).isEqualTo("XIV");
        assertThat(NpcGuildForming.NAMES).allMatch(n -> n.tag().length() <= 5);
    }

    private static Lord lord(long id, int points, int x, int y) {
        return new Lord(id, points, null, 0, false, 850, List.of(new Coordinates(x, y)));
    }

    private static Lord inGuild(Lord l, long guildId, int size) {
        return new Lord(
                l.id(), l.points(), guildId, size, l.byAdmin(), l.midPoints(), l.villages());
    }

    private static Lord placed(Lord l) {
        return new Lord(
                l.id(), l.points(), l.guildId(), l.guildSize(), true, l.midPoints(), l.villages());
    }
}
