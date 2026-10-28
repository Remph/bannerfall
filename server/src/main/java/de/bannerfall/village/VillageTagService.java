package de.bannerfall.village;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * A player's own tags on his villages (1.7, 02-game-rules §26): at most one per village, read by
 * nobody else. Plain SQL ({@code village_tag}); a row only counts while its player owns the
 * village, and a conquest deletes it ({@link OwnerChange}).
 */
@Service
public class VillageTagService {

    static final int TAG_MAX = 5;

    /** Only ASCII, like a guild tag, so it looks the same on every phone and in every font. */
    private static final Pattern TAG = Pattern.compile("[A-Z0-9]{1," + TAG_MAX + "}");

    private final JdbcClient jdbc;

    VillageTagService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The trimmed tag in capitals; null for a blank one (removes the tag).
     *
     * @throws ApiException 400 {@code VALIDATION} for more than {@value #TAG_MAX} characters or
     *     anything but letters A-Z and digits
     */
    static String normalise(String raw) {
        String tag = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (tag.isEmpty()) {
            return null;
        }
        if (!TAG.matcher(tag).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "A village tag has 1 to " + TAG_MAX + " letters or digits");
        }
        return tag;
    }

    /**
     * Writes the tag, or removes it when {@code raw} is blank.
     *
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} unless the player owns the village
     * @throws ApiException 400 {@code VALIDATION} for an invalid tag
     */
    public VillageTagDto save(long playerId, long villageId, String raw) {
        boolean owned =
                jdbc.sql(
                                "select exists (select 1 from village where id = :id and player_id"
                                        + " = :player)")
                        .param("id", villageId)
                        .param("player", playerId)
                        .query(Boolean.class)
                        .single();
        if (!owned) {
            throw new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND);
        }
        String tag = normalise(raw);
        if (tag == null) {
            deleteForVillage(villageId);
            return new VillageTagDto(villageId, null);
        }
        jdbc.sql(
                        """
                        insert into village_tag (village_id, player_id, tag)
                        values (:village, :player, :tag)
                        on conflict (village_id) do update
                           set player_id = excluded.player_id, tag = excluded.tag
                        """)
                .param("village", villageId)
                .param("player", playerId)
                .param("tag", tag)
                .update();
        return new VillageTagDto(villageId, tag);
    }

    /** The player's tags on the villages he owns, by village id; untagged villages are left out. */
    public Map<Long, String> tagsOf(long playerId) {
        Map<Long, String> tags = new HashMap<>();
        jdbc.sql(
                        """
                        select t.village_id, t.tag from village_tag t
                        join village v on v.id = t.village_id and v.player_id = t.player_id
                        where t.player_id = :player
                        """)
                .param("player", playerId)
                .query(
                        rs -> {
                            tags.put(rs.getLong("village_id"), rs.getString("tag"));
                        });
        return tags;
    }

    /** The village changed owner: the old owner's tag goes with it. */
    void deleteForVillage(long villageId) {
        jdbc.sql("delete from village_tag where village_id = :village")
                .param("village", villageId)
                .update();
    }

    /** All tags of a player the host deleted: nobody can read them any more. */
    public void deleteAllOf(long playerId) {
        jdbc.sql("delete from village_tag where player_id = :player")
                .param("player", playerId)
                .update();
    }
}
