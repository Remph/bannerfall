package de.bannerfall.player;

import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository players;
    private final WorldService worldService;
    private final Clock clock;

    PlayerService(PlayerRepository players, WorldService worldService, Clock clock) {
        this.players = players;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Creates the player of a newly registered account, with beginner protection (not scaled by
     * speed). The starting village is created separately ({@code SpawnService}).
     */
    @Transactional
    public Player createForAccount(long accountId, String name) {
        Instant now = clock.instant();
        Duration protection = Duration.ofHours(worldService.settings().beginnerProtectionHours());
        return players.save(Player.human(accountId, name, now.plus(protection), now));
    }

    /**
     * The player of a logged-in account. Every account has one from M3 on (registration creates
     * both), so a missing player is a bug, not a client error.
     */
    @Transactional(readOnly = true)
    public Player getByAccountId(long accountId) {
        return players.findByAccountId(accountId)
                .orElseThrow(
                        () -> new IllegalStateException("Account " + accountId + " has no player"));
    }

    /**
     * Sending an attack on a player's village ends the sender's beginner protection; an attack on a
     * barbarian village doesn't (02-game-rules §6, M25). A bulk update (see {@link
     * PlayerRepository#endProtection}): a {@code Player} already loaded in this transaction keeps
     * the old value.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void endProtection(long playerId, Instant now) {
        players.endProtection(playerId, now);
    }

    /** Shortcut for controllers: the id of the current player. */
    @Transactional(readOnly = true)
    public long idOf(long accountId) {
        return getByAccountId(accountId).getId();
    }
}
