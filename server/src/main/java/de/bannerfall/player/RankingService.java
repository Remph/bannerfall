package de.bannerfall.player;

import de.bannerfall.account.AccountRepository;
import de.bannerfall.account.AccountServerSettings;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.guild.GuildRepository;
import de.bannerfall.statistics.StatisticsService;
import de.bannerfall.village.VillageRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

/** The ranking and the player profile (M14a). Players the admin deleted are in neither. */
@Service
public class RankingService {

    private final PlayerRepository players;
    private final VillageRepository villages;
    private final GuildRepository guilds;
    private final AchievementService achievements;
    private final AccountRepository accounts;
    private final AccountServerSettings accountServer;
    private final StatisticsService statistics;

    RankingService(
            PlayerRepository players,
            VillageRepository villages,
            GuildRepository guilds,
            AchievementService achievements,
            AccountRepository accounts,
            AccountServerSettings accountServer,
            StatisticsService statistics) {
        this.players = players;
        this.villages = villages;
        this.guilds = guilds;
        this.achievements = achievements;
        this.accounts = accounts;
        this.accountServer = accountServer;
        this.statistics = statistics;
    }

    @Transactional(readOnly = true)
    public RankingPageDto ranking(int page, int size, RankingKind by) {
        long offset = (long) page * size;
        List<RankingRow> rows =
                by.stat() == null
                        ? players.findRankingPage(size, offset)
                        : players.findStatRankingPage(by.stat().name(), size, offset);
        List<RankingEntryDto> entries =
                rows.stream()
                        .map(
                                row ->
                                        new RankingEntryDto(
                                                row.getRank(),
                                                row.getPlayerId(),
                                                row.getName(),
                                                row.getNpc(),
                                                row.getPoints(),
                                                row.getVillageCount(),
                                                row.getValue(),
                                                row.getGuildId(),
                                                row.getGuildTag()))
                        .toList();
        return new RankingPageDto(page, size, players.countRanked(), entries);
    }

    @Transactional(readOnly = true)
    public PlayerProfileDto profile(long playerId) {
        long rank =
                players.findRank(playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        Player player = players.findById(playerId).orElseThrow();
        return new PlayerProfileDto(
                player.getId(),
                player.getName(),
                player.isNpc(),
                player.getPoints(),
                rank,
                villages.findProfileVillagesByPlayerId(playerId),
                guilds.findRefOf(playerId).orElse(null),
                player.isNpc() ? List.of() : achievements.earned(playerId),
                statistics.stats(playerId),
                careerUrl(player));
    }

    /** The player's career page at the account server, if this world reports his career there. */
    private String careerUrl(Player player) {
        if (!accountServer.careerReports() || player.getAccountId() == null) {
            return null;
        }
        return accounts.findById(player.getAccountId())
                .filter(account -> accountServer.issuer().equals(account.getExternalIssuer()))
                .map(
                        account ->
                                accountServer.issuer()
                                        + "/career/"
                                        + UriUtils.encodePathSegment(
                                                account.getExternalSubject(),
                                                StandardCharsets.UTF_8))
                .orElse(null);
    }
}
