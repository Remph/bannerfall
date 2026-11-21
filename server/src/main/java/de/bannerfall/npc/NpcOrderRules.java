package de.bannerfall.npc;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules an allied lord weighs an order with (2.1 S3, 12-solo-and-allies §8), without the world:
 * when it says no, how much of its army an order may take, the "land together" timing and the
 * resources it may spare. Unit-tested ({@code NpcOrderRulesTest}); {@link NpcOrders} applies them.
 */
final class NpcOrderRules {

    private NpcOrderRules() {}

    /** "No" to every order: its trust in the human (rounded) is below the bar. */
    static boolean refuses(double trust, NpcOrderSettings settings) {
        return NpcTrustRules.shown(trust) < settings.refuseBelowTrust();
    }

    /**
     * "No" to an attack: the target belongs to the lord itself or to somebody of its guild (its
     * guild mates include the humans of the guild).
     */
    static boolean bound(Long targetOwner, long lordId, Set<Long> mates) {
        return targetOwner != null && (targetOwner == lordId || mates.contains(targetOwner));
    }

    /** The share of its support units an ordered support takes: its own share doubled, a floor. */
    static double supportShare(double ownShare, NpcOrderSettings settings) {
        return Math.min(
                1, Math.max(settings.supportMinShare(), ownShare * settings.supportFactor()));
    }

    /** The share of its attack units an ordered attack takes, by personality. */
    static double attackShare(NpcPersonality personality, NpcOrderSettings settings) {
        return settings.attackShare().get(personality);
    }

    /** {@code share} of each of {@code types} in {@code available}, rounded down. */
    static Map<UnitType, Integer> army(
            Map<UnitType, Integer> available, List<UnitType> types, double share) {
        return NpcTargeting.shareOf(available, types, share);
    }

    /** When a "land together" march goes. */
    sealed interface Timing {
        /** Leave now: it lands within the window. */
        record Now() implements Timing {}

        /** Leave at {@code departAt} to land at {@code arriveAt}. */
        record Later(Instant departAt, Instant arriveAt) implements Timing {}

        /** It can't land within the window any more. */
        record TooLate() implements Timing {}
    }

    /**
     * "Land together" (12 §8): the lord aims to land {@code land-after-seconds} after the human's
     * attack. If it would have to leave in the past, it still goes now when it lands within the
     * window ({@code (ownArrival, ownArrival + land-window-seconds]}); otherwise it is too late.
     */
    static Timing landTogether(
            Instant now, Instant ownArrival, long travelSeconds, NpcOrderSettings settings) {
        Instant aim = ownArrival.plusSeconds(settings.landAfterSeconds());
        Instant depart = aim.minusSeconds(travelSeconds);
        if (depart.isAfter(now)) {
            return new Timing.Later(depart, aim);
        }
        Instant arrival = now.plusSeconds(travelSeconds);
        return arrival.isAfter(ownArrival)
                        && !arrival.isAfter(ownArrival.plusSeconds(settings.landWindowSeconds()))
                ? new Timing.Now()
                : new Timing.TooLate();
    }

    /** What the lord may send: of each resource what it holds above its own next goal. */
    static ResourceAmounts surplus(ResourceAmounts stock, ResourceAmounts nextGoal) {
        return new ResourceAmounts(
                Math.max(0, stock.wood() - nextGoal.wood()),
                Math.max(0, stock.clay() - nextGoal.clay()),
                Math.max(0, stock.iron() - nextGoal.iron()));
    }

    /**
     * The lord's surplus would fill at least one of its free merchants if the human had room for
     * it: then a Send that loads nothing failed for want of room, not of surplus.
     */
    static boolean wouldSend(ResourceAmounts surplus, int freeMerchants) {
        ResourceAmounts unlimited =
                new ResourceAmounts(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        return NpcShipments.load(surplus, unlimited, freeMerchants).total() > 0;
    }

    /**
     * A planned march's army if the village still has all of it at home; null if any of it is
     * missing (the lord then doesn't go with less than it weighed).
     */
    static Map<UnitType, Integer> stillHome(
            Map<UnitType, Integer> planned, Map<UnitType, Integer> home) {
        for (Map.Entry<UnitType, Integer> entry : planned.entrySet()) {
            if (home.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return null;
            }
        }
        return planned;
    }
}
