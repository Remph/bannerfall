package de.bannerfall.farm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What "send all" did (M39, 04-api <i>Farm helper</i>): the list was worked through from the top
 * until it ended or the units ran out.
 *
 * @param sent the entries whose attack went out, in list order
 * @param skipped the entries passed over because their target can't be hit now, in list order
 * @param stop why the run ended
 * @param stoppedAt the entry the units didn't cover; null if the list was done
 */
public record FarmSendAllDto(
        List<Long> sent,
        List<Skipped> skipped,
        Stop stop,
        @Schema(nullable = true) Long stoppedAt) {

    /** Why the run ended. */
    public enum Stop {
        /** Every entry was sent or skipped. */
        DONE,
        /** The village doesn't have the units of the next entry's template at home. */
        NOT_ENOUGH_UNITS
    }

    /** Why an entry was passed over; the run goes on with the next one. */
    public enum SkipReason {
        /** The target player is under beginner protection when the attack would arrive. */
        PROTECTED_TARGET,
        /** The target belongs to the player now (conquered since it was added). */
        OWN_VILLAGE,
        /**
         * The sender is under beginner protection and the target is a player's village: the attack
         * would end that protection, which a single send asks twice for, so "send all" never does.
         */
        ENDS_OWN_PROTECTION
    }

    /** An entry that was passed over. */
    @Schema(name = "FarmSendAllSkipped")
    public record Skipped(long targetId, SkipReason reason) {}
}
