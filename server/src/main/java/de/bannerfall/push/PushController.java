package de.bannerfall.push;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Push notifications (1.5, 04-api <i>Push</i>): the player's devices and switches. */
@RestController
class PushController {

    private final PushSettings settings;
    private final PushSubscriptionService subscriptions;
    private final PushNotifier notifier;
    private final PlayerService playerService;

    PushController(
            PushSettings settings,
            PushSubscriptionService subscriptions,
            PushNotifier notifier,
            PlayerService playerService) {
        this.settings = settings;
        this.subscriptions = subscriptions;
        this.notifier = notifier;
        this.playerService = playerService;
    }

    @GetMapping("/api/push")
    PushSettingsDto get(@AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        return dto(playerId, subscriptions.preferences(playerId));
    }

    @PutMapping("/api/push/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(
            @Valid @RequestBody PushSubscriptionRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        subscriptions.subscribe(
                playerService.idOf(me.accountId()),
                body.endpoint(),
                body.keys().p256dh(),
                body.keys().auth(),
                body.language());
    }

    @PostMapping("/api/push/subscription/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(
            @Valid @RequestBody PushUnsubscribeRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        subscriptions.unsubscribe(playerService.idOf(me.accountId()), body.endpoint());
    }

    @PutMapping("/api/push/preferences")
    PushSettingsDto preferences(
            @Valid @RequestBody PushPreferencesRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        Map<PushKind, Boolean> changes = new EnumMap<>(PushKind.class);
        body.kinds().forEach(kind -> changes.put(kind.kind(), kind.enabled()));
        return dto(playerId, subscriptions.setPreferences(playerId, changes));
    }

    @PostMapping("/api/push/test")
    PushTestDto test(@AuthenticationPrincipal AccountPrincipal me) {
        return new PushTestDto(notifier.test(playerService.idOf(me.accountId())));
    }

    private PushSettingsDto dto(long playerId, Map<PushKind, Boolean> preferences) {
        return new PushSettingsDto(
                settings.enabled(),
                settings.publicKey(),
                subscriptions.deviceCount(playerId),
                preferences.entrySet().stream()
                        .map(entry -> new PushKindDto(entry.getKey(), entry.getValue()))
                        .toList());
    }
}
