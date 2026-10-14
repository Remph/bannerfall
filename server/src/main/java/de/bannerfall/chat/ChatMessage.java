package de.bannerfall.chat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * One message in the global chat (02-game-rules §13). Never edited; the admin can hide it (M14).
 */
@Entity
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The author; a plain id, the name is joined in for the history. Null for a broadcast by the
     * host (M14d).
     */
    private Long playerId;

    /** Trimmed, 1–500 characters. */
    private String text;

    /** Soft delete by the admin (M14a): hidden from history. */
    private boolean deleted;

    private Instant createdAt;

    /** The guild whose members see the message (M15b); null = the global chat. */
    private Long guildId;

    protected ChatMessage() {}

    /**
     * @param playerId null for a broadcast by the host
     */
    public ChatMessage(Long playerId, String text, Instant createdAt) {
        this(playerId, text, createdAt, null);
    }

    /**
     * @param guildId null for the global chat
     */
    public ChatMessage(Long playerId, String text, Instant createdAt, Long guildId) {
        this.playerId = playerId;
        this.text = text;
        this.createdAt = createdAt;
        this.guildId = guildId;
    }

    /** Hides the message from the history (admin, M14a). */
    public void hide() {
        this.deleted = true;
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getText() {
        return text;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getGuildId() {
        return guildId;
    }
}
