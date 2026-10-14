package de.bannerfall.abuse;

import de.bannerfall.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Tells a 404 for a foreign id from one for an id that doesn't exist (any more), after the answer
 * is decided (M14e): the API answers both alike, the suspicion log only records the first. The ids
 * come from the request's path (and {@code fromVillageId}); one small query per 404.
 *
 * <p>Not foreign: an order that just finished or a movement that just arrived (they are gone), an
 * order of another of the player's own villages, and a village the player lost in a conquest (he
 * may still have its screen open; his {@code VILLAGE_LOST} report names it).
 */
@Component
class ForeignIds {

    private final JdbcClient jdbc;

    ForeignIds(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * True if the request named an object that exists and isn't the player's.
     *
     * @param code the 404's code
     */
    boolean isForeign(ErrorCode code, HttpServletRequest request, long playerId) {
        Map<String, String> path = pathVariables(request);
        return switch (code) {
            case VILLAGE_NOT_FOUND -> {
                Long id = id(path.getOrDefault("villageId", path.get("id")));
                if (id == null) {
                    id = id(request.getParameter("fromVillageId"));
                }
                yield id != null && isForeignVillage(id, playerId);
            }
            case BUILD_ORDER_NOT_FOUND ->
                    isForeignOrder("build_order", id(path.get("orderId")), playerId);
            case RECRUIT_ORDER_NOT_FOUND ->
                    isForeignOrder("recruit_order", id(path.get("orderId")), playerId);
            case DISMISS_ORDER_NOT_FOUND ->
                    isForeignOrder("dismiss_order", id(path.get("orderId")), playerId);
            case RESEARCH_ORDER_NOT_FOUND ->
                    isForeignOrder("research_order", id(path.get("orderId")), playerId);
            case BANNER_NOT_FOUND ->
                    exists(
                            "select 1 from banner where id = :id and owner_id <> :player",
                            id(path.get("bannerId")),
                            playerId);
            case FARM_TARGET_NOT_FOUND ->
                    isForeignOrder("farm_target", id(path.get("targetId")), playerId);
            case MOVEMENT_NOT_FOUND ->
                    exists(
                            "select 1 from movement where id = :id and owner_player_id <> :player",
                            id(path.get("movementId")),
                            playerId);
            case STACK_NOT_FOUND ->
                    exists(
                            """
                            select 1 from unit_stack s
                            join village h on h.id = s.home_village_id
                            join village l on l.id = s.location_village_id
                            where s.id = :id
                              and h.player_id is distinct from :player
                              and l.player_id is distinct from :player
                            """,
                            id(path.get("stackId")),
                            playerId);
            case REPORT_NOT_FOUND ->
                    exists(
                            "select 1 from report where id = :id and player_id <> :player",
                            id(path.get("reportId")),
                            playerId);
            // Only a cancel: an accept answers OFFER_NOT_FOUND for an offer that is gone.
            case OFFER_NOT_FOUND ->
                    "DELETE".equals(request.getMethod())
                            && exists(
                                    """
                                    select 1 from trade_offer o join village v on v.id = o.village_id
                                    where o.id = :id and v.player_id is distinct from :player
                                    """,
                                    id(path.get("offerId")),
                                    playerId);
            default -> false;
        };
    }

    private boolean isForeignVillage(long villageId, long playerId) {
        return exists(
                """
                select 1 from village v
                where v.id = :id and v.player_id is distinct from :player
                  and not exists (
                      select 1 from report r
                      where r.player_id = :player and r.kind = 'VILLAGE_LOST'
                        and r.payload -> 'village' ->> 'id' = cast(:id as text))
                """,
                villageId,
                playerId);
    }

    /** The order exists in a village of someone else. */
    private boolean isForeignOrder(String table, Long orderId, long playerId) {
        return exists(
                "select 1 from "
                        + table
                        + " o join village v on v.id = o.village_id"
                        + " where o.id = :id and v.player_id is distinct from :player",
                orderId,
                playerId);
    }

    private boolean exists(String sql, Long id, long playerId) {
        if (id == null) {
            return false;
        }
        return !jdbc.sql(sql)
                .param("id", id)
                .param("player", playerId)
                .query(Integer.class)
                .list()
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private static Long id(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
