package de.bannerfall.account;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerService;
import de.bannerfall.village.SpawnService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageService;
import de.bannerfall.village.VillageSummaryDto;
import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {

    /** Password length, as at registration ({@link RegisterRequest}). */
    public static final int PASSWORD_MIN = 8;

    public static final int PASSWORD_MAX = 128;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final WorldService worldService;
    private final PlayerService playerService;
    private final SpawnService spawnService;
    private final VillageService villageService;
    private final BarbarianService barbarianService;
    private final LoginRateLimiter rateLimiter;
    private final AccountStates states;
    private final ApplicationEventPublisher events;
    private final AchievementService achievements;

    AccountService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            WorldService worldService,
            PlayerService playerService,
            SpawnService spawnService,
            VillageService villageService,
            BarbarianService barbarianService,
            LoginRateLimiter rateLimiter,
            AccountStates states,
            ApplicationEventPublisher events,
            AchievementService achievements) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.worldService = worldService;
        this.playerService = playerService;
        this.spawnService = spawnService;
        this.villageService = villageService;
        this.barbarianService = barbarianService;
        this.rateLimiter = rateLimiter;
        this.states = states;
        this.events = events;
        this.achievements = achievements;
    }

    /**
     * Creates a PLAYER account together with its player and starting village, all in one
     * transaction (a full world leaves no account behind), and spawns {@code
     * barbariansPerNewPlayer} barbarian villages around it (02-game-rules §2). Field formats are
     * already checked by {@link RegisterRequest}. In the pre-game (M14g) there is no map yet: the
     * account and player are created, the village comes when the world opens.
     */
    @Transactional
    public Account register(RegisterRequest request) {
        // Invite code first, so strangers can't probe which usernames exist.
        if (!inviteCodeMatches(request.inviteCode())) {
            throw new GameRuleException(ErrorCode.INVALID_INVITE_CODE, "Invalid invite code");
        }
        if (accounts.existsByUsernameKey(Account.keyOf(request.username()))) {
            throw usernameTaken();
        }
        // The slow hash before the lock below, which every registration and the opening share.
        String passwordHash = passwordEncoder.encode(request.password());
        // Under the row lock: an opening that is placing the waiting players right now finishes
        // first, and this registration then gets its village like any later one.
        boolean pregame = worldService.lockedPhase() == WorldPhase.PREGAME;
        Account account =
                new Account(request.username(), passwordHash, Role.PLAYER, clock.instant());
        try {
            account = accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same name at the same moment: the unique index decides.
            throw usernameTaken();
        }
        Player player = playerService.createForAccount(account.getId(), account.getUsername());
        if (pregame) {
            // registered before the opening (02-game-rules §19)
            achievements.grant(player, Achievement.PIONEER);
            return account;
        }
        Village village = spawnService.createStartingVillage(player);
        barbarianService.spawnAround(
                village.coordinates(), worldService.settings().barbariansPerNewPlayer());
        return account;
    }

    @Transactional(readOnly = true)
    public MeDto me(long accountId) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        Player player = playerService.getByAccountId(accountId);
        return MeDto.of(
                account,
                player,
                villageService.summariesOf(player.getId()),
                worldService.phase() == WorldPhase.PREGAME);
    }

    /**
     * The player of the account starts again with a new village ({@link SpawnService#respawn}).
     *
     * @return the new village
     */
    @Transactional
    public VillageSummaryDto respawn(long accountId) {
        // A session of a player the admin deleted may still be open (sessions are in memory, M14e).
        if (accounts.findById(accountId).orElseThrow().isDisabled()) {
            throw new GameRuleException(ErrorCode.RESPAWN_NOT_ALLOWED, "This player was deleted");
        }
        Village village = spawnService.respawn(playerService.idOf(accountId));
        return new VillageSummaryDto(
                village.getId(),
                village.getName(),
                village.getX(),
                village.getY(),
                village.getPoints(),
                0);
    }

    /**
     * Sets a new password chosen by the host (M14d, 08-host-console §2); there is no e-mail, so
     * this is the way back in for a player who forgot his. Also lifts a login block of the account
     * and ends its open sessions (M14e). The password is already checked ({@link #PASSWORD_MIN} to
     * {@link #PASSWORD_MAX} characters).
     *
     * @return the account
     */
    @Transactional
    public Account resetPassword(long accountId, String password) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.changePasswordHash(passwordEncoder.encode(password));
        rateLimiter.forget(account.getUsernameKey());
        sessionsEnded(account);
        return account;
    }

    /**
     * The player changes his own password (M14d). A wrong current password counts as a failed login
     * of the account from {@code address}, so the page can't be used to guess it faster than the
     * login. The account's other sessions end (M14e); the caller renews the session of this request
     * ({@code LoginService#renewSession}).
     *
     * @return the account with its new session version
     * @throws ApiException 429 {@code TOO_MANY_LOGIN_ATTEMPTS} while the account's logins from this
     *     address are blocked
     * @throws GameRuleException {@code WRONG_PASSWORD} if the current password is wrong
     */
    @Transactional
    public Account changePassword(
            long accountId, String currentPassword, String newPassword, String address) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        String key = account.getUsernameKey();
        if (!rateLimiter.tryAttempt(key, address)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many wrong passwords, try again in a minute");
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new GameRuleException(ErrorCode.WRONG_PASSWORD, "The current password is wrong");
        }
        rateLimiter.recordSuccess(key, address);
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        sessionsEnded(account);
        return account;
    }

    /**
     * Bans the account until {@code until} ({@link Account#PERMANENT_BAN} for good, M14e): no
     * login, its open sessions end at their next request, its WebSocket connections are closed. Its
     * villages are not touched here ({@code AdminPlayerService} converts them for a permanent ban).
     */
    @Transactional
    public Account ban(long accountId, Instant until, String reason) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.ban(until, reason);
        sessionsEnded(account);
        return account;
    }

    /** Lifts a ban (M14e). The player logs in again as usual. */
    @Transactional
    public Account unban(long accountId) {
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.unban();
        states.changed(accountId);
        return account;
    }

    /**
     * The account's open sessions must end (ban, deletion, password; M14e): its cached state is
     * forgotten after the commit and {@link SessionsEnded} closes its WebSocket connections.
     * Callers have already changed the account ({@link Account#endSessions} or {@link
     * Account#disable}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sessionsEnded(Account account) {
        states.changed(account.getId());
        events.publishEvent(new SessionsEnded(account.getId()));
    }

    /** Used by Spring Security's login check. Case-insensitive, like registration. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByUsernameKey(Account.keyOf(username))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    private boolean inviteCodeMatches(String given) {
        // Constant-time comparison: the response time doesn't reveal how much of the code was
        // right.
        String inviteCode = worldService.settings().inviteCode();
        return MessageDigest.isEqual(
                inviteCode.getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8));
    }

    private static GameRuleException usernameTaken() {
        return new GameRuleException(ErrorCode.USERNAME_TAKEN, "Username is already taken");
    }
}
