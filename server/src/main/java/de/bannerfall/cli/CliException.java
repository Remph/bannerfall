package de.bannerfall.cli;

/**
 * Something the CLI tells the host instead of a result: the server refused (with its {@code code}),
 * isn't reachable, or the token is missing. Exit code {@value AdminCli#EXIT_REFUSED}.
 */
class CliException extends RuntimeException {

    CliException(String message) {
        super(message);
    }
}
