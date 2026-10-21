package de.bannerfall.account;

import de.bannerfall.player.Player;
import de.bannerfall.village.VillageSummaryDto;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/me (also the answer to register and login): the account plus its player.
 *
 * @param needsRespawn the player lost all his villages (conquered, 02-game-rules §10) and may start
 *     again with {@code POST /api/me/respawn}; {@code villages} is empty then. Never in the
 *     pre-game (M14g): the player has no village yet because the world has not opened
 * @param villages the player's villages, oldest first
 * @param linked the account logs in through the account server and has no password here (1.5)
 * @param respawnNearGuild while {@code needsRespawn}: a guild mate has a village the new start can
 *     go next to (1.6, 02-game-rules §2), so the respawn screen offers the choice
 */
public record MeDto(
        long accountId,
        String username,
        Role role,
        long playerId,
        int points,
        Instant protectedUntil,
        boolean needsRespawn,
        List<VillageSummaryDto> villages,
        boolean linked,
        boolean respawnNearGuild) {

    static MeDto of(
            Account account,
            Player player,
            List<VillageSummaryDto> villages,
            boolean pregame,
            boolean guildMateHasVillage) {
        boolean needsRespawn = !pregame && !player.isNpc() && villages.isEmpty();
        return new MeDto(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                player.getId(),
                player.getPoints(),
                player.getProtectedUntil(),
                needsRespawn,
                villages,
                account.isLinked(),
                needsRespawn && guildMateHasVillage);
    }
}
