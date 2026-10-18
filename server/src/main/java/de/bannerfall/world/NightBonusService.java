package de.bannerfall.world;

import de.bannerfall.rules.NightBonus;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The world's night bonus (1.4, 02-game-rules §8.1) with the zone its hours are read in: {@code
 * bannerfall.night-bonus.zone}, Europe/Berlin unless the host sets another one.
 */
@Service
public class NightBonusService {

    private final WorldService worldService;
    private final ZoneId zone;

    NightBonusService(
            WorldService worldService,
            @Value("${bannerfall.night-bonus.zone:Europe/Berlin}") String zone) {
        this.worldService = worldService;
        this.zone = ZoneId.of(zone);
    }

    /** The night bonus as the settings stand now. */
    public NightBonus current() {
        return worldService.settings().nightBonus(zone);
    }
}
