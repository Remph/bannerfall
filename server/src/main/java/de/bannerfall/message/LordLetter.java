package de.bannerfall.message;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.victory.CourtRefDto;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * A letter of an NPC lord to a human (2.1 S2b, 12-solo-and-allies §5, §8, §9): kept as a private
 * message from the lord, whose text the client writes in the reader's language from {@code kind}
 * and the fields the kind uses. Every field but {@code kind} is optional, so older letters still
 * read.
 *
 * @param guild the guild the letter is about (the one the lord joined, refused or left); null if
 *     none
 * @param target the village an order was about (S3); null for the guild letters
 * @param units the units the lord sends on an order (attack, support); null if none
 * @param resources the resources it sends on a {@code SEND} order; null if none
 * @param arriveAt when its troops or merchants arrive (game time when written); null if none
 * @param departAt when a "land together" march leaves (game time when written); null if none
 * @param voice how the lord writes (S4: one voice per personality); set by {@code
 *     MessageService.sendLetter}, null in older letters (the client then writes plainly)
 * @param request the help request the letter is (S4, 12 §9); null for every other letter
 * @param count the defenders a request for support asks for; null otherwise
 * @param attacker the name of the player whose attack a request for support is about; null if none
 * @param goal what a shortage holds up ({@code NOBLE} or {@code WALL}); null otherwise
 * @param court the court that started the victory clock (a taunt); null otherwise
 */
public record LordLetter(
        Kind kind,
        @Schema(nullable = true) String guild,
        @Schema(nullable = true) VillageRefDto target,
        @Schema(nullable = true) Map<UnitType, Integer> units,
        @Schema(nullable = true) ResourceAmounts resources,
        @Schema(nullable = true) Instant arriveAt,
        @Schema(nullable = true) Instant departAt,
        @Schema(nullable = true) Voice voice,
        @Schema(nullable = true) Long request,
        @Schema(nullable = true) Integer count,
        @Schema(nullable = true) String attacker,
        @Schema(nullable = true) String goal,
        @Schema(nullable = true) CourtRefDto court) {

    /** A letter without the fields of S4. */
    public LordLetter(
            Kind kind,
            String guild,
            VillageRefDto target,
            Map<UnitType, Integer> units,
            ResourceAmounts resources,
            Instant arriveAt,
            Instant departAt) {
        this(
                kind, guild, target, units, resources, arriveAt, departAt, null, null, null, null,
                null, null);
    }

    /** A letter about a guild (joining, refusing, leaving, a warning). */
    public LordLetter(Kind kind, String guild) {
        this(kind, guild, null, null, null, null, null);
    }

    /** A letter about an order on {@code target} (S3, 12 §8). */
    public static LordLetter order(Kind kind, VillageRefDto target) {
        return new LordLetter(kind, null, target, null, null, null, null);
    }

    /** The same letter in the lord's voice. */
    public LordLetter withVoice(Voice newVoice) {
        return new LordLetter(
                kind, guild, target, units, resources, arriveAt, departAt, newVoice, request, count,
                attacker, goal, court);
    }

    /**
     * How a lord writes (S4, 12 §9): one voice per personality, so the player hears who is careful
     * and who is bold without being told the personality (09 §2).
     */
    public enum Voice {
        /** The careful one (DEFENSIVE): warm, worried, grateful. */
        CAREFUL,
        /** The steady one (BALANCED): plain and courteous. */
        STEADY,
        /** The bold one (OFFENSIVE): short, proud, blunt. */
        BOLD
    }

    /** What the letter says. */
    public enum Kind {
        /** The lord accepted the invitation and joined the guild. */
        JOINED,
        /** The lord said no: trust below its bar ("I don't know you well enough yet"). */
        REFUSED_TRUST,
        /** The lord said no: the guild has no room. */
        REFUSED_FULL,
        /** The lord left the guild: its trust fell below 0. */
        LEFT_TRUST,
        /** The lord left the guild at once: the human attacked it. */
        LEFT_ATTACKED,
        /** Order (S3): its attack is on the way ({@code units}, {@code arriveAt}). */
        ORDER_ATTACK,
        /**
         * Order: it lands together with the human's attack, leaving at {@code departAt} and landing
         * at {@code arriveAt} with {@code units}.
         */
        ORDER_ATTACK_PLANNED,
        /** Order: its support is on the way ({@code units}, {@code arriveAt}). */
        ORDER_SUPPORT,
        /** Order: its merchants are on the way ({@code resources}, {@code arriveAt}). */
        ORDER_SEND,
        /** Order: it leaves the village alone. */
        ORDER_HOLD,
        /** Order refused: its trust in the human is below 20 ("No"). */
        ORDER_REFUSED_TRUST,
        /** Order refused: the target belongs to its own guild ("No"). */
        ORDER_REFUSED_BOUND,
        /** Order refused: its estimate is no clear win ("I would lose"). */
        ORDER_TOO_STRONG,
        /** Order refused: no defenders to spare for a support. */
        ORDER_NO_TROOPS,
        /** Order refused: no resources above its own next goal. */
        ORDER_NO_SURPLUS,
        /** Order refused: the human's warehouse has no room for what it could send. */
        ORDER_NO_ROOM,
        /** Order refused: it can't land within a minute after the human's attack. */
        ORDER_NOT_IN_TIME,
        /** Order refused: it couldn't see what stands in the target (no scouts, or they died). */
        ORDER_UNSEEN,
        /** Order refused: its attacks on that player today are used up (09 §7). */
        ORDER_CAPS,
        /** Order refused: the target's owner is under beginner protection. */
        ORDER_PROTECTED,
        /**
         * Help request (S4): {@code count} defenders at {@code target} before {@code arriveAt}, the
         * arrival of {@code attacker}'s attack.
         */
        HELP_SUPPORT,
        /**
         * Help request (S4): {@code resources} at {@code target} before {@code arriveAt}, for its
         * {@code goal}.
         */
        HELP_RESOURCES,
        /**
         * Thanks for a request answered in time (S4), with the thank-you shipment ({@code
         * resources}, {@code arriveAt}) when there is one.
         */
        THANKS,
        /** Warning (S4): the lord's trust in the human is falling towards 0. */
        WARNING,
        /**
         * The court started the victory clock (S4): {@code court} of {@code guild} wins at {@code
         * arriveAt} unless somebody stops it.
         */
        COURT_TAUNT
    }
}
