package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shared proxy for several worlds on one server ({@code edge/Caddyfile}, 1.5) repeats the
 * single-world {@code Caddyfile}'s log filter and headers. Both are hand-written, so this test
 * keeps them from drifting apart: a header added to one must be added to the other.
 */
class EdgeCaddyfileTest {

    static final Path SINGLE_WORLD = Path.of("../Caddyfile");
    static final Path EDGE = Path.of("../edge/Caddyfile");

    @Test
    void theLogFilterIsTheSame() throws IOException {
        assertThat(globalOptions(read(EDGE)))
                .isNotEmpty()
                .isEqualTo(globalOptions(read(SINGLE_WORLD)));
    }

    @Test
    void eachWorldGetsTheSameHeadersAndRules() throws IOException {
        // One world takes its domain from .env; the edge serves each world under its own {host}.
        List<String> singleWorld =
                siteRules(read(SINGLE_WORLD)).stream()
                        .map(line -> line.replace("wss://{$BANNERFALL_DOMAIN}", "wss://{host}"))
                        .toList();

        assertThat(siteRules(read(EDGE))).contains("-Server").isEqualTo(singleWorld);
    }

    @Test
    void theEdgePassesEachWorldToItsPortOnThisHost() throws IOException {
        assertThat(read(EDGE)).contains("reverse_proxy 127.0.0.1:{args[0]}", "import sites.caddy");
    }

    /** The lines of the file, trimmed, without blank lines and comments. */
    private static List<String> read(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    /** The global options block: from the first line to the first closing brace on its own. */
    private static List<String> globalOptions(List<String> lines) {
        assertThat(lines.getFirst()).isEqualTo("{");
        return lines.subList(0, lines.indexOf("}") + 1);
    }

    /** What every world's site gets: from the compression to the end of the header block. */
    private static List<String> siteRules(List<String> lines) {
        int from = lines.indexOf("encode zstd gzip");
        int to = lines.indexOf("-Server");
        assertThat(from).isNotNegative();
        assertThat(to).isGreaterThan(from);
        return lines.subList(from, to + 1);
    }
}
