package de.bannerfall.guild;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guilds (04-api <i>Guilds</i>). Everybody who is logged in may read every guild; the changes are
 * the player's own (his guild, his invitations) and the leader's. All of them answer with the new
 * {@link MyGuildDto}. Open in the pre-game ({@code PregameGateFilter}).
 */
@RestController
@RequestMapping("/api/guilds")
class GuildController {

    private final GuildService guildService;
    private final PlayerService playerService;

    GuildController(GuildService guildService, PlayerService playerService) {
        this.guildService = guildService;
        this.playerService = playerService;
    }

    @GetMapping
    List<GuildSummaryDto> listGuilds() {
        return guildService.list();
    }

    @GetMapping("/mine")
    MyGuildDto myGuild(@AuthenticationPrincipal AccountPrincipal me) {
        return guildService.mine(playerOf(me));
    }

    @GetMapping("/{guildId}")
    GuildDto getGuild(@PathVariable long guildId, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.get(guildId, playerOf(me));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MyGuildDto createGuild(
            @Valid @RequestBody CreateGuildRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.create(playerOf(me), body.name(), body.tag());
    }

    @PutMapping("/mine/description")
    MyGuildDto describeGuild(
            @Valid @RequestBody DescriptionRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.describe(playerOf(me), body.description());
    }

    @PostMapping("/mine/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    MyGuildDto inviteToGuild(
            @Valid @RequestBody InviteRequest body, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.invite(playerOf(me), body.playerName());
    }

    @DeleteMapping("/mine/invitations/{invitationId}")
    MyGuildDto withdrawInvitation(
            @PathVariable long invitationId, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.withdrawInvitation(playerOf(me), invitationId);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    MyGuildDto acceptInvitation(
            @PathVariable long invitationId, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.accept(playerOf(me), invitationId);
    }

    @DeleteMapping("/invitations/{invitationId}")
    MyGuildDto declineInvitation(
            @PathVariable long invitationId, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.decline(playerOf(me), invitationId);
    }

    @PostMapping("/mine/leave")
    MyGuildDto leaveGuild(@AuthenticationPrincipal AccountPrincipal me) {
        return guildService.leave(playerOf(me));
    }

    @DeleteMapping("/mine/members/{playerId}")
    MyGuildDto kickFromGuild(
            @PathVariable long playerId, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.kick(playerOf(me), playerId);
    }

    @PostMapping("/mine/leader")
    MyGuildDto handLeadershipOver(
            @Valid @RequestBody LeaderRequest body, @AuthenticationPrincipal AccountPrincipal me) {
        return guildService.transferLeadership(playerOf(me), body.playerId());
    }

    @DeleteMapping("/mine")
    MyGuildDto disbandGuild(@AuthenticationPrincipal AccountPrincipal me) {
        return guildService.disband(playerOf(me));
    }

    private long playerOf(AccountPrincipal me) {
        return playerService.idOf(me.accountId());
    }
}
