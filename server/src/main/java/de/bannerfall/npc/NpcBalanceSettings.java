package de.bannerfall.npc;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The general NPC numbers and the difficulties, bound from {@code gamedata/npc/balance.yml}
 * (09-npc-lords §9). Invalid values stop the application at startup.
 */
@ConfigurationProperties("npc")
public record NpcBalanceSettings(Balance balance) {

    public NpcBalanceSettings {
        if (balance == null) {
            throw new IllegalStateException("Invalid NPC balance: npc.balance is missing");
        }
    }

    /**
     * @param thinkJitter a round's interval varies by ±this share
     * @param ledgerDecay the spending ledger is multiplied by this each round
     * @param guildRadius in fields: lords this close form guilds (09 §13.1)
     * @param difficulties EASY, NORMAL and HARD (CUSTOM lives in {@code world_settings.npc_custom})
     */
    public record Balance(
            double thinkJitter,
            double ledgerDecay,
            double intelMaxAgeHours,
            double farmMargin,
            double farmMaxLoss,
            double attackMaxLoss,
            double farmCooldownMinutes,
            int farmsPerRound,
            double nobleTroopShare,
            double supportHorizonHours,
            double grudgeHours,
            double guildRadius,
            String quietFrom,
            String quietTo,
            String quietZone,
            Map<NpcDifficulty, Difficulty> difficulties) {

        public Balance {
            var copy = new EnumMap<NpcDifficulty, Difficulty>(NpcDifficulty.class);
            if (difficulties != null) {
                copy.putAll(difficulties);
            }
            difficulties = Collections.unmodifiableMap(copy);
            List<String> problems = new ArrayList<>();
            inRange(problems, "think-jitter", thinkJitter, 0, 0.9);
            inRange(problems, "ledger-decay", ledgerDecay, 0.5, 1);
            inRange(problems, "noble-troop-share", nobleTroopShare, 0, 1);
            positive(problems, "intel-max-age-hours", intelMaxAgeHours);
            positive(problems, "farm-margin", farmMargin);
            inRange(problems, "farm-max-loss", farmMaxLoss, 0, 1);
            inRange(problems, "attack-max-loss", attackMaxLoss, 0, 1);
            positive(problems, "farm-cooldown-minutes", farmCooldownMinutes);
            positive(problems, "support-horizon-hours", supportHorizonHours);
            positive(problems, "grudge-hours", grudgeHours);
            positive(problems, "guild-radius", guildRadius);
            if (farmsPerRound < 1) {
                problems.add("farms-per-round must be >= 1");
            }
            if (!isTime(quietFrom) || !isTime(quietTo)) {
                problems.add("quiet-from and quiet-to must be HH:mm");
            }
            if (!isZone(quietZone)) {
                problems.add("quiet-zone must be a time zone id");
            }
            for (NpcDifficulty d :
                    new NpcDifficulty[] {
                        NpcDifficulty.EASY, NpcDifficulty.NORMAL, NpcDifficulty.HARD
                    }) {
                Difficulty entry = copy.get(d);
                if (entry == null) {
                    problems.add("difficulties." + d + ": missing");
                } else {
                    entry.problems("difficulties." + d, problems);
                }
            }
            if (copy.containsKey(NpcDifficulty.CUSTOM)) {
                problems.add(
                        "difficulties.CUSTOM: not allowed here, it lives in the world settings");
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Invalid NPC balance:\n" + String.join("\n", problems));
            }
        }

        public LocalTime quietFromTime() {
            return LocalTime.parse(quietFrom);
        }

        public LocalTime quietToTime() {
            return LocalTime.parse(quietTo);
        }

        public ZoneId zone() {
            return ZoneId.of(quietZone);
        }

        /** The values of a difficulty; CUSTOM is {@code custom} (NORMAL while it is null). */
        public Difficulty difficulty(NpcDifficulty difficulty, Difficulty custom) {
            if (difficulty == NpcDifficulty.CUSTOM) {
                return custom != null ? custom : difficulties.get(NpcDifficulty.NORMAL);
            }
            return difficulties.get(difficulty);
        }
    }

    /**
     * One difficulty (09 §8): also the shape of the CUSTOM values the admin edits. Times are in the
     * {@code quiet-zone}.
     *
     * @param farmLootShare the farm budget (09 §6.2, v1.3): a lord farms only while its loot of the
     *     last 24 real hours is below this share of its own daily production; 0 = no budget (also
     *     what CUSTOM values stored before v1.3 read as)
     * @param farmRest the lords send no farms or raids during the build quiet hours either, like a
     *     player who sleeps (09 §6.2, v1.3)
     */
    public record Difficulty(
            double productionFactor,
            double boostBelow,
            double boostFactor,
            double leadMargin,
            int attacksPerPlayerPerDay,
            int attacksPerHumanPerDay,
            boolean quietHours,
            boolean spareLastVillage,
            String buildQuietFrom,
            String buildQuietTo,
            double farmLootShare,
            boolean farmRest) {

        /** All problems of these values (empty = valid), each prefixed with {@code name}. */
        public List<String> problems(String name) {
            List<String> problems = new ArrayList<>();
            problems(name, problems);
            return problems;
        }

        public LocalTime buildQuietFromTime() {
            return LocalTime.parse(buildQuietFrom);
        }

        public LocalTime buildQuietToTime() {
            return LocalTime.parse(buildQuietTo);
        }

        void problems(String name, List<String> problems) {
            List<String> own = new ArrayList<>();
            positive(own, "production-factor", productionFactor);
            positive(own, "boost-factor", boostFactor);
            positive(own, "lead-margin", leadMargin);
            if (boostBelow < 0) {
                own.add("boost-below must be >= 0");
            }
            if (attacksPerPlayerPerDay < 0 || attacksPerHumanPerDay < 0) {
                own.add("attack caps must be >= 0");
            }
            if (!(farmLootShare >= 0)) {
                own.add("farm-loot-share must be >= 0");
            }
            if (!isTime(buildQuietFrom) || !isTime(buildQuietTo)) {
                own.add("build-quiet-from and build-quiet-to must be HH:mm");
            }
            own.forEach(p -> problems.add(name + ": " + p));
        }
    }

    private static void inRange(
            List<String> problems, String name, double v, double min, double max) {
        if (!(v >= min && v <= max)) {
            problems.add(name + " must be in " + min + ".." + max);
        }
    }

    private static boolean isTime(String text) {
        if (text == null || !text.matches("\\d\\d:\\d\\d")) {
            return false;
        }
        try {
            LocalTime.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isZone(String text) {
        try {
            ZoneId.of(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void positive(List<String> problems, String name, double v) {
        if (!(v > 0)) {
            problems.add(name + " must be > 0");
        }
    }
}
