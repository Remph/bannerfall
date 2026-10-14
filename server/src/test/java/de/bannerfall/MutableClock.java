package de.bannerfall;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A Clock tests can move forward: clock.advance(Duration.ofHours(2)). */
public class MutableClock extends Clock {

    private Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public static MutableClock startingAt(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this; // The game only uses UTC.
    }

    @Override
    public Instant instant() {
        return now;
    }
}
