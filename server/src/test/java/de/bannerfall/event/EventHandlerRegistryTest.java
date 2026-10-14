package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventHandlerRegistryTest {

    /** A handler that does nothing. */
    private record StubHandler(GameEventKind kind) implements GameEventHandler {
        @Override
        public void handle(GameEvent event) {}
    }

    @Test
    void findsTheHandlerOfAKind() {
        var build = new StubHandler(GameEventKind.BUILD_COMPLETE);
        var movement = new StubHandler(GameEventKind.MOVEMENT_ARRIVE);
        var registry = new EventHandlerRegistry(List.of(build, movement));

        assertThat(registry.get(GameEventKind.BUILD_COMPLETE)).isSameAs(build);
        assertThat(registry.get(GameEventKind.MOVEMENT_ARRIVE)).isSameAs(movement);
    }

    @Test
    void twoHandlersForOneKindAreRejected() {
        var first = new StubHandler(GameEventKind.BUILD_COMPLETE);
        var second = new StubHandler(GameEventKind.BUILD_COMPLETE);

        assertThatThrownBy(() -> new EventHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUILD_COMPLETE")
                .hasMessageContaining("Two handlers");
    }

    @Test
    void aKindWithoutHandlerIsAnError() {
        var registry = new EventHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.get(GameEventKind.TRADE_ARRIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRADE_ARRIVE");
    }

    @Test
    void waitingKindsNeedAHandler() {
        var registry =
                new EventHandlerRegistry(List.of(new StubHandler(GameEventKind.BUILD_COMPLETE)));

        assertThatCode(() -> registry.requireHandlersFor(List.of(GameEventKind.BUILD_COMPLETE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> registry.requireHandlersFor(List.of())).doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                registry.requireHandlersFor(
                                        List.of(
                                                GameEventKind.BUILD_COMPLETE,
                                                GameEventKind.NPC_THINK,
                                                GameEventKind.TRADE_ARRIVE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRADE_ARRIVE")
                .hasMessageContaining("NPC_THINK")
                .hasMessageNotContaining("BUILD_COMPLETE");
    }
}
