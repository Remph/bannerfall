package de.bannerfall.rules;

import java.time.Instant;
import java.time.ZoneId;

/**
 * The night bonus (1.4, 02-game-rules §8.1): an attack that arrives between {@code startHour} and
 * {@code endHour} in {@code zone} meets a defence {@code factor} times as strong, if the village
 * has an owner (barbarians never get it). The window may run across midnight (23 to 7); the start
 * hour belongs to it, the end hour doesn't. Pure value, built from the world settings.
 *
 * @param enabled the world's switch; off means a factor of 1 at any time
 * @param startHour 0–23, the first hour of the night
 * @param endHour 0–23, the hour the night ends; never equal to {@code startHour}
 * @param factor multiplies the defence at night, above 1
 * @param zone the time zone the hours are read in
 */
public record NightBonus(boolean enabled, int startHour, int endHour, double factor, ZoneId zone) {

    public NightBonus {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException(
                    "hours must be in 0–23: " + startHour + ", " + endHour);
        }
        if (startHour == endHour) {
            throw new IllegalArgumentException("the night must not be empty: " + startHour);
        }
        if (!(factor >= 1)) {
            throw new IllegalArgumentException("factor must be at least 1: " + factor);
        }
    }

    /** Whether {@code at} lies in the night window (whatever the switch says). */
    public boolean isNight(Instant at) {
        int hour = at.atZone(zone).getHour();
        return startHour < endHour
                ? hour >= startHour && hour < endHour
                : hour >= startHour || hour < endHour;
    }

    /**
     * What the defence of a village is multiplied with for an attack arriving at {@code at}: the
     * factor at night with the switch on and an owned village, else 1.
     *
     * @param owned false for a barbarian village
     */
    public double factorAt(Instant at, boolean owned) {
        return enabled && owned && isNight(at) ? factor : 1;
    }
}
