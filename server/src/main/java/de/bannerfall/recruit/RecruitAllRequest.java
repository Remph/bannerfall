package de.bannerfall.recruit;

import de.bannerfall.gamedata.UnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * POST /api/villages/{id}/recruit-orders/all: several orders queued at once, all or none
 * (02-game-rules §5.2, "recruit all"). Each entry is checked like a single order.
 *
 * @param orders 1 … one per unit type; queued in this order
 */
public record RecruitAllRequest(
        @NotNull @Size(min = 1, max = MAX_ORDERS) List<@NotNull @Valid RecruitOrderRequest> orders) {

    /**
     * Every unit at most once, so there are never more entries than unit types ({@code
     * UnitType.values().length}, which isn't a constant an annotation can use; a test checks it).
     */
    static final int MAX_ORDERS = 13;

    /**
     * Two entries for the same unit would be two orders the screen never sends (it has one field
     * per unit); refused, so the request stays one order per unit.
     */
    @AssertTrue(message = "every unit at most once") boolean isEveryUnitOnce() {
        if (orders == null) {
            return true; // @NotNull reports that
        }
        Set<UnitType> seen = new HashSet<>();
        return orders.stream()
                .filter(Objects::nonNull)
                .map(RecruitOrderRequest::unit)
                .filter(Objects::nonNull)
                .allMatch(seen::add);
    }
}
