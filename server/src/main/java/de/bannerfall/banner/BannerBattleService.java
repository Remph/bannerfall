package de.bannerfall.banner;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandBanner;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.TheftResult;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a battle does with banners after it is decided (02-game-rules §21.6.2, §21.7): the banner
 * thieves' roll and the drop roll of the winner, and for paladin standards (1.4, §21.18) the extra
 * drop roll and the defenders' thieves' roll on a lost attack. Called by {@code BattleService} in
 * the battle's transaction, which holds the locks of the attacker's home and the target. NPC lords
 * collect and steal only while lord banners are on (1.4, 09-npc-lords §14): then a lord that wins
 * rolls like a player, without toast or achievement; otherwise it rolls nothing.
 *
 * <p>Order of the dice in a battle: luck, the nobles' loyalty hits, then the theft roll (one draw),
 * then the drop roll (one draw, and two more for tier and type when it drops), then the standard
 * drop roll (the same), then the defenders' standard theft roll (one draw).
 */
@Service
public class BannerBattleService {

    /** The window of {@code drops.maxPerDay}: 24 real hours, not scaled by the world speed. */
    private static final Duration DAY = Duration.ofHours(24);

    private final BannerRepository banners;
    private final BannerEventRepository events;
    private final BannerCatalog catalog;
    private final WorldService worldService;
    private final RandomSource random;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher notifications;
    private final AchievementService achievements;
    private final GameData gameData;
    private final PlayerRepository players;

    BannerBattleService(
            BannerRepository banners,
            BannerEventRepository events,
            BannerCatalog catalog,
            WorldService worldService,
            RandomSource random,
            JdbcClient jdbc,
            ApplicationEventPublisher notifications,
            AchievementService achievements,
            GameData gameData,
            PlayerRepository players) {
        this.banners = banners;
        this.events = events;
        this.catalog = catalog;
        this.worldService = worldService;
        this.random = random;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.achievements = achievements;
        this.gameData = gameData;
        this.players = players;
    }

    /**
     * The loser of a battle for the drop filters: a player (an NPC lord too) or one barbarian
     * village.
     *
     * @param key {@code banner_roll.loser_key}
     * @param barbarian true: the lower chance against barbarians
     */
    public record Loser(String key, boolean barbarian) {

        public static Loser player(long playerId) {
            return new Loser("P" + playerId, false);
        }

        public static Loser barbarianVillage(long villageId) {
            return new Loser("V" + villageId, true);
        }

        /** The side of {@code village} when it loses: its owner, or the barbarian village. */
        public static Loser defenderOf(Village village) {
            Player owner = village.getPlayer();
            return owner == null ? barbarianVillage(village.getId()) : player(owner.getId());
        }
    }

    /**
     * The winner of a battle rolls for a banner (02-game-rules §21.7). Nothing happens, and no roll
     * is used up, when banners or drops are switched off, the winner is no human player, the losing
     * side fought with less than {@code drops.minLoserPop} population or the winner has found
     * {@code drops.maxPerDay} banners in the last 24 real hours. Otherwise the pair of winner and
     * loser rolls at most once per {@code drops.pairHours} real hours: a roll inside that window is
     * skipped. A banner that drops goes to the winner's stash (origin FOUND, a FOUND line in the
     * log) and the winner gets the BANNER_FOUND toast.
     *
     * @param winner the attacker who won, or the owner of the village that held
     * @param loserPop the population the losing side fought with: the attacker's army, or every
     *     unit that stood in the village before the battle; against barbarians the attacker's army
     * @param village the village the battle was fought at (for the log and the toast)
     * @return the banner that dropped, empty if none
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BattleReport.ScoutedBanner> rollDrop(
            Player winner, Loser loser, int loserPop, Village village, Instant at) {
        return roll(winner, loser, loserPop, village, at, false);
    }

    /**
     * The winner of a battle his own paladin fought in rolls once more, for a paladin standard
     * (1.4, 02-game-rules §21.18): like {@link #rollDrop}, with the same chances and tier odds, the
     * kind random among the seven, but with a pair window and a daily cap of its own. A standard
     * counts for no banner achievement. The caller decides whether the winner's paladin fought.
     *
     * @return the standard that dropped, empty if none
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BattleReport.ScoutedBanner> rollStandardDrop(
            Player winner, Loser loser, int loserPop, Village village, Instant at) {
        return roll(winner, loser, loserPop, village, at, true);
    }

    /** {@link #rollDrop} for a village banner or, with {@code standard}, a paladin standard. */
    private Optional<BattleReport.ScoutedBanner> roll(
            Player winner,
            Loser loser,
            int loserPop,
            Village village,
            Instant at,
            boolean standard) {
        WorldSettings world = worldService.settings();
        BannerCatalog.Drops drops = catalog.drops();
        List<BannerType> types = standard ? BannerType.standards() : BannerType.villageBanners();
        // the standards' pair window is its own: their keys start with S
        String pair = standard ? "S" + loser.key() : loser.key();
        if (!world.bannersEnabled()
                || world.bannerDropFactor() <= 0
                || winner == null
                || (winner.isNpc() && !world.lordBanners())
                || loserPop < drops.minLoserPop()
                || events.countByKindAndToPlayerIdAndHappenedAtAfterAndTypeIn(
                                BannerEventKind.FOUND, winner.getId(), at.minus(DAY), types)
                        >= drops.maxPerDay()
                || !takePairRoll(winner.getId(), pair, at, drops.pairHours())) {
            return Optional.empty();
        }
        double base = loser.barbarian() ? drops.barbarianChance() : drops.playerChance();
        double chance = Math.min(1, base * world.bannerDropFactor());
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        int tier = BannerRules.dropTier(random.nextDouble(), drops.tierOdds());
        BannerType type = types.get(random.nextInt(0, types.size() - 1));

        Banner banner =
                banners.save(new Banner(winner.getId(), type, tier, BannerOrigin.FOUND, at));
        events.save(
                new BannerEvent(
                        banner,
                        BannerEventKind.FOUND,
                        null,
                        winner.getId(),
                        village.getId(),
                        at,
                        null));
        if (winner.isNpc()) {
            return Optional.of(new BattleReport.ScoutedBanner(type, tier)); // no toast, no honour
        }
        if (!standard) {
            achievements.grant(winner, Achievement.STANDARD_BEARER);
            achievements.checkBanners(winner);
        }
        notifications.publishEvent(
                new PlayerNotification(
                        winner.getId(),
                        new GameNotification.BannerFound(type, tier, VillageRefDto.of(village))));
        return Optional.of(new BattleReport.ScoutedBanner(type, tier));
    }

    /**
     * Whether an attack with these units can win a banner roll against the loser at all
     * (02-game-rules §21.7): not with scouts alone (there is no main battle), and against a
     * barbarian village only with at least {@code drops.minLoserPop} population, since the
     * attacker's army is what counts there. Against a player the defenders decide, which the
     * attacker can't know. The farm list's "Send banner chances" (§18) leaves out the rest.
     */
    public boolean attackCanRoll(Loser loser, Map<UnitType, Integer> units) {
        if (units.entrySet().stream()
                .noneMatch(e -> e.getKey() != UnitType.SCOUT && e.getValue() > 0)) {
            return false;
        }
        if (!loser.barbarian()) {
            return true;
        }
        int pop = 0;
        for (var entry : units.entrySet()) {
            pop += entry.getValue() * gameData.unit(entry.getKey()).pop();
        }
        return pop >= catalog.drops().minLoserPop();
    }

    /**
     * When the winner may roll against each of these losers again (02-game-rules §21.7, shown in
     * the farm list): the end of the pair window for every pair that rolled inside it; a loser the
     * winner may roll against now is missing. Empty when banners or drops are switched off. Only
     * reads: the size filter and the daily cap still decide at the battle.
     *
     * @param loserKeys {@link Loser#key()} of each loser
     */
    public Map<String, Instant> nextRolls(
            long winnerId, Collection<String> loserKeys, Instant now) {
        WorldSettings world = worldService.settings();
        if (!world.bannersEnabled() || world.bannerDropFactor() <= 0 || loserKeys.isEmpty()) {
            return Map.of();
        }
        Duration window = Duration.ofHours(catalog.drops().pairHours());
        return jdbc
                .sql(
                        """
                        select loser_key, rolled_at from banner_roll
                        where winner_id = :winner and loser_key in (:losers) and rolled_at > :cutoff
                        """)
                .param("winner", winnerId)
                .param("losers", Set.copyOf(loserKeys))
                .param("cutoff", java.sql.Timestamp.from(now.minus(window)))
                .query(
                        (rs, n) ->
                                Map.entry(
                                        rs.getString("loser_key"),
                                        rs.getTimestamp("rolled_at").toInstant().plus(window)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The banner thieves of an attack try their luck (02-game-rules §21.6.2): after a won main
     * battle that doesn't conquer the village, with an equipped banner there (a dormant one too)
     * and at least one surviving thief, one draw against {@link BannerRules#theftChance}. On
     * success the banner goes to the attacker's stash (a STOLEN line in the log) and both players
     * get the BANNER_STOLEN toast; the thieves are not used up. The target is locked and caught up
     * to {@code at}, so taking the banner away needs no catch-up.
     *
     * @param target the attacked village, still the defender's
     * @param attackerId the owner of the attacking units
     * @param sent thieves in the attack, at least one
     * @param survived thieves alive after the battle
     * @param thiefLevel the attacker's thief level at the battle (Hall training, 1.4)
     * @param won the attacker won the main battle
     * @param conquered the battle conquers the village (its banner goes with the conquest)
     * @param hallLevel the target's Banner Hall after the battle (catapults may have hit it)
     * @return what the thieves did for the attacker's report; null with banners switched off
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BattleReport.TheftReport steal(
            Village target,
            long attackerId,
            int sent,
            int survived,
            int thiefLevel,
            boolean won,
            boolean conquered,
            int hallLevel,
            Instant at) {
        if (!worldService.settings().bannersEnabled()) {
            return null;
        }
        if (!won) {
            return theft(sent, survived, null, TheftResult.BATTLE_LOST, null);
        }
        if (conquered) {
            return theft(sent, survived, null, TheftResult.CONQUERED, null);
        }
        if (survived == 0) {
            // dead thieves tell nothing, not even whether a banner flies there
            return theft(sent, survived, null, TheftResult.NO_SURVIVORS, null);
        }
        Banner banner =
                target.getPlayer() == null
                        ? null // barbarian villages carry no banner
                        : banners.findFlying(target.getId()).orElse(null);
        if (banner == null) {
            return theft(sent, survived, null, TheftResult.NO_BANNER, null);
        }
        BannerCatalog.Theft rules = catalog.theft();
        double chance =
                BannerRules.theftChance(
                        survived,
                        hallLevel,
                        rules.perThief(thiefLevel),
                        rules.maxChance(),
                        rules.hallReductionPerLevel());
        var seen = new BattleReport.ScoutedBanner(banner.getType(), banner.getTier());
        if (random.nextDouble() >= chance) {
            return theft(sent, survived, chance, TheftResult.FAILED, seen);
        }

        long from = banner.getOwnerId();
        banner.passTo(attackerId);
        BannerEvent line =
                events.save(
                        new BannerEvent(
                                banner,
                                BannerEventKind.STOLEN,
                                from,
                                attackerId,
                                target.getId(),
                                at,
                                null));
        achievements.bannerTaken(attackerId, banner.getId(), line.getId());
        achievements.grant(from, Achievement.COLOURS_LOST);
        VillageRefDto village = VillageRefDto.of(target);
        Player victim = target.getPlayer();
        if (!victim.isNpc()) {
            notifications.publishEvent(
                    new PlayerNotification(
                            from,
                            new GameNotification.BannerStolen(
                                    banner.getType(), banner.getTier(), village, true)));
        }
        if (!isNpc(attackerId)) { // lord thieves on a retaliation (09-npc-lords §14.4)
            notifications.publishEvent(
                    new PlayerNotification(
                            attackerId,
                            new GameNotification.BannerStolen(
                                    banner.getType(), banner.getTier(), village, false)));
        }
        return theft(sent, survived, chance, TheftResult.STOLEN, seen);
    }

    /**
     * The defenders' thieves take the paladin standard of an attack they beat (1.4, 02-game-rules
     * §21.18, admin setting {@code standardTheftEnabled}): after a lost attack that carried a
     * standard, with at least one of the village owner's own thieves at home alive, one draw
     * against {@code min(maxChance, 1 − (1 − c)^k)}, c by the defender's thief level, without the
     * Hall reduction. On success the standard leaves the attacker's village for the defender's
     * stash (a STOLEN line in the log) and both players get the BANNER_STOLEN toast. Nothing
     * happens (null) with banners or the setting off, without a carried standard, against a
     * barbarian village, against an NPC lord while lord banners are off (09-npc-lords §14.1),
     * without a surviving thief, or when the standard is no longer where it was when the attack
     * left (its village was conquered meanwhile). Standards count for no banner achievement. The
     * battle holds the locks of both villages.
     *
     * @param carried the standard the attack took along; null without
     * @param attackerId the owner of the attacking units
     * @param attackerHomeId the village the attack came from, where the standard is equipped
     * @param target the defended village
     * @param survivors the village owner's own thieves at home that survived the battle
     * @param thiefLevel the defender's thief level at the battle
     * @return the roll for the reports; null if there was none
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BattleReport.StandardTheftReport stealStandard(
            CommandBanner.Standard carried,
            long attackerId,
            long attackerHomeId,
            Village target,
            int survivors,
            int thiefLevel,
            Instant at) {
        WorldSettings world = worldService.settings();
        Player defender = target.getPlayer();
        if (!world.bannersEnabled()
                || !world.standardTheftEnabled()
                || carried == null
                || defender == null
                || (defender.isNpc() && !world.lordBanners())
                || survivors <= 0) {
            return null;
        }
        Banner standard =
                banners.findById(carried.bannerId())
                        .filter(
                                banner ->
                                        banner.getOwnerId() == attackerId
                                                && Long.valueOf(attackerHomeId)
                                                        .equals(banner.getEquippedVillageId()))
                        .orElse(null);
        if (standard == null) {
            return null;
        }
        BannerCatalog.Theft rules = catalog.theft();
        double chance =
                BannerRules.theftChance(
                        survivors, 0, rules.perThief(thiefLevel), rules.maxChance(), 0);
        var seen = new BattleReport.ScoutedBanner(standard.getType(), standard.getTier());
        if (random.nextDouble() >= chance) {
            return new BattleReport.StandardTheftReport(survivors, chance, false, seen);
        }
        standard.passTo(defender.getId());
        events.save(
                new BannerEvent(
                        standard,
                        BannerEventKind.STOLEN,
                        attackerId,
                        defender.getId(),
                        attackerHomeId,
                        at,
                        null));
        VillageRefDto village = VillageRefDto.of(target);
        if (!isNpc(attackerId)) {
            notifications.publishEvent(
                    new PlayerNotification(
                            attackerId,
                            new GameNotification.BannerStolen(
                                    standard.getType(), standard.getTier(), village, true)));
        }
        if (!defender.isNpc()) {
            notifications.publishEvent(
                    new PlayerNotification(
                            defender.getId(),
                            new GameNotification.BannerStolen(
                                    standard.getType(), standard.getTier(), village, false)));
        }
        return new BattleReport.StandardTheftReport(survivors, chance, true, seen);
    }

    private boolean isNpc(long playerId) {
        return players.findById(playerId).map(Player::isNpc).orElse(false);
    }

    private static BattleReport.TheftReport theft(
            int sent,
            int survived,
            Double chance,
            TheftResult result,
            BattleReport.ScoutedBanner banner) {
        return new BattleReport.TheftReport(sent, survived, chance, result, banner);
    }

    /**
     * Uses up the pair's roll: true if the winner hasn't rolled against this loser in the last
     * {@code pairHours} real hours (it then has now), false if he has. One upsert, like the battle
     * credits of the achievements.
     */
    private boolean takePairRoll(long winnerId, String loserKey, Instant at, int pairHours) {
        Instant cutoff = at.minus(Duration.ofHours(pairHours));
        int changed =
                jdbc.sql(
                                """
                                insert into banner_roll (winner_id, loser_key, rolled_at)
                                values (:winner, :loser, :at)
                                on conflict (winner_id, loser_key)
                                do update set rolled_at = excluded.rolled_at
                                where banner_roll.rolled_at <= :cutoff
                                """)
                        .param("winner", winnerId)
                        .param("loser", loserKey)
                        .param("at", java.sql.Timestamp.from(at))
                        .param("cutoff", java.sql.Timestamp.from(cutoff))
                        .update();
        return changed == 1;
    }
}
