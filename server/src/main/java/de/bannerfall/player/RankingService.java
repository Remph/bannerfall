package de.bannerfall.player;

import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.guild.GuildRepository;
import de.bannerfall.village.VillageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The ranking and the player profile (M14a). Players the admin deleted are in neither. */
@Service
public class RankingService {

    private final PlayerRepository players;
    private final VillageRepository villages;
    private final GuildRepository guilds;
    private final AchievementService achievements;

    RankingService(
            PlayerRepository players,
            VillageRepository villages,
            GuildRepository guilds,
            AchievementService achievements) {
        this.players = players;
        this.villages = villages;
        this.guilds = guilds;
        this.achievements = achievements;
    }

    @Transactional(readOnly = true)
    public RankingPageDto ranking(int page, int size) {
        List<RankingEntryDto> entries =
                players.findRankingPage(size, (long) page * size).stream()
                        .map(
                                row ->
                                        new RankingEntryDto(
                                                row.getRank(),
                                                row.getPlayerId(),
                                                row.getName(),
                                                row.getNpc(),
                                                row.getPoints(),
                                                row.getVillageCount(),
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
                player.isNpc() ? List.of() : achievements.earned(playerId));
    }
}
