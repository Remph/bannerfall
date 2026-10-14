package de.bannerfall.banner;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.TheftResult;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a battle does with banners after it is decided (02-game-rules §21.6.2, §21.7): the banner
 * thieves' roll and the drop roll of the winner. Called by {@code BattleService} in the battle's
 * transaction, which holds the locks of the attacker's home and the target. NPC lords neither steal
 * nor collect (§21.11): they never have thieves, and a lord that wins rolls nothing.
 *
 * <p>Order of the dice in a battle: luck, the nobles' loyalty hits, then the theft roll (one draw),
 * then the drop roll (one draw, and two more for tier and type when it drops).
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

    BannerBattleService(
            BannerRepository banners,
            BannerEventRepository events,
            BannerCatalog catalog,
            WorldService worldService,
            RandomSource random,
            JdbcClient jdbc,
            ApplicationEventPublisher notifications,
            AchievementService achievements) {
        this.banners = banners;
        this.events = events;
        this.catalog = catalog;
        this.worldService = worldService;
        this.random = random;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.achievements = achievements;
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
        WorldSettings world = worldService.settings();
        BannerCatalog.Drops drops = catalog.drops();
        if (!world.bannersEnabled()
                || world.bannerDropFactor() <= 0
                || winner == null
                || winner.isNpc()
                || loserPop < drops.minLoserPop()
                || events.countByKindAndToPlayerIdAndHappenedAtAfter(
                                BannerEventKind.FOUND, winner.getId(), at.minus(DAY))
                        >= drops.maxPerDay()
                || !takePairRoll(winner.getId(), loser.key(), at, drops.pairHours())) {
            return Optional.empty();
        }
        double base = loser.barbarian() ? drops.barbarianChance() : drops.playerChance();
        double chance = Math.min(1, base * world.bannerDropFactor());
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        int tier = BannerRules.dropTier(random.nextDouble(), drops.tierOdds());
        BannerType[] types = BannerType.values();
        BannerType type = types[random.nextInt(0, types.length - 1)];

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
        achievements.grant(winner, Achievement.STANDARD_BEARER);
        achievements.checkBanners(winner);
        notifications.publishEvent(
                new PlayerNotification(
                        winner.getId(),
                        new GameNotification.BannerFound(type, tier, VillageRefDto.of(village))));
        return Optional.of(new BattleReport.ScoutedBanner(type, tier));
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
                        : banners.findByEquippedVillageId(target.getId()).orElse(null);
        if (banner == null) {
            return theft(sent, survived, null, TheftResult.NO_BANNER, null);
        }
        BannerCatalog.Theft rules = catalog.theft();
        double chance =
                BannerRules.theftChance(
                        survived,
                        hallLevel,
                        rules.chancePerThief(),
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
        notifications.publishEvent(
                new PlayerNotification(
                        attackerId,
                        new GameNotification.BannerStolen(
                                banner.getType(), banner.getTier(), village, false)));
        return theft(sent, survived, chance, TheftResult.STOLEN, seen);
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
