package de.bannerfall.account;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * At most {@code bannerfall.abuse.sessions-per-account} (default 20) logged-in sessions per account
 * (code review 1.2, D2): sessions live in memory for 30 days, so a script logging in again and
 * again would otherwise fill the server's memory. A login beyond the cap ends the account's oldest
 * session (its WebSocket connections close with it, {@code PlayerConnections}).
 *
 * <p>Spring Boot registers this bean as an {@link HttpSessionListener}, so sessions that end by
 * logout, expiry or the account guard leave the list by themselves.
 */
@Component
public class AccountSessions implements HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(AccountSessions.class);

    /** The session attribute naming the account the session was logged in for. */
    private static final String ACCOUNT_ATTRIBUTE = AccountSessions.class.getName() + ".account";

    private volatile int maxPerAccount;

    /** Per account its sessions, oldest login first. Guarded by {@code this}. */
    private final Map<Long, Deque<HttpSession>> byAccount = new HashMap<>();

    AccountSessions(@Value("${bannerfall.abuse.sessions-per-account:20}") int maxPerAccount) {
        this.maxPerAccount = maxPerAccount;
    }

    /**
     * The session was just logged in for the account (or renewed after a password change): it
     * becomes the account's newest; if that makes one too many, the oldest ends.
     */
    void loggedIn(long accountId, HttpSession session) {
        HttpSession oldest = null;
        synchronized (this) {
            Object before = session.getAttribute(ACCOUNT_ATTRIBUTE);
            if (before instanceof Long previous) {
                remove(previous, session);
            }
            session.setAttribute(ACCOUNT_ATTRIBUTE, accountId);
            Deque<HttpSession> sessions =
                    byAccount.computeIfAbsent(accountId, id -> new ArrayDeque<>());
            sessions.addLast(session);
            if (sessions.size() > maxPerAccount) {
                oldest = sessions.pollFirst();
            }
        }
        // Outside the lock: invalidating calls sessionDestroyed below.
        if (oldest != null) {
            log.info("Ended the oldest session of account {}: too many sessions", accountId);
            try {
                oldest.invalidate();
            } catch (IllegalStateException alreadyEnded) {
                // ended at the same moment by itself
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        Object accountId;
        try {
            accountId = session.getAttribute(ACCOUNT_ATTRIBUTE);
        } catch (IllegalStateException alreadyEnded) {
            return;
        }
        if (accountId instanceof Long id) {
            synchronized (this) {
                remove(id, session);
            }
        }
    }

    /** How many sessions the account has (tests). */
    synchronized int count(long accountId) {
        Deque<HttpSession> sessions = byAccount.get(accountId);
        return sessions == null ? 0 : sessions.size();
    }

    /** Another cap for a test. */
    public void setMaxPerAccount(int maxPerAccount) {
        this.maxPerAccount = maxPerAccount;
    }

    private void remove(long accountId, HttpSession session) {
        Deque<HttpSession> sessions = byAccount.get(accountId);
        if (sessions == null) {
            return;
        }
        // by identity (the id of a session changes at login, fixation protection), and by id in
        // case the container hands out another wrapper of the same session
        String id = session.getId();
        sessions.removeIf(other -> other == session || other.getId().equals(id));
        if (sessions.isEmpty()) {
            byAccount.remove(accountId);
        }
    }
}
