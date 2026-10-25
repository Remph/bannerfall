package de.bannerfall.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Service;

/**
 * The host's switch for debug logs (1.6.3, 08-host-console §2a): sets the level of the game's own
 * loggers ({@code de.bannerfall}) while the server runs, through Spring's {@link LoggingSystem}.
 * Nothing is stored, so a restart is back at INFO. Debug lines cost time and disk (the event
 * worker, every battle and every step of an NPC lord's round write one), so the console says so.
 */
@Service
class DebugLogging {

    private static final Logger log = LoggerFactory.getLogger(DebugLogging.class);

    /** The package every class of the game lives in; Spring, Hibernate and Tomcat stay at INFO. */
    static final String LOGGER = "de.bannerfall";

    private final LoggingSystem loggingSystem;

    DebugLogging(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    /** Whether debug lines of the game are written right now. */
    boolean enabled() {
        return LoggerFactory.getLogger(LOGGER).isDebugEnabled();
    }

    /** Switches the game's debug lines on, or back to the level the configuration gives. */
    void set(boolean enabled) {
        loggingSystem.setLogLevel(LOGGER, enabled ? LogLevel.DEBUG : null);
        log.info(
                enabled ? "Debug logging is ON until the server restarts" : "Debug logging is OFF");
    }
}
