package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The matching of {@link HostCheckFilter}; {@link HostCheckFlowTest} runs it in the app. */
class HostCheckFilterTest {

    @Test
    void aDomainWithoutSchemeOrNoneChecksNothing() {
        assertThat(HostCheckFilter.allowedHosts("play.example.com")).isNull();
        assertThat(HostCheckFilter.allowedHosts("")).isNull();
        assertThat(HostCheckFilter.allowedHosts(null)).isNull();
        assertThat(HostCheckFilter.accepts(null, "evil.example:8461")).isTrue();
    }

    @Test
    void theSoloAddressAnswersItsHostAndLoopbackOnAnyPort() {
        Set<String> allowed = HostCheckFilter.allowedHosts(" http://localhost:8461 ");

        assertThat(allowed).containsExactlyInAnyOrder("localhost", "127.0.0.1", "[::1]");
        assertThat(HostCheckFilter.accepts(allowed, "localhost:8461")).isTrue();
        assertThat(HostCheckFilter.accepts(allowed, "LocalHost:8461")).isTrue();
        assertThat(HostCheckFilter.accepts(allowed, "127.0.0.1:8461")).isTrue();
        assertThat(HostCheckFilter.accepts(allowed, "[::1]:8461")).isTrue();
        // the health check and the host console inside the container
        assertThat(HostCheckFilter.accepts(allowed, "localhost:8080")).isTrue();
        assertThat(HostCheckFilter.accepts(allowed, null)).isTrue();
    }

    @Test
    void aRebindingNameIsRefused() {
        Set<String> allowed = HostCheckFilter.allowedHosts("http://localhost:8461");

        assertThat(HostCheckFilter.accepts(allowed, "evil.example:8461")).isFalse();
        assertThat(HostCheckFilter.accepts(allowed, "evil.example")).isFalse();
        assertThat(HostCheckFilter.accepts(allowed, "localhost.evil.example:8461")).isFalse();
        assertThat(HostCheckFilter.accepts(allowed, "127.0.0.1.nip.io:8461")).isFalse();
        assertThat(HostCheckFilter.accepts(allowed, "")).isFalse();
    }

    @Test
    void anotherHostWithSchemeIsAddedToLoopback() {
        Set<String> allowed = HostCheckFilter.allowedHosts("http://Game-PC.lan:8461");

        assertThat(allowed).contains("game-pc.lan", "localhost");
        assertThat(HostCheckFilter.accepts(allowed, "game-pc.lan:8461")).isTrue();
        assertThat(HostCheckFilter.accepts(allowed, "other-pc.lan:8461")).isFalse();
    }

    @Test
    void anAddressWithSchemeButNoHostAnswersLoopbackOnly() {
        assertThat(HostCheckFilter.allowedHosts("http://"))
                .containsExactlyInAnyOrder("localhost", "127.0.0.1", "[::1]");
    }

    @Test
    void hostNameDropsThePort() {
        assertThat(HostCheckFilter.hostName("localhost:8461")).isEqualTo("localhost");
        assertThat(HostCheckFilter.hostName("[::1]:8461")).isEqualTo("[::1]");
        assertThat(HostCheckFilter.hostName("example.org")).isEqualTo("example.org");
    }
}
