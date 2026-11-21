package de.bannerfall.npc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The numbers of help requests from allied NPC lords (2.1 S4, 12-solo-and-allies §9), bound from
 * {@code npc.help} in {@code gamedata/npc/balance.yml}. Invalid values stop the application at
 * startup.
 *
 * @param perDay requests all allies together send one human in a day (a game day ÷ speed)
 * @param shortageHours a shortage that held a noble or the wall this long (÷ speed) becomes a
 *     request
 * @param resourceHours a request for resources is open this long (÷ speed)
 * @param answered trust for an answer in time
 * @param declined trust for "Not this time"
 * @param ignored trust for no answer until the deadline
 * @param thanksHours a thank-you shipment at most once this long (÷ speed) per lord and human
 * @param thanksShare the share of what the lord holds above its own next goal it ships as thanks
 * @param minDefenders a request for support asks for at least this many defenders
 * @param maxDefenders and at most this many
 * @param warnBelow an ally whose trust in a human falls below this writes a warning, once
 * @param warnAgainAbove and again only after its trust rose above this
 */
@ConfigurationProperties("npc.help")
public record NpcHelpSettings(
        int perDay,
        double shortageHours,
        double resourceHours,
        int answered,
        int declined,
        int ignored,
        double thanksHours,
        double thanksShare,
        int minDefenders,
        int maxDefenders,
        int warnBelow,
        int warnAgainAbove) {

    public NpcHelpSettings {
        List<String> problems = new ArrayList<>();
        if (perDay < 1) {
            problems.add("per-day must be >= 1");
        }
        if (!(shortageHours > 0) || !(resourceHours > 0) || !(thanksHours > 0)) {
            problems.add("shortage-hours, resource-hours and thanks-hours must be above 0");
        }
        if (answered < 0 || declined > 0 || ignored > 0) {
            problems.add("answered must be >= 0, declined and ignored <= 0");
        }
        if (!(thanksShare > 0 && thanksShare <= 1)) {
            problems.add("thanks-share must be above 0 and at most 1");
        }
        if (minDefenders < 1 || maxDefenders < minDefenders) {
            problems.add("min-defenders must be >= 1 and at most max-defenders");
        }
        if (warnBelow < 0 || warnAgainAbove <= warnBelow || warnAgainAbove > NpcTrustRules.MAX) {
            problems.add("warn-below must be >= 0 and below warn-again-above (at most 100)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid NPC help requests (npc.help):\n" + String.join("\n", problems));
        }
    }
}
