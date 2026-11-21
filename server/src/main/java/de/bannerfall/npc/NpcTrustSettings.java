package de.bannerfall.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The numbers of trust between NPC lords and humans (2.1 S2b, 12-solo-and-allies §5), bound from
 * {@code npc.trust} in {@code gamedata/npc/balance.yml}. Days and hours are game time ÷ speed.
 * Invalid values stop the application at startup.
 *
 * @param driftPerDay trust moves towards 0 by this much a day
 * @param support a support of the human's stands in the lord's village when a foreign attack
 *     arrives
 * @param supportPerDay the most {@code support} gives in a day
 * @param shippedPerThousand per 1,000 resources the human's merchants delivered to the lord
 * @param shippedPerDay the most shipments give in a day
 * @param grudgeWin the human beat somebody the lord holds a grudge against (once per grudge)
 * @param attacked the human attacked the lord (negative)
 * @param scouted the human scouted the lord (negative)
 * @param mateAttacked the human attacked one of the lord's guild mates (negative)
 * @param bars the trust a lord joins a human's guild at, per personality
 * @param lordGuildExtra the bar is this much higher while the lord is in a lord guild
 * @param refusalWaitHours after a no, the next invitation waits this long
 */
@ConfigurationProperties("npc.trust")
public record NpcTrustSettings(
        double driftPerDay,
        double support,
        double supportPerDay,
        double shippedPerThousand,
        double shippedPerDay,
        double grudgeWin,
        double attacked,
        double scouted,
        double mateAttacked,
        Map<NpcPersonality, Integer> bars,
        int lordGuildExtra,
        double refusalWaitHours) {

    public NpcTrustSettings {
        var copy = new EnumMap<NpcPersonality, Integer>(NpcPersonality.class);
        if (bars != null) {
            copy.putAll(bars);
        }
        bars = Collections.unmodifiableMap(copy);
        List<String> problems = new ArrayList<>();
        if (!(driftPerDay >= 0)) {
            problems.add("drift-per-day must be >= 0");
        }
        for (var gain :
                Map.of(
                                "support", support,
                                "support-per-day", supportPerDay,
                                "shipped-per-thousand", shippedPerThousand,
                                "shipped-per-day", shippedPerDay,
                                "grudge-win", grudgeWin)
                        .entrySet()) {
            if (!(gain.getValue() >= 0 && gain.getValue() <= NpcTrustRules.MAX)) {
                problems.add(gain.getKey() + " must be 0 to " + NpcTrustRules.MAX);
            }
        }
        for (var loss :
                Map.of("attacked", attacked, "scouted", scouted, "mate-attacked", mateAttacked)
                        .entrySet()) {
            if (!(loss.getValue() <= 0 && loss.getValue() >= NpcTrustRules.MIN)) {
                problems.add(loss.getKey() + " must be " + NpcTrustRules.MIN + " to 0");
            }
        }
        for (NpcPersonality personality : NpcPersonality.values()) {
            Integer bar = copy.get(personality);
            if (bar == null || bar < 0 || bar > NpcTrustRules.MAX) {
                problems.add("bars." + personality + " must be 0 to " + NpcTrustRules.MAX);
            }
        }
        if (lordGuildExtra < 0) {
            problems.add("lord-guild-extra must be >= 0");
        }
        if (!(refusalWaitHours >= 0)) {
            problems.add("refusal-wait-hours must be >= 0");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid NPC trust (npc.trust):\n" + String.join("\n", problems));
        }
    }
}
