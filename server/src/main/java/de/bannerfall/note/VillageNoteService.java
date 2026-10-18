package de.bannerfall.note;

import de.bannerfall.chat.ChatText;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * A player's own notes on villages (1.4, 02-game-rules §23): one per player and village, read by
 * nobody else. Plain SQL ({@code village_note}); the map and the farm list read them with a join.
 */
@Service
public class VillageNoteService {

    private final JdbcClient jdbc;
    private final Clock clock;

    VillageNoteService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Writes the note, or deletes it when {@code text} is blank. Any village on the map, the
     * player's own included.
     *
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} for an unknown village
     * @throws de.bannerfall.common.ApiException 400 {@code VALIDATION} for more than 500 characters
     *     or control characters other than line breaks and tabs
     */
    public VillageNoteDto save(long playerId, long villageId, String text) {
        boolean exists =
                jdbc.sql("select exists (select 1 from village where id = :id)")
                        .param("id", villageId)
                        .query(Boolean.class)
                        .single();
        if (!exists) {
            throw new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND);
        }
        if (text.isBlank()) {
            jdbc.sql("delete from village_note where player_id = :player and village_id = :village")
                    .param("player", playerId)
                    .param("village", villageId)
                    .update();
            return new VillageNoteDto(villageId, null);
        }
        String checked = ChatText.checked(text);
        jdbc.sql(
                        """
                        insert into village_note (player_id, village_id, text, updated_at)
                        values (:player, :village, :text, :now)
                        on conflict (player_id, village_id) do update
                           set text = excluded.text, updated_at = excluded.updated_at
                        """)
                .param("player", playerId)
                .param("village", villageId)
                .param("text", checked)
                .param("now", Timestamp.from(clock.instant()))
                .update();
        return new VillageNoteDto(villageId, checked);
    }

    /** The player's notes on these villages, by village id; villages without one are left out. */
    public Map<Long, String> notesOn(long playerId, Collection<Long> villageIds) {
        if (villageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> notes = new HashMap<>();
        jdbc.sql(
                        """
                        select village_id, text from village_note
                        where player_id = :player and village_id in (:villages)
                        """)
                .param("player", playerId)
                .param("villages", villageIds)
                .query(
                        rs -> {
                            notes.put(rs.getLong("village_id"), rs.getString("text"));
                        });
        return notes;
    }

    /** All notes of a player the host deleted: nobody can read them any more. */
    public void deleteAllOf(long playerId) {
        jdbc.sql("delete from village_note where player_id = :player")
                .param("player", playerId)
                .update();
    }
}
