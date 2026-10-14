package de.bannerfall.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Visible messages with an id below {@code before}, newest first, with the author's name
     * (broadcasts have none).
     */
    @Query(
            """
            select new de.bannerfall.chat.ChatMessageDto(m.id, p.id, p.name, m.text, m.createdAt)
            from ChatMessage m left join Player p on p.id = m.playerId
            where m.deleted = false and m.guildId is null and m.id < :before
            order by m.id desc
            """)
    List<ChatMessageDto> findNewestBefore(long before, Pageable pageable);

    /** The same for the chat of one guild (M15b). */
    @Query(
            """
            select new de.bannerfall.chat.ChatMessageDto(m.id, p.id, p.name, m.text, m.createdAt)
            from ChatMessage m left join Player p on p.id = m.playerId
            where m.deleted = false and m.guildId = :guildId and m.id < :before
            order by m.id desc
            """)
    List<ChatMessageDto> findNewestOfGuildBefore(long guildId, long before, Pageable pageable);
}
