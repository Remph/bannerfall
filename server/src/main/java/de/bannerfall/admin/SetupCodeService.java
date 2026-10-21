package de.bannerfall.admin;

import de.bannerfall.account.Account;
import de.bannerfall.account.AccountRepository;
import de.bannerfall.account.Role;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The first admin of a new world (1.6, guided world setup, 08-host-console §5): {@code
 * bannerfall-admin setup} asks for a one-time code, and the logged-in account that sends it becomes
 * an admin and lands in the setup wizard. Pre-game only. One code at a time, kept in memory (a
 * restart forgets it), valid for {@link #LIFETIME} and used once.
 */
@Service
public class SetupCodeService {

    static final Duration LIFETIME = Duration.ofMinutes(30);
    static final int CODE_LENGTH = 10;

    /** No look-alikes (i, l, o, 0, 1): the host may type it from a terminal. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final WorldService world;
    private final AccountRepository accounts;
    private final PlayerRepository players;
    private final AdminAuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<Pending> pending = new AtomicReference<>();

    private record Pending(String code, Instant expiresAt) {}

    SetupCodeService(
            WorldService world,
            AccountRepository accounts,
            PlayerRepository players,
            AdminAuditService audit,
            Clock clock) {
        this.world = world;
        this.accounts = accounts;
        this.players = players;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * A new code; an older one stops working.
     *
     * @throws ApiException {@code 409 WORLD_ALREADY_OPEN} once the world is open
     */
    public SetupCodeDto newCode() {
        requirePregame();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        Pending next = new Pending(code.toString(), clock.instant().plus(LIFETIME));
        pending.set(next);
        return new SetupCodeDto(next.code(), next.expiresAt());
    }

    /**
     * Makes the account an admin if {@code code} is the current code; the code is used up either
     * way it matches. An admin who claims stays an admin.
     *
     * @throws ApiException {@code 400 SETUP_CODE_INVALID} for a wrong, used or expired code, {@code
     *     409 WORLD_ALREADY_OPEN} once the world is open
     */
    @Transactional
    public void claim(long accountId, String code) {
        requirePregame();
        Pending current = pending.get();
        if (current == null
                || !clock.instant().isBefore(current.expiresAt())
                || !matches(current.code(), code)
                || !pending.compareAndSet(current, null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.SETUP_CODE_INVALID,
                    "This setup code is wrong, used or expired; run `bannerfall-admin setup`"
                            + " again");
        }
        Account account = accounts.findById(accountId).orElseThrow();
        Role before = account.getRole();
        if (before != Role.ADMIN) {
            account.changeRole(Role.ADMIN);
        }
        Long playerId = players.findByAccountId(accountId).map(Player::getId).orElse(null);
        audit.record(
                "SETUP_CLAIM",
                "player",
                playerId,
                Map.of("name", account.getUsername(), "from", before, "to", Role.ADMIN));
    }

    private void requirePregame() {
        if (world.phase() != WorldPhase.PREGAME) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_ALREADY_OPEN,
                    "The world is open already; make admins with `player <name> role admin`");
        }
    }

    /** Ignores case and spaces around it, in constant time. */
    private static boolean matches(String expected, String given) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                given.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
