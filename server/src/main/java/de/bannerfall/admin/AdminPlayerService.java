package de.bannerfall.admin;

import de.bannerfall.account.Account;
import de.bannerfall.account.AccountRepository;
import de.bannerfall.account.AccountService;
import de.bannerfall.account.Role;
import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.banner.BannerService;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.guild.GuildService;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.village.ProfileVillageDto;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's player operations (04-api *Admin*, 08-host-console §2): list, role, password reset,
 * delete. Every change writes an audit row ({@link AdminAuditService}) in its transaction.
 */
@Service
public class AdminPlayerService {

    /** No 0/O, 1/l/I: the host reads the password out to a friend. */
    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    static final int GENERATED_PASSWORD_LENGTH = 16;

    private final PlayerRepository players;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BarbarianService barbarians;
    private final GuildService guilds;
    private final AchievementService achievements;
    private final BannerService banners;
    private final AdminAuditService audit;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    AdminPlayerService(
            PlayerRepository players,
            AccountRepository accounts,
            AccountService accountService,
            BarbarianService barbarians,
            GuildService guilds,
            AchievementService achievements,
            BannerService banners,
            AdminAuditService audit,
            JdbcClient jdbc,
            Clock clock) {
        this.players = players;
        this.accounts = accounts;
        this.accountService = accountService;
        this.barbarians = barbarians;
        this.guilds = guilds;
        this.achievements = achievements;
        this.banners = banners;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Every human player, deleted ones included, by name; NPC lords have their own list. */
    @Transactional(readOnly = true)
    public List<AdminPlayerDto> list() {
        Instant now = clock.instant();
        Map<Long, List<ProfileVillageDto>> villagesByPlayer = new HashMap<>();
        jdbc.sql(
                        """
                        select v.id, v.name, v.x, v.y, v.points, v.player_id
                        from village v join player p on p.id = v.player_id
                        where not p.npc
                        order by v.id
                        """)
                .query(
                        (rs, row) ->
                                villagesByPlayer
                                        .computeIfAbsent(
                                                rs.getLong("player_id"), id -> new ArrayList<>())
                                        .add(
                                                new ProfileVillageDto(
                                                        rs.getLong("id"),
                                                        rs.getString("name"),
                                                        rs.getInt("x"),
                                                        rs.getInt("y"),
                                                        rs.getInt("points"))))
                .list();
        return jdbc.sql(
                        """
                        select p.id, p.name, a.role, p.points, p.village_count, a.last_login_at,
                               a.created_at, a.disabled, a.banned_until, a.ban_reason
                        from player p join account a on a.id = p.account_id
                        order by lower(p.name)
                        """)
                .query(
                        (rs, row) -> {
                            Instant bannedUntil = instant(rs.getTimestamp("banned_until"));
                            boolean banned = bannedUntil != null && bannedUntil.isAfter(now);
                            return new AdminPlayerDto(
                                    rs.getLong("id"),
                                    rs.getString("name"),
                                    Role.valueOf(rs.getString("role")),
                                    rs.getInt("points"),
                                    rs.getInt("village_count"),
                                    instant(rs.getTimestamp("last_login_at")),
                                    instant(rs.getTimestamp("created_at")),
                                    rs.getBoolean("disabled"),
                                    banned ? bannedUntil : null,
                                    banned ? rs.getString("ban_reason") : null,
                                    villagesByPlayer.getOrDefault(rs.getLong("id"), List.of()));
                        })
                .list();
    }

    /**
     * Makes the player an admin or takes it back. Counts at the player's next admin request ({@link
     * AdminAccess}); the web shows the Admin entry after the next page load.
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for an NPC lord, an unknown or a deleted
     *     player
     * @throws GameRuleException {@code LAST_ADMIN} if he is the last admin who can log in
     */
    @Transactional
    public AdminPlayerDto changeRole(long playerId, Role role) {
        Account account = activeAccountOf(playerId);
        Role before = account.getRole();
        if (before == Role.ADMIN && role != Role.ADMIN) {
            requireAnotherAdmin();
        }
        if (before != role) {
            account.changeRole(role);
            audit.record(
                    "CHANGE_ROLE",
                    "player",
                    playerId,
                    Map.of("name", account.getUsername(), "from", before, "to", role));
        }
        accounts.flush(); // list() reads with plain SQL, which doesn't flush by itself
        return list().stream().filter(p -> p.playerId() == playerId).findFirst().orElseThrow();
    }

    /**
     * Sets a new password: the given one, or a generated one of {@value GENERATED_PASSWORD_LENGTH}
     * characters that is answered once. The audit row never contains it.
     *
     * @param password null to generate one
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} as for {@link #changeRole}
     */
    @Transactional
    public PasswordResetDto resetPassword(long playerId, String password) {
        Account account = activeAccountOf(playerId);
        String generated = password == null ? generatePassword() : null;
        accountService.resetPassword(
                account.getId(), Objects.requireNonNullElse(password, generated));
        audit.record(
                "RESET_PASSWORD",
                "player",
                playerId,
                Map.of("name", account.getUsername(), "generated", generated != null));
        return new PasswordResetDto(playerId, account.getUsername(), generated);
    }

    /**
     * The player's villages become barbarian ({@link BarbarianService#convertVillagesOf}); a
     * human's account is disabled (no login, no respawn), so the player leaves the ranking and
     * can't come back. The rows stay: reports and chat messages point at them. An NPC lord ends up
     * out of the game with no villages.
     *
     * @param adminAccountId the caller; an admin can't delete himself. Null for the host token.
     * @throws GameRuleException {@code LAST_ADMIN} for the last admin who can log in (M14d review)
     * @return how many villages became barbarian
     */
    @Transactional
    public int delete(long playerId, Long adminAccountId) {
        Player player =
                players.findById(playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        if (player.getAccountId() != null) {
            Account account = accounts.findById(player.getAccountId()).orElseThrow();
            if (account.getId().equals(adminAccountId)) {
                throw new GameRuleException(
                        ErrorCode.CANNOT_DELETE_SELF, "You can't delete your own player");
            }
            if (account.isDisabled()) {
                throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
            }
            if (account.getRole() == Role.ADMIN) {
                requireAnotherAdmin();
            }
            account.disable();
            accountService.sessionsEnded(account); // his open sessions end (M14e)
        }
        int converted = barbarians.convertVillagesOf(playerId).size();
        guilds.removeBecauseGone(playerId);
        banners.destroyAllOf(playerId, "player deleted");
        audit.record(
                "DELETE_PLAYER",
                "player",
                playerId,
                Map.of(
                        "name",
                        player.getName(),
                        "npc",
                        player.isNpc(),
                        "villagesConverted",
                        converted));
        return converted;
    }

    /**
     * Bans the player's account (M14e, 08-host-console §2): no login until {@code until}, his open
     * sessions end at their next request and his WebSocket connections close. A timed ban leaves
     * his villages alone; a permanent one ({@code until} null) turns them barbarian, like a delete
     * (decided in M14e), and unbanning doesn't give them back.
     *
     * @param until in the future; null, or a date at or after {@link Account#PERMANENT_BAN} =
     *     permanent
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} as for {@link #changeRole}
     * @throws GameRuleException {@code CANNOT_BAN_ADMIN}: take the role away first
     * @throws ApiException 400 {@code VALIDATION} if {@code until} isn't in the future
     */
    @Transactional
    public AdminPlayerDto ban(long playerId, Instant until, String reason) {
        Account account = activeAccountOf(playerId);
        if (account.getRole() == Role.ADMIN) {
            throw new GameRuleException(
                    ErrorCode.CANNOT_BAN_ADMIN, "Admins can't be banned; take the role away first");
        }
        // a date at or beyond the permanent end counts as for good (and can't overflow the column)
        boolean permanent = until == null || !until.isBefore(Account.PERMANENT_BAN);
        Instant end = permanent ? Account.PERMANENT_BAN : until;
        if (!end.isAfter(clock.instant())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "The ban must end in the future");
        }
        String trimmed = reason.strip();
        accountService.ban(account.getId(), end, trimmed);
        int converted = permanent ? barbarians.convertVillagesOf(playerId).size() : 0;
        if (permanent) {
            guilds.removeBecauseGone(playerId);
            banners.destroyAllOf(playerId, "permanent ban");
        }
        audit.record(
                "BAN_PLAYER",
                "player",
                playerId,
                Map.of(
                        "name",
                        account.getUsername(),
                        "until",
                        permanent ? "permanent" : end.toString(),
                        "reason",
                        trimmed,
                        "villagesConverted",
                        converted));
        accounts.flush();
        return row(playerId);
    }

    /**
     * Lifts the ban of the player (M14e); nothing happens (and nothing is audited) if he isn't
     * banned.
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} as for {@link #changeRole}
     */
    @Transactional
    public AdminPlayerDto unban(long playerId) {
        Account account = activeAccountOf(playerId);
        if (account.getBannedUntil() != null) {
            boolean wasBanned = account.isBannedAt(clock.instant());
            accountService.unban(account.getId());
            if (wasBanned) {
                audit.record(
                        "UNBAN_PLAYER", "player", playerId, Map.of("name", account.getUsername()));
            }
        }
        accounts.flush();
        return row(playerId);
    }

    /**
     * Takes an achievement away from a human player for good (M16, 02-game-rules §19): its levels
     * and counter go and he can't earn it again. Revoking twice changes nothing and is audited
     * once.
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} as for {@link #changeRole}; {@code
     *     ACHIEVEMENT_NOT_FOUND} for a key that is no achievement
     */
    @Transactional
    public AchievementRevokedDto revokeAchievement(long playerId, String key, String reason) {
        Account account = activeAccountOf(playerId);
        Achievement achievement;
        try {
            achievement = Achievement.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(ErrorCode.ACHIEVEMENT_NOT_FOUND);
        }
        boolean changed = achievements.revoke(playerId, achievement, reason);
        if (changed) {
            audit.record(
                    "REVOKE_ACHIEVEMENT",
                    "player",
                    playerId,
                    Map.of(
                            "name",
                            account.getUsername(),
                            "achievement",
                            achievement.name(),
                            "reason",
                            reason == null ? "" : reason));
        }
        return new AchievementRevokedDto(playerId, achievement.name(), changed);
    }

    private AdminPlayerDto row(long playerId) {
        return list().stream().filter(p -> p.playerId() == playerId).findFirst().orElseThrow();
    }

    /**
     * Before an admin stops being one (role change or deletion): there must be another admin who
     * can log in. The admins are locked, so two such changes at the same moment can't both pass.
     *
     * @throws GameRuleException {@code LAST_ADMIN}
     */
    private void requireAnotherAdmin() {
        if (accounts.lockActiveAdmins().size() <= 1) {
            throw new GameRuleException(
                    ErrorCode.LAST_ADMIN, "The last admin can't stop being one");
        }
    }

    /** The account of a human player who isn't deleted. */
    private Account activeAccountOf(long playerId) {
        return players.findById(playerId)
                .map(Player::getAccountId)
                .flatMap(accounts::findById)
                .filter(account -> !account.isDisabled())
                .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
