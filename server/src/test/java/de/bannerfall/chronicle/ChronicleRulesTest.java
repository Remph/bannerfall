package de.bannerfall.chronicle;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.chronicle.ChronicleFrames.Frame;
import de.bannerfall.chronicle.ChronicleFrames.Owner;
import de.bannerfall.chronicle.ChronicleRules.Entry;
import de.bannerfall.chronicle.ChronicleRules.Stored;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which moments the chronicle tells (2.1 I1, 02-game-rules §32). */
class ChronicleRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");

    private static final ChroniclePlayer ANNA = new ChroniclePlayer(1, "Anna", false, null, null);
    private static final ChroniclePlayer BORIS = new ChroniclePlayer(2, "Boris", false, null, null);
    private static final ChroniclePlayer LORD = new ChroniclePlayer(3, "Eckhart", true, null, null);

    private final List<Stored> events = new ArrayList<>();

    @Test
    void theFirstConquestOfTheWorldIsAlwaysTold() {
        conquest(1, null, ANNA, false, false);

        List<Entry> picked = ChronicleRules.pick(events, List.of(), false);

        assertThat(picked)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.kind()).isEqualTo(ChronicleKind.CONQUEST);
                            assertThat(e.facts().first()).isTrue();
                        });
    }

    @Test
    void keepsLastVillagesAndAConquerorsFirstPlayerVillageAreTurningPoints() {
        conquest(1, null, ANNA, false, false); // the first
        conquest(1, null, ANNA, true, false); // a keep, from barbarians
        conquest(2, LORD, ANNA, false, false); // Anna's first village taken from a player
        conquest(2, LORD, ANNA, false, false); // her second: no turning point
        conquest(3, BORIS, ANNA, false, true); // Boris's last village

        List<Entry> picked = ChronicleRules.pick(events, List.of(), false);

        assertThat(picked)
                .extracting(Entry::kind)
                .containsExactly(
                        ChronicleKind.CONQUEST,
                        ChronicleKind.CONQUEST,
                        ChronicleKind.CONQUEST,
                        ChronicleKind.CONQUESTS,
                        ChronicleKind.CONQUEST);
        assertThat(picked.get(1).facts().keep()).isTrue();
        assertThat(picked.get(4).facts().eliminated()).isTrue();
    }

    @Test
    void theOtherConquestsOfADayAreOneLineWithTheBusiestConqueror() {
        conquest(1, null, ANNA, false, false); // the first: told on its own
        conquest(1, null, ANNA, false, false);
        conquest(1, null, BORIS, false, false);
        conquest(1, null, BORIS, false, false);
        conquest(1, null, BORIS, false, false);
        conquest(2, null, ANNA, false, false);

        List<Entry> picked = ChronicleRules.pick(events, List.of(), false);

        assertThat(picked).hasSize(3);
        Entry dayOne = picked.get(1);
        assertThat(dayOne.kind()).isEqualTo(ChronicleKind.CONQUESTS);
        assertThat(dayOne.day()).isEqualTo(1);
        assertThat(dayOne.facts().count()).isEqualTo(4);
        assertThat(dayOne.facts().to()).isEqualTo(BORIS);
        assertThat(dayOne.facts().topCount()).isEqualTo(3);
        assertThat(picked.get(2).facts().count()).isEqualTo(1);
        assertThat(picked.get(2).facts().to()).isNull(); // one conquest: nobody is the busiest
    }

    @Test
    void aTieOrASingleConquestNamesNoBusiestConqueror() {
        conquest(1, null, ANNA, false, false); // the first: told on its own
        conquest(2, null, ANNA, false, false);
        conquest(2, null, BORIS, false, false);
        conquest(3, null, ANNA, false, false);
        conquest(3, null, ANNA, false, false);
        conquest(3, null, BORIS, false, false);
        conquest(3, null, BORIS, false, false);

        List<Entry> picked = ChronicleRules.pick(events, List.of(), false);

        assertThat(picked).hasSize(3);
        assertThat(picked.get(1).facts().count()).isEqualTo(2);
        assertThat(picked.get(1).facts().to()).isNull();
        assertThat(picked.get(2).facts().count()).isEqualTo(4);
        assertThat(picked.get(2).facts().to()).isNull();
    }

    @Test
    void everyOtherMomentIsToldAsItIs() {
        event(ChronicleKind.ALLY_JOINED, 2, ChronicleFacts.empty().withLord(LORD));
        event(ChronicleKind.HELP_IGNORED, 3, ChronicleFacts.empty().withLord(LORD));
        event(ChronicleKind.WORLD_ENDED, 4, ChronicleFacts.empty().withReason("END_DATE"));

        assertThat(ChronicleRules.pick(events, List.of(), false))
                .extracting(Entry::kind)
                .containsExactly(
                        ChronicleKind.ALLY_JOINED,
                        ChronicleKind.HELP_IGNORED,
                        ChronicleKind.WORLD_ENDED);
    }

    @Test
    void aNewLeaderInPointsIsToldButNotTheFirstOneOrATie() {
        Owner anna = owner(1, null, 0);
        Owner boris = owner(2, null, 0);
        List<Frame> frames =
                List.of(
                        frame(0, points(anna, 100), points(boris, 50)), // Anna leads first
                        frame(1, points(anna, 100), points(boris, 100)), // a tie: no change
                        frame(2, points(anna, 100), points(boris, 150)), // Boris takes the lead
                        frame(3, points(anna, 90), points(boris, 160))); // he keeps it

        List<Entry> leads = ChronicleRules.leads(frames, false);

        assertThat(leads)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.day()).isEqualTo(2);
                            assertThat(e.facts().side().id()).isEqualTo(2);
                        });
    }

    @Test
    void withGuildsTheLeadIsAGuildsSumOfPoints() {
        Owner anna = owner(1, 10L, 0);
        Owner carla = owner(4, 10L, 0);
        Owner boris = owner(2, null, 0);
        List<Frame> frames =
                List.of(
                        frame(0, points(boris, 150), points(anna, 100), points(carla, 0)),
                        frame(1, points(boris, 150), points(anna, 100), points(carla, 60)));

        assertThat(ChronicleRules.leads(frames, true))
                .singleElement()
                .satisfies(e -> assertThat(e.facts().side().key()).isEqualTo("GUILD:10"));
        // without guilds Boris stays ahead of each of them
        assertThat(ChronicleRules.leads(frames, false)).isEmpty();
    }

    // ---- helpers ----

    private void conquest(
            int day, ChroniclePlayer from, ChroniclePlayer to, boolean keep, boolean eliminated) {
        event(
                ChronicleKind.CONQUEST,
                day,
                ChronicleFacts.empty()
                        .withConquest(
                                new ChronicleVillage(events.size(), "V", 1, 1),
                                keep,
                                from,
                                to,
                                eliminated));
    }

    private void event(ChronicleKind kind, int day, ChronicleFacts facts) {
        long id = events.size() + 1;
        events.add(new Stored(id, T0.plus(Duration.ofHours(id)), day, kind, facts));
    }

    private static Owner owner(long id, Long guildId, long points) {
        return new Owner(id, "P" + id, false, guildId, guildId == null ? null : "G", "G", points);
    }

    private static Owner points(Owner owner, long points) {
        return new Owner(
                owner.id(),
                owner.name(),
                owner.npc(),
                owner.guildId(),
                owner.guildName(),
                owner.guildTag(),
                points);
    }

    private static Frame frame(int day, Owner... owners) {
        return new Frame(day, T0.plus(Duration.ofDays(day)), List.of(), List.of(owners));
    }
}
