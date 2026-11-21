package de.bannerfall.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The numbers of orders to allied NPC lords (2.1 S3, 12-solo-and-allies §8), bound from {@code
 * npc.orders} in {@code gamedata/npc/balance.yml}. Invalid values stop the application at startup.
 *
 * @param attackShare the most of a village's available attack units an ordered attack takes, per
 *     personality
 * @param supportFactor an ordered support takes up to the lord's support share times this
 * @param supportMinShare an ordered support takes at least this share (decided in S3: an OFFENSIVE
 *     lord's own share is 0)
 * @param refuseBelowTrust below this trust in the human the lord answers every order with "No"
 * @param landAfterSeconds "land together": the lord aims to land this long after the human's attack
 * @param landWindowSeconds and goes only if it lands within this window after it
 * @param scoutTries scoutings of an unseen target before the lord answers that it can't see it
 * @param candidates the lord's villages nearest the target it tries, nearest first
 */
@ConfigurationProperties("npc.orders")
public record NpcOrderSettings(
        Map<NpcPersonality, Double> attackShare,
        double supportFactor,
        double supportMinShare,
        int refuseBelowTrust,
        int landAfterSeconds,
        int landWindowSeconds,
        int scoutTries,
        int candidates) {

    public NpcOrderSettings {
        var copy = new EnumMap<NpcPersonality, Double>(NpcPersonality.class);
        if (attackShare != null) {
            copy.putAll(attackShare);
        }
        attackShare = Collections.unmodifiableMap(copy);
        List<String> problems = new ArrayList<>();
        for (NpcPersonality personality : NpcPersonality.values()) {
            Double share = copy.get(personality);
            if (share == null || !(share > 0 && share <= 1)) {
                problems.add("attack-share." + personality + " must be above 0 and at most 1");
            }
        }
        if (!(supportFactor >= 0)) {
            problems.add("support-factor must be >= 0");
        }
        if (!(supportMinShare >= 0 && supportMinShare <= 1)) {
            problems.add("support-min-share must be 0 to 1");
        }
        if (refuseBelowTrust < NpcTrustRules.MIN || refuseBelowTrust > NpcTrustRules.MAX) {
            problems.add(
                    "refuse-below-trust must be " + NpcTrustRules.MIN + " to " + NpcTrustRules.MAX);
        }
        if (landWindowSeconds < 1 || landAfterSeconds < 1 || landAfterSeconds > landWindowSeconds) {
            problems.add("land-after-seconds must be 1 to land-window-seconds (at least 1)");
        }
        if (scoutTries < 0) {
            problems.add("scout-tries must be >= 0");
        }
        if (candidates < 1) {
            problems.add("candidates must be >= 1");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid NPC orders (npc.orders):\n" + String.join("\n", problems));
        }
    }
}
