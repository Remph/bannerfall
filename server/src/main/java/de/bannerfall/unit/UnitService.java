package de.bannerfall.unit;

import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.recruit.DismissOrder;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrder;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * What a village's units look like. Units in recruit orders (M5), in movements (M7) and waiting to
 * be dismissed add their part to {@link VillageUnits#all()} here, once, so everything that counts
 * population sees it.
 */
@Service
public class UnitService {

    /**
     * @param home the village's own army at home (live: changes are saved with its stack)
     * @param all all units that belong to the village: home, supporting elsewhere, on the way
     *     (attack, support, return) and in recruit orders and not done yet; this is what counts
     *     against the farm (02-game-rules §5.3)
     */
    public record VillageUnits(UnitCounts home, UnitCounts all) {}

    private final UnitStackRepository stacks;
    private final MovementRepository movements;
    private final DismissOrderRepository dismissOrders;

    UnitService(
            UnitStackRepository stacks,
            MovementRepository movements,
            DismissOrderRepository dismissOrders) {
        this.stacks = stacks;
        this.movements = movements;
        this.dismissOrders = dismissOrders;
    }

    /**
     * The village's own army at home, live: changes are saved with its stack. Call it inside a
     * transaction, on a locked village.
     */
    public UnitCounts homeUnits(long villageId) {
        return stacks.findHomeStack(villageId)
                .map(UnitStack::getUnits)
                .orElseThrow(() -> noHomeStack(villageId));
    }

    /**
     * All stacks of the village are loaded with one query, its movements with a second one. The
     * recruit orders come from the caller, so a request reads them only once: {@code
     * VillageStateService.catchUp} returns them. Call it inside a transaction, on a locked village.
     *
     * @param recruitOrders the village's open recruit orders (as returned by the catch-up)
     */
    public VillageUnits unitsOf(long villageId, List<RecruitOrder> recruitOrders) {
        List<UnitStack> villageStacks = stacks.findByHomeVillageId(villageId);
        UnitCounts home =
                villageStacks.stream()
                        .filter(UnitStack::isHome)
                        .findFirst()
                        .map(UnitStack::getUnits)
                        .orElseThrow(() -> noHomeStack(villageId));
        // A fresh instance: adding to it must not change a live stack.
        UnitCounts all = new UnitCounts();
        for (UnitStack stack : villageStacks) {
            all = all.plus(stack.getUnits());
        }
        for (Movement movement : movements.findByHomeVillageId(villageId)) {
            all = all.plus(movement.getUnits());
        }
        for (RecruitOrder order : recruitOrders) {
            all.add(order.getUnit(), order.remaining());
        }
        // Units waiting to be dismissed left the home stack but still eat from the farm until
        // their turn (02-game-rules §5.2a).
        for (DismissOrder order : dismissOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            all.add(order.getUnit(), order.remaining());
        }
        return new VillageUnits(home, all);
    }

    private static IllegalStateException noHomeStack(long villageId) {
        return new IllegalStateException("Village " + villageId + " has no home stack");
    }
}
