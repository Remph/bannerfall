package de.bannerfall.victory;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.npc.NpcMemory;
import de.bannerfall.npc.NpcMemoryConverter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The NPC lords' memory on a wake or a fast-forward step (2.1, 12-solo-and-allies §2.4): every
 * instant in it moves, whatever record it sits in, and nothing else changes.
 */
class FrozenTimeTest {

    private static final NpcMemoryConverter CONVERTER = new NpcMemoryConverter();
    private static final Instant T = Instant.parse("2026-11-19T08:00:00Z");

    @Test
    void everyInstantInTheLordsMemoryMovesAndNothingElse() {
        Duration by = Duration.ofHours(5).plusMillis(250);
        NpcMemory before = sample(T);
        String shifted = FrozenTime.shiftInstants(CONVERTER.convertToDatabaseColumn(before), by);
        assertThat(CONVERTER.convertToEntityAttribute(shifted)).isEqualTo(sample(T.plus(by)));
        // backwards too: the host's fast forward
        String back = FrozenTime.shiftInstants(shifted, by.negated());
        assertThat(CONVERTER.convertToEntityAttribute(back)).isEqualTo(before);
    }

    @Test
    void theSampleHasEveryInstantTheMemoryCanHold() {
        // A new Instant in NpcMemory (or a record in it) must be added to sample(): this test
        // then proves the shift finds it.
        Set<String> declared = new TreeSet<>();
        instantComponents(NpcMemory.class, "NpcMemory", declared, new HashSet<>());
        Set<String> filled = new TreeSet<>();
        filledInstants(sample(T), "NpcMemory", filled);
        assertThat(filled).containsAll(declared);
    }

    @Test
    void textThatOnlyLooksLikeATimeStays() {
        String json = "{\"a\":\"2026-11-19\",\"b\":\"not 2026-11-19T08:00:00Z\",\"c\":42}";
        assertThat(FrozenTime.shiftInstants(json, Duration.ofHours(1))).isEqualTo(json);
    }

    private static NpcMemory sample(Instant t) {
        return new NpcMemory(
                7,
                Map.of(1L, new NpcMemory.Ledger(1, 2)),
                NpcMemory.Stance.NONE,
                Map.of(
                        2L,
                        new NpcMemory.Intel(
                                t.minusSeconds(60),
                                3L,
                                null,
                                2,
                                null,
                                null,
                                null,
                                false,
                                null,
                                0,
                                t.plusSeconds(3600),
                                null)),
                Map.of(4L, new NpcMemory.Grudge(t.plusSeconds(7200), 5L, false, 1)),
                List.of(new NpcMemory.SentAttack(6L, t.minusSeconds(120))),
                Map.of(8L, t.minusSeconds(30)),
                List.of(new NpcMemory.Supported(9L, 10L)),
                Map.of(11L, new NpcMemory.ConquestTarget(12L, t.plusSeconds(600))),
                new NpcMemory.Lessons(
                        new NpcMemory.Tally(t.minusSeconds(5), 1, 2, 3, 4, 5),
                        Map.of(13L, new NpcMemory.WallGoal(5, t.plusSeconds(900)))),
                Map.of(
                        14L,
                        new NpcMemory.Shortage(
                                "WALL:3",
                                t.minusSeconds(40),
                                new de.bannerfall.gamedata.ResourceAmounts(0, 100, 0))));
    }

    /** "Owner.component" for every Instant (or map or list of them) in the records under type. */
    private static void instantComponents(
            Type type, String where, Set<String> found, Set<Type> seen) {
        if (type instanceof ParameterizedType p) {
            for (Type argument : p.getActualTypeArguments()) {
                instantComponents(argument, where, found, seen);
            }
            return;
        }
        if (type == Instant.class) {
            found.add(where);
            return;
        }
        if (!(type instanceof Class<?> c) || !c.isRecord() || !seen.add(c)) {
            return;
        }
        if (!c.getPackageName().startsWith("de.bannerfall.npc")) {
            return; // reports and game data hold no times
        }
        for (RecordComponent component : c.getRecordComponents()) {
            instantComponents(
                    component.getGenericType(),
                    c.getSimpleName() + "." + component.getName(),
                    found,
                    seen);
        }
    }

    private static void filledInstants(Object value, String where, Set<String> found) {
        switch (value) {
            case null -> {}
            case Instant i -> found.add(where);
            case Map<?, ?> m ->
                    m.forEach(
                            (k, v) -> {
                                filledInstants(k, where, found);
                                filledInstants(v, where, found);
                            });
            case Collection<?> c -> c.forEach(e -> filledInstants(e, where, found));
            case Record r -> {
                for (RecordComponent component : r.getClass().getRecordComponents()) {
                    try {
                        filledInstants(
                                component.getAccessor().invoke(r),
                                r.getClass().getSimpleName() + "." + component.getName(),
                                found);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            default -> {}
        }
    }
}
