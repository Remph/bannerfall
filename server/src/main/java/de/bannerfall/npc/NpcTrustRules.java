package de.bannerfall.npc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules of trust between an NPC lord and a human (2.1 S2b, 12-solo-and-allies §5) as pure
 * functions: the drift towards 0, the bounds, the daily caps, the bar a lord joins at and its
 * answer to an invitation. The numbers come from {@link NpcTrustSettings}.
 */
public final class NpcTrustRules {

    /** The lowest trust: a lord whose village the human conquered. */
    public static final int MIN = -100;

    /** The highest trust. */
    public static final int MAX = 100;

    /** How many of the last changes a lord's trust keeps for the diplomacy page. */
    public static final int KEPT_CHANGES = 3;

    private NpcTrustRules() {}

    /**
     * One change as the diplomacy page lists it.
     *
     * @param delta what it moved, rounded (the daily caps may make it smaller than the rule)
     * @param village where it happened (the lord's village, or the beaten one), null if nowhere
     */
    public record Change(TrustReason reason, int delta, String village) {}

    /** The lord's answer to an invitation into a human's guild (12 §5). */
    public enum Answer {
        YES,
        /** Trust below the bar: "I don't know you well enough yet". */
        NOT_ENOUGH_TRUST,
        /** The guild has no room. */
        GUILD_FULL
    }

    /** A game day at {@code speed}: a real day ÷ speed. */
    public static Duration day(double speed) {
        return Duration.ofMillis(Math.round(Duration.ofDays(1).toMillis() / speed));
    }

    /**
     * {@code trust} after {@code elapsed} of drift towards 0 by {@code perDay} a game day (÷
     * speed); never past 0.
     */
    public static double drifted(double trust, Duration elapsed, double speed, double perDay) {
        if (trust == 0 || elapsed.isNegative() || elapsed.isZero() || perDay <= 0) {
            return trust;
        }
        double days = (double) elapsed.toMillis() / day(speed).toMillis();
        double move = perDay * days;
        return trust > 0 ? Math.max(0, trust - move) : Math.min(0, trust + move);
    }

    /** {@code trust} plus {@code delta}, kept within {@link #MIN} and {@link #MAX}. */
    public static double plus(double trust, double delta) {
        return Math.max(MIN, Math.min(MAX, trust + delta));
    }

    /** The trust shown to players and compared with the bar: the stored value rounded. */
    public static int shown(double trust) {
        return (int) Math.round(trust);
    }

    /**
     * What a gain may still give today: {@code gain}, but at most {@code cap} minus what the same
     * kind already gave today; never negative.
     */
    public static double capped(double gain, double givenToday, double cap) {
        return Math.max(0, Math.min(gain, cap - givenToday));
    }

    /**
     * The trust a delivery of {@code amount} resources gives when {@code shippedToday} arrived
     * before today: {@code perThousand} per full 1,000 of the day's total, at most {@code cap} a
     * day, minus what the day's earlier shipments gave already. Small shipments add up.
     */
    public static double shippedGain(
            long shippedToday, long amount, double perThousand, double cap) {
        double before = Math.min(cap, Math.floor(shippedToday / 1000.0) * perThousand);
        double after = Math.min(cap, Math.floor((shippedToday + amount) / 1000.0) * perThousand);
        return Math.max(0, after - before);
    }

    /** The trust a lord joins a human's guild at: its personality's bar, higher in a lord guild. */
    public static int bar(
            NpcTrustSettings settings, NpcPersonality personality, boolean inLordGuild) {
        return settings.bars().get(personality) + (inLordGuild ? settings.lordGuildExtra() : 0);
    }

    /** The lord's answer: yes when trust reaches the bar and the guild has room (12 §5). */
    public static Answer answer(double trust, int bar, boolean guildHasRoom) {
        if (shown(trust) < bar) {
            return Answer.NOT_ENOUGH_TRUST;
        }
        return guildHasRoom ? Answer.YES : Answer.GUILD_FULL;
    }

    /** An ally leaves at its next round once its trust is below 0 (12 §5). */
    public static boolean leaves(double trust) {
        return shown(trust) < 0;
    }

    /** {@code change} put in front of {@code changes}, keeping the newest {@link #KEPT_CHANGES}. */
    public static List<Change> withChange(List<Change> changes, Change change) {
        List<Change> out = new ArrayList<>();
        out.add(change);
        for (Change old : changes) {
            if (out.size() >= KEPT_CHANGES) {
                break;
            }
            out.add(old);
        }
        return List.copyOf(out);
    }
}
