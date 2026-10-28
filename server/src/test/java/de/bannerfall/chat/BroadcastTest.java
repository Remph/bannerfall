package de.bannerfall.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.common.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The host's popups (1.7): sent live to everyone, kept for ten minutes for players who connect
 * later, never in the chat. The REST side is in {@code HostConsoleTest}, the real connections in
 * {@code WebSocketTest}.
 */
@IntegrationTest
class BroadcastTest {

    @Autowired BroadcastService broadcasts;
    @Autowired MutableClock clock;
    @Autowired SentMessages sent;

    @Test
    void aBroadcastIsKeptForTenMinutesOldestFirst() {
        broadcasts.send("first");
        clock.advance(Duration.ofMinutes(6));
        broadcasts.send("second");

        assertThat(broadcasts.recent())
                .extracting(BroadcastDto::text)
                .containsSubsequence("first", "second");

        // the first is now 11 minutes old, the second 5
        clock.advance(Duration.ofMinutes(5));
        assertThat(broadcasts.recent())
                .extracting(BroadcastDto::text)
                .contains("second")
                .doesNotContain("first");

        clock.advance(BroadcastService.KEEP);
        assertThat(broadcasts.recent()).isEmpty();
    }

    @Test
    void idsKeepGrowingEvenWithinOneMillisecond() {
        BroadcastDto a = broadcasts.send("a");
        BroadcastDto b = broadcasts.send("b");

        assertThat(a.id()).isGreaterThanOrEqualTo(a.sentAt().toEpochMilli());
        assertThat(b.id()).isGreaterThan(a.id());
    }

    @Test
    void aBroadcastIsTrimmedAndCheckedLikeAChatMessage() {
        assertThat(broadcasts.send("  Update in 5 minutes \n").text())
                .isEqualTo("Update in 5 minutes");
        assertThatThrownBy(() -> broadcasts.send("  ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> broadcasts.send("a".repeat(501))).isInstanceOf(ApiException.class);
    }

    @Test
    void itGoesToTheBroadcastTopicAndNotToTheChat() {
        int chatBefore = sent.chat().size();
        int popupsBefore = sent.broadcasts().size();

        BroadcastDto broadcast = broadcasts.send("Hello");

        assertThat(sent.chat()).hasSize(chatBefore);
        assertThat(sent.broadcasts().subList(popupsBefore, sent.broadcasts().size()))
                .singleElement()
                .asString()
                .contains("\"id\":" + broadcast.id(), "\"text\":\"Hello\"");
    }
}
