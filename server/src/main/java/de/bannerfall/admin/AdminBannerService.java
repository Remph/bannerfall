package de.bannerfall.admin;

import de.bannerfall.account.AccountRepository;
import de.bannerfall.banner.BannerDto;
import de.bannerfall.banner.BannerService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's banner operations (02-game-rules §21.9, 08-host-console): list a player's banners,
 * hand one out, take one away. Every change writes an audit row in its own transaction. Until the
 * battles drop banners this is how a world gets them.
 */
@Service
public class AdminBannerService {

    private final BannerService banners;
    private final PlayerRepository players;
    private final AccountRepository accounts;
    private final AdminAuditService audit;

    AdminBannerService(
            BannerService banners,
            PlayerRepository players,
            AccountRepository accounts,
            AdminAuditService audit) {
        this.banners = banners;
        this.players = players;
        this.accounts = accounts;
        this.audit = audit;
    }

    /**
     * The banners of a human player who can still play.
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<BannerDto> list(long playerId) {
        activePlayer(playerId);
        return banners.bannersOf(playerId);
    }

    /**
     * Gives the player a new banner in his stash.
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND}
     */
    @Transactional
    public BannerDto grant(long playerId, BannerType type, int tier, String reason) {
        Player player = activePlayer(playerId);
        String why = reason == null ? "" : reason;
        BannerDto banner = banners.grant(playerId, type, tier, why);
        audit.record(
                "GRANT_BANNER",
                "player",
                playerId,
                Map.of(
                        "name",
                        player.getName(),
                        "banner",
                        banner.id(),
                        "type",
                        type.name(),
                        "tier",
                        tier,
                        "reason",
                        why));
        return banner;
    }

    /**
     * Takes a banner away for good, wherever it is.
     *
     * @throws NotFoundException {@code BANNER_NOT_FOUND}
     */
    @Transactional
    public BannerDto revoke(long bannerId, String reason) {
        String why = reason == null ? "" : reason;
        BannerDto banner = banners.revoke(bannerId, why);
        audit.record(
                "REVOKE_BANNER",
                "banner",
                bannerId,
                Map.of("type", banner.type().name(), "tier", banner.tier(), "reason", why));
        return banner;
    }

    private Player activePlayer(long playerId) {
        Player player =
                players.findById(playerId)
                        .filter(p -> p.getAccountId() != null)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        accounts.findById(player.getAccountId())
                .filter(account -> !account.isDisabled())
                .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        return player;
    }
}
