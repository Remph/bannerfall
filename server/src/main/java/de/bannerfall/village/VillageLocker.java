package de.bannerfall.village;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to lock villages (01-architecture §4.5): {@code select ... for update}, always in
 * ascending id order, so two transactions locking the same villages can't deadlock. The locks are
 * held until the end of the caller's transaction (there must be one).
 */
@Component
public class VillageLocker {

    private final VillageRepository villages;

    VillageLocker(VillageRepository villages) {
        this.villages = villages;
    }

    /**
     * Locks the villages with these ids and returns them in id order. Ids that don't exist are
     * missing from the result.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Village> lock(Long... ids) {
        return villages.lockAllInIdOrder(Arrays.stream(ids).distinct().sorted().toList());
    }

    /**
     * Locks a village of the player.
     *
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} if it doesn't exist or belongs to someone
     *     else (the API doesn't reveal which)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Village lockOwned(long villageId, long playerId) {
        return villages.lockOwned(villageId, playerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND));
    }
}
