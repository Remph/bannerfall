package de.bannerfall.diplomacy;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The diplomacy page (2.1 S2b, 04-api <i>Diplomacy</i>, 12-solo-and-allies §7): the court, the
 * player's allies and the other NPC lords with their trust, and inviting a lord into his guild;
 * since S3 the orders to allies (12 §8), since S4 the help requests of allies (12 §9).
 */
@RestController
@RequestMapping("/api/diplomacy")
class DiplomacyController {

    private final DiplomacyService diplomacy;
    private final OrderService orders;
    private final HelpService help;
    private final PlayerService playerService;

    DiplomacyController(
            DiplomacyService diplomacy,
            OrderService orders,
            HelpService help,
            PlayerService playerService) {
        this.diplomacy = diplomacy;
        this.orders = orders;
        this.help = help;
        this.playerService = playerService;
    }

    /** The player's open help requests from his allies (2.1 S4, 12-solo-and-allies §9). */
    @GetMapping("/requests")
    List<HelpRequestDto> requests(@AuthenticationPrincipal AccountPrincipal me) {
        return help.open(playerOf(me));
    }

    /** "Not this time" on a help request; returns the open requests left. */
    @PostMapping("/requests/{requestId}/decline")
    List<HelpRequestDto> decline(
            @PathVariable long requestId, @AuthenticationPrincipal AccountPrincipal me) {
        return help.decline(playerOf(me), requestId);
    }

    @GetMapping
    DiplomacyDto diplomacy(@AuthenticationPrincipal AccountPrincipal me) {
        return diplomacy.page(playerOf(me));
    }

    /** Invites the lord into the player's guild (its leader); the lord answers by letter. */
    @PostMapping("/lords/{lordId}/invite")
    DiplomacyDto invite(@PathVariable long lordId, @AuthenticationPrincipal AccountPrincipal me) {
        return diplomacy.invite(playerOf(me), lordId);
    }

    /** The order sheet for one target village (2.1 S3, 12-solo-and-allies §8). */
    @GetMapping("/orders/{villageId}")
    OrderSheetDto orderSheet(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return orders.sheet(playerOf(me), villageId);
    }

    /** Gives an order to one or more allies; each answers by letter in its next round. */
    @PostMapping("/orders")
    OrderSheetDto order(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal AccountPrincipal me) {
        return orders.give(playerOf(me), request);
    }

    /** Takes the order to the lord back. */
    @DeleteMapping("/orders/{lordId}")
    DiplomacyDto cancelOrder(
            @PathVariable long lordId, @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerOf(me);
        orders.cancel(playerId, lordId);
        return diplomacy.page(playerId);
    }

    private long playerOf(AccountPrincipal me) {
        return playerService.idOf(me.accountId());
    }
}
