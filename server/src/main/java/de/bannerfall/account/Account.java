package de.bannerfall.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Locale;

/** Login identity. The game identity (player) is a separate table from M3 on. */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** As typed at registration, shown in the UI. */
    private String username;

    /** Lower-case username: used for login and uniqueness ("Ben" and "ben" are the same). */
    private String usernameKey;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant createdAt;

    /** Set when the admin deletes the player (M14a): no login, no respawn. */
    private boolean disabled;

    /** The last successful login (or the registration); null before M14d. */
    private Instant lastLoginAt;

    /** Banned until then (M14e); null = not banned, {@link #PERMANENT_BAN} = for good. */
    private Instant bannedUntil;

    /** Why, shown to the player on the login page; null when not banned. */
    private String banReason;

    /**
     * Raised whenever the account's open sessions must end (ban, password reset or change, M14e). A
     * session remembers the value of its login ({@link AccountPrincipal#sessionVersion()}).
     */
    private int sessionVersion;

    /** The end of a ban "for good" (M14e): far enough away to never come. */
    public static final Instant PERMANENT_BAN = Instant.parse("9999-12-31T00:00:00Z");

    protected Account() {}

    public Account(String username, String passwordHash, Role role, Instant createdAt) {
        this.username = username;
        this.usernameKey = keyOf(username);
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static String keyOf(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void disable() {
        this.disabled = true;
    }

    /**
     * A new password hash: the host's reset or the player's own change (M14d). Ends the account's
     * open sessions (M14e): whoever knew the old password is out.
     */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        endSessions();
    }

    /** Banned until {@code until} (M14e); ends the open sessions. */
    public void ban(Instant until, String reason) {
        this.bannedUntil = until;
        this.banReason = reason;
        endSessions();
    }

    public void unban() {
        this.bannedUntil = null;
        this.banReason = null;
    }

    /** True while a ban lasts. */
    public boolean isBannedAt(Instant now) {
        return bannedUntil != null && bannedUntil.isAfter(now);
    }

    /** Every open session of the account ends at its next request (M14e). */
    public void endSessions() {
        sessionVersion++;
    }

    public Instant getBannedUntil() {
        return bannedUntil;
    }

    public String getBanReason() {
        return banReason;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    /** Made admin or back to player by the host (M14d). */
    public void changeRole(Role role) {
        this.role = role;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameKey() {
        return usernameKey;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
