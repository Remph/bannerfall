package de.bannerfall.market;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The market (04-api <i>Market</i>). Village paths and {@code fromVillageId} are own villages only
 * (404 {@code VILLAGE_NOT_FOUND}); cancelling a foreign offer is 404 {@code OFFER_NOT_FOUND}.
 */
@RestController
@RequestMapping("/api")
class MarketController {

    private final MarketService marketService;
    private final MarketExchangeService exchangeService;
    private final PlayerService playerService;

    MarketController(
            MarketService marketService,
            MarketExchangeService exchangeService,
            PlayerService playerService) {
        this.marketService = marketService;
        this.exchangeService = exchangeService;
        this.playerService = playerService;
    }

    @GetMapping("/villages/{villageId}/market")
    MarketDto market(@PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return marketService.market(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/villages/{villageId}/transports")
    @ResponseStatus(HttpStatus.CREATED)
    TransportDto send(
            @PathVariable long villageId,
            @Valid @RequestBody TransportRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return marketService.send(villageId, playerService.idOf(me.accountId()), body);
    }

    @PostMapping("/villages/{villageId}/market/exchange")
    ExchangeDto exchange(
            @PathVariable long villageId,
            @Valid @RequestBody ExchangeRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return exchangeService.exchange(villageId, playerService.idOf(me.accountId()), body);
    }

    @GetMapping("/offers")
    List<OfferDto> offers(
            @RequestParam long fromVillageId, @AuthenticationPrincipal AccountPrincipal me) {
        return marketService.offersOfOthers(fromVillageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/villages/{villageId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    OfferDto createOffer(
            @PathVariable long villageId,
            @Valid @RequestBody OfferRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return marketService.createOffer(villageId, playerService.idOf(me.accountId()), body);
    }

    @DeleteMapping("/offers/{offerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelOffer(@PathVariable long offerId, @AuthenticationPrincipal AccountPrincipal me) {
        marketService.cancelOffer(offerId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/offers/{offerId}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    List<TransportDto> accept(
            @PathVariable long offerId,
            @Valid @RequestBody AcceptRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return marketService.accept(offerId, playerService.idOf(me.accountId()), body);
    }
}
