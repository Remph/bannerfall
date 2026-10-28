package de.bannerfall.guild;

import de.bannerfall.rules.GuildEffect;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The numbers of the guild banner (1.7, 02-game-rules §16.4), bound from {@code
 * gamedata/guild-banner.yml}. The constructor validates them, so a broken file stops the
 * application at startup. Level 1 switches on the production bonus, level 2 the support bonus,
 * level 3 the defence bonus; there are as many levels as prices.
 *
 * @param memberPrices what level 1, 2, … cost per member, in resources of any mix
 * @param production level 1: production of wood, clay and iron + (a fraction)
 * @param supportHaste level 2: travel time of supports to a guild mate − (a fraction)
 * @param defence level 3: defence +
 * @param lockHours how long after a member left or was removed no level can be raised
 */
@ConfigurationProperties("guild-banner")
public record GuildBannerCatalog(
        List<Long> memberPrices,
        double production,
        double supportHaste,
        double defence,
        int lockHours) {

    /** How many levels there are: one effect each. */
    public static final int LEVELS = 3;

    public GuildBannerCatalog {
        memberPrices = memberPrices == null ? List.of() : List.copyOf(memberPrices);
        List<String> problems = new ArrayList<>();
        if (memberPrices.size() != LEVELS) {
            problems.add("member-prices needs " + LEVELS + " entries");
        }
        if (memberPrices.stream().anyMatch(price -> price <= 0)) {
            problems.add("member-prices must be positive");
        }
        for (double value : new double[] {production, supportHaste, defence}) {
            if (value < 0 || value >= 1) {
                problems.add("an effect must be at least 0 and below 1: " + value);
            }
        }
        if (lockHours < 0) {
            problems.add("lock-hours must not be negative");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid guild banner data: " + problems);
        }
    }

    /** The price of {@code level} (1–3) for a guild with {@code members} members. */
    public long price(int level, long members) {
        if (level < 1 || level > LEVELS) {
            throw new IllegalArgumentException("level must be 1.." + LEVELS + ": " + level);
        }
        return memberPrices.get(level - 1) * Math.max(1, members);
    }

    /** What a banner of {@code level} (0–3) does: every effect up to that level is on. */
    public GuildEffect effect(int level) {
        return new GuildEffect(
                level >= 1 ? production : 0,
                level >= 2 ? supportHaste : 0,
                level >= 3 ? defence : 0);
    }
}
