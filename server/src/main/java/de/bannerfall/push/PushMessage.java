package de.bannerfall.push;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.village.VillageRefDto;
import java.time.Instant;

/**
 * What one push carries to the service worker (1.5), as JSON; the web writes the text from it in
 * the device's language, with the same words as the toast (05-frontend §7b). At most a few hundred
 * bytes: push services take 4 KB.
 *
 * @param lang the device's language
 * @param world the world's name: the notification's title
 * @param event the live notification behind it, as {@code /user/queue/events} sends it; null for a
 *     test
 * @param village the village it is about with its owner now (the attacked one, the lost one, the
 *     one whose build queue ran empty); null for the others
 * @param attacks attacks on the way to {@code village}, this one included; only for an attack
 * @param firstArrival when the first of them arrives; only for an attack
 * @param test a test the player sent himself from the Notifications page
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record PushMessage(
        String lang,
        String world,
        GameNotification event,
        VillageRefDto village,
        Integer attacks,
        Instant firstArrival,
        Boolean test) {

    static PushMessage of(GameNotification event) {
        return new PushMessage(null, null, event, null, null, null, null);
    }

    static PushMessage selfTest() {
        return new PushMessage(null, null, null, null, null, null, true);
    }

    PushMessage about(VillageRefDto village) {
        return new PushMessage(lang, world, event, village, attacks, firstArrival, test);
    }

    PushMessage withAttacks(int attacks, Instant firstArrival) {
        return new PushMessage(lang, world, event, village, attacks, firstArrival, test);
    }

    /** The same message for one device. */
    PushMessage forDevice(String lang, String world) {
        return new PushMessage(lang, world, event, village, attacks, firstArrival, test);
    }
}
