package de.bannerfall.farm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What "send all" did (M39, 04-api <i>Farm helper</i>): the list was worked through from the top to
 * the end (since 1.3 an entry the units don't cover no longer ends the run, it is skipped).
 *
 * @param sent the entries whose attack went out, in list order
 * @param skipped the entries passed over, with the reason, in list order
 */
public record FarmSendAllDto(List<Long> sent, List<Skipped> skipped) {

    /** Why an entry was passed over; the run goes on with the next one. */
    public enum SkipReason {
        /**
         * The village doesn't have the units of the entry's template at home (any more: the entries
         * above may have taken them).
         */
        NOT_ENOUGH_UNITS,
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
