package de.bannerfall.message;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Private messages and the block list (04-api <i>Private messages</i>). */
@RestController
class MessageController {

    private final MessageService messageService;
    private final PlayerService playerService;

    MessageController(MessageService messageService, PlayerService playerService) {
        this.messageService = messageService;
        this.playerService = playerService;
    }

    @GetMapping("/api/messages/conversations")
    List<ConversationDto> conversations(@AuthenticationPrincipal AccountPrincipal me) {
        return messageService.conversations(playerService.idOf(me.accountId()));
    }

    @GetMapping("/api/messages/unread")
    UnreadMessagesDto unread(@AuthenticationPrincipal AccountPrincipal me) {
        return new UnreadMessagesDto(
                messageService.unreadCount(playerService.idOf(me.accountId())));
    }

    /** The last 100 messages with a player, oldest first; {@code before} pages back. */
    @GetMapping("/api/messages/with/{playerId}")
    List<MessageDto> conversation(
            @PathVariable long playerId,
            @RequestParam(required = false) @Min(1) Long before,
            @AuthenticationPrincipal AccountPrincipal me) {
        return messageService.conversation(playerService.idOf(me.accountId()), playerId, before);
    }

    @PostMapping("/api/messages/with/{playerId}")
    @ResponseStatus(HttpStatus.CREATED)
    MessageDto send(
            @PathVariable long playerId,
            @Valid @RequestBody MessageSendRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return messageService.send(playerService.idOf(me.accountId()), playerId, body.text());
    }

    @PostMapping("/api/messages/with/{playerId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable long playerId, @AuthenticationPrincipal AccountPrincipal me) {
        messageService.markRead(playerService.idOf(me.accountId()), playerId);
    }

    @GetMapping("/api/blocks")
    List<BlockedPlayerDto> blocks(@AuthenticationPrincipal AccountPrincipal me) {
        return messageService.blocks(playerService.idOf(me.accountId()));
    }

    @PutMapping("/api/blocks/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@PathVariable long playerId, @AuthenticationPrincipal AccountPrincipal me) {
        messageService.block(playerService.idOf(me.accountId()), playerId);
    }

    @DeleteMapping("/api/blocks/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@PathVariable long playerId, @AuthenticationPrincipal AccountPrincipal me) {
        messageService.unblock(playerService.idOf(me.accountId()), playerId);
    }
}
