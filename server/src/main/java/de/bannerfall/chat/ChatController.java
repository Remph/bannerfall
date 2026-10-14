package de.bannerfall.chat;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.common.ApiException;
import de.bannerfall.player.PlayerPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The chats (04-api <i>Chat</i>, <i>WebSocket</i>): history over REST, sending over STOMP ({@code
 * /app/chat.send}), new messages arrive on {@code /topic/chat} (everyone, the sender too). The
 * guild chat (M15b) sends to {@code /app/guild-chat.send} and arrives on {@code
 * /user/queue/guild-chat}, so only the members hear it.
 */
@RestController
class ChatController {

    private final ChatService chatService;
    private final PlayerService playerService;

    ChatController(ChatService chatService, PlayerService playerService) {
        this.chatService = chatService;
        this.playerService = playerService;
    }

    /** The last 100 messages, oldest first; {@code before} = a message id pages further back. */
    @GetMapping("/api/chat/messages")
    List<ChatMessageDto> history(@RequestParam(required = false) @Min(1) Long before) {
        return chatService.history(before);
    }

    /** The last 100 messages of the player's own guild (M15b), like {@link #history}. */
    @GetMapping("/api/guilds/mine/chat")
    List<ChatMessageDto> guildHistory(
            @RequestParam(required = false) @Min(1) Long before,
            @AuthenticationPrincipal AccountPrincipal me) {
        return chatService.guildHistory(playerService.idOf(me.accountId()), before);
    }

    /** The leader hides a message of his guild's chat (M15b). */
    @DeleteMapping("/api/guilds/mine/chat/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hideGuildMessage(
            @PathVariable long messageId, @AuthenticationPrincipal AccountPrincipal me) {
        chatService.hideInGuild(playerService.idOf(me.accountId()), messageId);
    }

    @MessageMapping("guild-chat.send")
    void sendToGuild(ChatSendRequest request, PlayerPrincipal me) {
        chatService.sendToGuild(me.playerId(), request.text());
    }

    @MessageMapping("chat.send")
    void send(ChatSendRequest request, PlayerPrincipal me) {
        chatService.send(me.playerId(), request.text());
    }

    /** A refused message goes back to the sender's connection only, as {@link ChatErrorDto}. */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ChatErrorDto refused(ApiException e) {
        return new ChatErrorDto(e.code(), e.getMessage());
    }
}
