package de.bannerfall.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Trading with the market itself (02-game-rules §12.1): {@code ratio} of one resource for 1 of
 * another, up to a daily amount per resource that grows with the market level. Pure functions.
 */
public final class MarketExchange {

    /** What a village may receive per resource and day for each market level, at speed 1. */
    public static final int DAILY_CAP_PER_LEVEL = 1000;

    private MarketExchange() {}

    /**
     * How much of each resource a village may receive per day: {@code floor(1000 × level × speed)}
     * (0 without market).
     */
    public static int dailyCap(int marketLevel, double speed) {
        if (marketLevel < 0) {
            throw new IllegalArgumentException("level must not be negative: " + marketLevel);
        }
        return (int) Math.floor((double) DAILY_CAP_PER_LEVEL * marketLevel * speed);
    }

    /**
     * What receiving {@code amount} costs: {@code ceil(amount × ratio)}, counted in decimals so
     * that 1000 at 1.1 costs 1100 and not 1101.
     */
    public static int cost(int amount, double ratio) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        return BigDecimal.valueOf(ratio)
                .multiply(BigDecimal.valueOf(amount))
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
    }

    /** When the trading day of {@code now} began: the last midnight in {@code zone}. */
    public static Instant dayStart(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
    }

    /** When the daily amounts start again: the next midnight in {@code zone}. */
    public static Instant nextReset(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
    }
}
