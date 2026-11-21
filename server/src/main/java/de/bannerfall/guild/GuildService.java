package de.bannerfall.guild;

import de.bannerfall.account.AccountRepository;
import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.chronicle.ChronicleSide;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.common.RateLimiter;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guilds (02-game-rules §16): create, invite, join, leave, kick, hand the lead over, disband.
 *
 * <p>Every change of who is in a guild starts by locking the guild's row ({@link
 * GuildRepository#findForUpdate}) and only then reads the membership, so two requests (the leader
 * kicks while the member leaves, two invitations accepted for the last free place) are decided one
 * after the other. Players hear of changes through {@link GameNotification.GuildChanged} after the
 * commit.
 */
@Service
public class GuildService {

    private final GuildRepository guilds;
    private final GuildMemberRepository members;
    private final GuildInvitationRepository invitations;
    private final PlayerRepository players;
    private final AccountRepository accounts;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AchievementService achievements;
    private final Chronicle chronicle;
    private final GuildBannerService banner;

    /**
     * When each player was last invited by any guild (in memory, per instance, like the chat's
     * limit): a player gets at most one invitation per {@link #inviteInterval}, so a leader can't
     * invite, withdraw and invite again to flood somebody's screen.
     */
    private final RateLimiter lastInvited = new RateLimiter();

    private volatile Duration inviteInterval = Duration.ofMinutes(1);

    GuildService(
            GuildRepository guilds,
            GuildMemberRepository members,
            GuildInvitationRepository invitations,
            PlayerRepository players,
            AccountRepository accounts,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock,
            AchievementService achievements,
            GuildBannerService banner,
            Chronicle chronicle) {
        this.chronicle = chronicle;
        this.guilds = guilds;
        this.members = members;
        this.invitations = invitations;
        this.players = players;
        this.accounts = accounts;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
        this.achievements = achievements;
        this.banner = banner;
    }

    // ---- reading ----

    /** All guilds, strongest first. */
    @Transactional(readOnly = true)
    public List<GuildSummaryDto> list() {
        return guilds.findSummaries();
    }

    /**
     * A guild with its members. The open invitations come with it only for the leader of this
     * guild.
     *
     * @throws NotFoundException {@code GUILD_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public GuildDto get(long guildId, long viewerId) {
        Guild guild =
                guilds.findById(guildId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.GUILD_NOT_FOUND));
        return dto(guild, isLeaderOf(viewerId, guildId));
    }

    /** The player's own guild state. */
    @Transactional(readOnly = true)
    public MyGuildDto mine(long playerId) {
        return myGuild(playerId);
    }

    // ---- creating and joining ----

    /**
     * Founds a guild; the player is its leader. His open invitations are dropped.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code ALREADY_IN_GUILD}, {@code
     *     GUILD_NAME_TAKEN}, {@code GUILD_TAG_TAKEN}
     * @throws ApiException 400 {@code VALIDATION} for a name or tag that breaks {@link GuildText}
     */
    @Transactional
    public MyGuildDto create(long playerId, String rawName, String rawTag) {
        requireEnabled();
        String name = GuildText.name(rawName);
        String tag = GuildText.tag(rawTag);
        if (members.findGuildIdOf(playerId).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
        }
        if (guilds.existsByNameIgnoreCase(name)) {
            throw new GameRuleException(ErrorCode.GUILD_NAME_TAKEN, "That guild name is taken");
        }
        if (guilds.existsByTagIgnoreCase(tag)) {
            throw new GameRuleException(ErrorCode.GUILD_TAG_TAKEN, "That guild tag is taken");
        }
        Guild guild;
        try {
            guild = guilds.saveAndFlush(new Guild(name, tag, clock.instant()));
            members.saveAndFlush(
                    new GuildMember(playerId, guild.getId(), GuildRole.LEADER, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Two requests at the same moment passed the checks above; the unique indexes decide.
            throw conflictOf(e);
        }
        achievements.grant(playerId, Achievement.GUILD_FOUNDER);
        chronicle.recordNow(
                ChronicleKind.GUILD_FOUNDED,
                ChronicleFacts.empty()
                        .withSide(
                                ChronicleSide.guild(guild.getId(), guild.getName(), guild.getTag()))
                        .withPlayer(chronicle.player(playerId)));
        achievements.grant(playerId, Achievement.GUILD_MEMBER);
        notifyAll(dropInvitationsOf(playerId));
        return myGuild(playerId);
    }

    /**
     * Invites a player by name (leader). The invited player hears of it and decides.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code GUILD_FULL}, {@code
     *     ALREADY_IN_GUILD} (the player is in a guild already), {@code ALREADY_INVITED}
     * @throws ApiException 429 {@code INVITATION_RATE_LIMIT}: the player got an invitation (from
     *     any guild) less than a minute ago
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for an unknown name, an NPC lord or a
     *     deleted player
     */
    @Transactional
    public MyGuildDto invite(long leaderId, String playerName) {
        requireEnabled();
        Locked locked = lockLeader(leaderId);
        Player target =
                players.findHumanByName(playerName.strip())
                        .filter(this::isActive)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        if (members.findGuildIdOf(target.getId()).isPresent()) {
            throw new GameRuleException(
                    ErrorCode.ALREADY_IN_GUILD, "That player is in a guild already");
        }
        if (isFull(locked.guild.getId())) {
            throw new GameRuleException(ErrorCode.GUILD_FULL, "The guild is full");
        }
        if (invitations
                .findByGuildIdAndPlayerId(locked.guild.getId(), target.getId())
                .isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_INVITED, "Already invited");
        }
        requireInviteSlot(target.getId());
        invitations.save(
                new GuildInvitation(
                        locked.guild.getId(), target.getId(), leaderId, clock.instant()));
        events.publishEvent(
                new PlayerNotification(
                        target.getId(),
                        new GameNotification.GuildInvited(
                                locked.guild.getId(), locked.guild.getName())));
        return myGuild(leaderId);
    }

    /**
     * Takes an invitation back (leader).
     *
     * @throws NotFoundException {@code INVITATION_NOT_FOUND} if it isn't one of this guild's
     */
    @Transactional
    public MyGuildDto withdrawInvitation(long leaderId, long invitationId) {
        Locked locked = lockLeader(leaderId);
        GuildInvitation invitation =
                invitations
                        .findById(invitationId)
                        .filter(i -> i.getGuildId().equals(locked.guild.getId()))
                        .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
        invitations.delete(invitation);
        notify(invitation.getPlayerId());
        return myGuild(leaderId);
    }

    /**
     * Joins the guild that sent the invitation; the player's other invitations are dropped.
     *
     * @throws NotFoundException {@code INVITATION_NOT_FOUND} if it isn't the player's
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code ALREADY_IN_GUILD}, {@code
     *     GUILD_FULL}
     */
    @Transactional
    public MyGuildDto accept(long playerId, long invitationId) {
        requireEnabled();
        GuildInvitation invitation = ownInvitation(playerId, invitationId);
        Guild guild = lockGuild(invitation.getGuildId());
        // The leader may have taken the invitation back while this request waited for the lock.
        if (!invitations.existsByIdAndPlayerId(invitationId, playerId)) {
            throw new NotFoundException(ErrorCode.INVITATION_NOT_FOUND);
        }
        if (members.findGuildIdOf(playerId).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
        }
        // The guild row is locked: nobody else is filling the last place meanwhile.
        if (isFull(guild.getId())) {
            throw new GameRuleException(ErrorCode.GUILD_FULL, "The guild is full");
        }
        List<Long> toTell = new ArrayList<>(members.playerIdsOf(guild.getId()));
        try {
            members.saveAndFlush(
                    new GuildMember(playerId, guild.getId(), GuildRole.MEMBER, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Only the inviting guild is locked: an accept of another guild's invitation can win
            // the race for this player (two tabs). The key on guild_member decides, not a 500.
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
        }
        achievements.grant(playerId, Achievement.GUILD_MEMBER);
        toTell.addAll(dropInvitationsOf(playerId));
        notifyAll(toTell);
        return myGuild(playerId);
    }

    /**
     * Says no to an invitation.
     *
     * @throws NotFoundException {@code INVITATION_NOT_FOUND} if it isn't the player's
     */
    @Transactional
    public MyGuildDto decline(long playerId, long invitationId) {
        GuildInvitation invitation = ownInvitation(playerId, invitationId);
        invitations.delete(invitation);
        // the leader's list of open invitations has changed
        members.leaderIdOf(invitation.getGuildId()).ifPresent(this::notify);
        return myGuild(playerId);
    }

    // ---- leaving, kicking, leading ----

    /**
     * Leaves the guild. A guild's last member takes it down with him; the leader of a guild with
     * other human members hands the lead over first. The last human takes it down too: its NPC
     * allies go back to being lone lords (2.1 feature review, finding 07; a lord never leads a
     * guild of humans).
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}, {@code LEADER_MUST_TRANSFER}
     */
    @Transactional
    public MyGuildDto leave(long playerId) {
        Locked locked = lock(playerId);
        long guildId = locked.guild.getId();
        if (locked.me.getRole() == GuildRole.LEADER && members.countHumansOf(guildId) > 1) {
            throw new GameRuleException(
                    ErrorCode.LEADER_MUST_TRANSFER, "Hand the lead over before you leave");
        }
        if (members.countHumansOf(guildId) <= 1) {
            deleteGuild(locked.guild);
            return myGuild(playerId);
        }
        drop(locked);
        return myGuild(playerId);
    }

    /**
     * The leader sends a member away.
     *
     * @throws NotFoundException {@code GUILD_MEMBER_NOT_FOUND} if the player isn't in the leader's
     *     guild
     * @throws ApiException 400 {@code VALIDATION} when the leader names himself ({@link #leave})
     */
    @Transactional
    public MyGuildDto kick(long leaderId, long memberId) {
        Locked locked = lockLeader(leaderId);
        if (memberId == leaderId) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "You can't kick yourself");
        }
        GuildMember target = otherMemberOf(locked, memberId);
        List<Long> everybody = members.playerIdsOf(locked.guild.getId());
        members.delete(target);
        members.flush();
        banner.memberLeft(locked.guild.getId());
        notifyAll(everybody);
        return myGuild(leaderId);
    }

    /**
     * The leader gives the lead to a member and stays a plain member.
     *
     * @throws NotFoundException {@code GUILD_MEMBER_NOT_FOUND}
     * @throws ApiException 400 {@code VALIDATION} when the leader names himself
     */
    @Transactional
    public MyGuildDto transferLeadership(long leaderId, long newLeaderId) {
        Locked locked = lockLeader(leaderId);
        if (newLeaderId == leaderId) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "You lead the guild already");
        }
        GuildMember successor = otherMemberOf(locked, newLeaderId);
        if (players.findById(newLeaderId).filter(Player::isNpc).isPresent()) {
            // an allied lord (2.1 S2b) follows a human's guild, it doesn't lead one
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "An NPC lord can't lead the guild");
        }
        // The database allows one leader per guild: demote first, then promote.
        locked.me.setRole(GuildRole.MEMBER);
        members.saveAndFlush(locked.me);
        successor.setRole(GuildRole.LEADER);
        members.saveAndFlush(successor);
        notifyAll(members.playerIdsOf(locked.guild.getId()));
        return myGuild(leaderId);
    }

    /**
     * Ends the guild for everybody (leader).
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}
     * @throws ApiException 403 {@code NOT_GUILD_LEADER}
     */
    @Transactional
    public MyGuildDto disband(long leaderId) {
        Locked locked = lockLeader(leaderId);
        List<Long> everybody = members.playerIdsOf(locked.guild.getId());
        deleteGuild(locked.guild);
        notifyAll(everybody);
        return myGuild(leaderId);
    }

    /**
     * Changes the description (leader).
     *
     * @throws ApiException 400 {@code VALIDATION} for more than 500 characters
     */
    @Transactional
    public MyGuildDto describe(long leaderId, String description) {
        Locked locked = lockLeader(leaderId);
        locked.guild.describe(GuildText.description(description));
        notifyAll(members.playerIdsOf(locked.guild.getId()));
        return myGuild(leaderId);
    }

    /**
     * The player stops being a member because the host deleted or banned him for good (M14a, M14e):
     * his invitations are dropped, and a leader's guild goes to its longest member, or ends with
     * its last. In a guild of humans the lead goes to the longest human; without one the guild ends
     * and its NPC allies are lone lords again (2.1 feature review, finding 07). Nothing happens for
     * a player in no guild.
     */
    @Transactional
    public void removeBecauseGone(long playerId) {
        notifyAll(dropInvitationsOf(playerId));
        if (members.findGuildIdOf(playerId).isEmpty()) {
            return;
        }
        Locked locked = lock(playerId);
        if (locked.me.getRole() == GuildRole.LEADER) {
            List<GuildMember> others = members.othersOldestFirst(locked.guild.getId(), playerId);
            if (members.countHumansOf(locked.guild.getId()) > 0) {
                // a guild of humans: only a human leads (a lord never leads a guild of humans)
                others = others.stream().filter(m -> isHuman(m.getPlayerId())).toList();
                if (others.isEmpty()) {
                    deleteGuild(locked.guild);
                    return;
                }
            }
            if (!others.isEmpty()) {
                // The old leader goes first, the one-leader index would refuse two.
                members.delete(locked.me);
                members.flush();
                banner.memberLeft(locked.guild.getId());
                GuildMember successor = others.getFirst();
                successor.setRole(GuildRole.LEADER);
                members.saveAndFlush(successor);
                notifyAll(members.playerIdsOf(locked.guild.getId()));
                return;
            }
        }
        drop(locked);
    }

    // ---- guilds of NPC lords (09-npc-lords §13) ----

    /**
     * Founds a guild of two NPC lords (09 §13.1); the first one leads. Lords never invite or
     * accept, so there are no invitations to drop. Lords earn no achievements.
     *
     * @return the new guild's id
     * @throws GameRuleException {@code ALREADY_IN_GUILD}, {@code GUILD_NAME_TAKEN}, {@code
     *     GUILD_TAG_TAKEN}
     */
    @Transactional
    public long foundForLords(long leaderId, long otherId, String rawName, String rawTag) {
        requireLord(leaderId);
        if (otherId != leaderId) {
            requireLord(otherId);
        }
        String name = GuildText.name(rawName);
        String tag = GuildText.tag(rawTag);
        if (members.findGuildIdOf(leaderId).isPresent()
                || members.findGuildIdOf(otherId).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
        }
        if (guilds.existsByNameIgnoreCase(name)) {
            throw new GameRuleException(ErrorCode.GUILD_NAME_TAKEN, "That guild name is taken");
        }
        if (guilds.existsByTagIgnoreCase(tag)) {
            throw new GameRuleException(ErrorCode.GUILD_TAG_TAKEN, "That guild tag is taken");
        }
        try {
            Guild guild = guilds.saveAndFlush(new Guild(name, tag, clock.instant()));
            members.saveAndFlush(
                    new GuildMember(leaderId, guild.getId(), GuildRole.LEADER, clock.instant()));
            if (otherId != leaderId) {
                members.saveAndFlush(
                        new GuildMember(otherId, guild.getId(), GuildRole.MEMBER, clock.instant()));
            }
            return guild.getId();
        } catch (DataIntegrityViolationException e) {
            throw conflictOf(e);
        }
    }

    /**
     * Puts an NPC lord without a guild into a guild of NPC lords that has room ({@code
     * npcGuildMaxMembers}, and never above {@code guildMaxMembers}).
     *
     * @throws GameRuleException {@code ALREADY_IN_GUILD}, {@code GUILD_FULL}
     * @throws NotFoundException {@code GUILD_NOT_FOUND} also for a guild with human members
     */
    @Transactional
    public void addLord(long guildId, long lordId) {
        requireLord(lordId);
        Guild guild = lockGuild(guildId);
        if (members.countHumansOf(guildId) > 0) {
            throw new NotFoundException(ErrorCode.GUILD_NOT_FOUND);
        }
        if (members.findGuildIdOf(lordId).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
        }
        if (members.countByGuildId(guildId) >= lordGuildMax()) {
            throw new GameRuleException(ErrorCode.GUILD_FULL, "The guild is full");
        }
        members.saveAndFlush(
                new GuildMember(lordId, guild.getId(), GuildRole.MEMBER, clock.instant()));
    }

    /**
     * Takes an NPC lord out of its guild: a leader's guild passes to its longest member, the last
     * one ends it (like {@link #removeBecauseGone}). Nothing happens for a lord in no guild.
     */
    @Transactional
    public void removeLord(long lordId) {
        requireLord(lordId);
        removeBecauseGone(lordId);
    }

    // ---- NPC lords as allies in a solo world (2.1 S2b, 12-solo-and-allies §4, §5) ----

    /**
     * Invites an NPC lord into the leader's guild (a solo world only). The lord answers in its next
     * round ({@link #acceptForLord}, {@link #declineForLord}); it may be in a lord guild meanwhile.
     * The wait after a refusal is checked by the caller (the lord's trust).
     *
     * @return the invitation's id
     * @throws ApiException 409 {@code WORLD_NOT_SOLO}; 403 {@code NOT_GUILD_LEADER}
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code NOT_IN_GUILD}, {@code GUILD_FULL},
     *     {@code ALREADY_IN_GUILD} (the lord is in this guild already), {@code LORD_ALLIED} (it is
     *     in another guild with a human), {@code ALREADY_INVITED}
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for a player who is no lord or owns no
     *     village
     */
    @Transactional
    public long inviteLord(long leaderId, long lordId) {
        requireEnabled();
        if (!worldService.settings().soloMode()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_SOLO,
                    "Lords join a player's guild only in a solo world");
        }
        Locked locked = lockLeader(leaderId);
        Player lord =
                players.findById(lordId)
                        .filter(p -> p.isNpc() && p.getVillageCount() > 0)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        long guildId = locked.guild.getId();
        Optional<Long> lordsGuild = members.findGuildIdOf(lord.getId());
        if (lordsGuild.filter(id -> id == guildId).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "The lord is in your guild");
        }
        if (lordsGuild.filter(id -> members.countHumansOf(id) > 0).isPresent()) {
            // another human's ally: it would never answer (finding 08)
            throw new GameRuleException(
                    ErrorCode.LORD_ALLIED, "The lord is sworn to another guild");
        }
        if (isFull(guildId)) {
            throw new GameRuleException(ErrorCode.GUILD_FULL, "The guild is full");
        }
        if (invitations.findByGuildIdAndPlayerId(guildId, lord.getId()).isPresent()) {
            throw new GameRuleException(ErrorCode.ALREADY_INVITED, "Already invited");
        }
        GuildInvitation invitation =
                invitations.saveAndFlush(
                        new GuildInvitation(guildId, lord.getId(), leaderId, clock.instant()));
        notify(leaderId);
        return invitation.getId();
    }

    /**
     * An NPC lord says yes to an invitation (its round, 12 §5): it leaves the guild it is in (a
     * lord guild) and joins the inviting one; its other invitations are dropped. Lords earn no
     * achievements.
     *
     * @return false, changing nothing, if the invitation is gone or the guild has no room
     */
    @Transactional
    public boolean acceptForLord(long lordId, long invitationId) {
        requireLord(lordId);
        GuildInvitation invitation =
                invitations
                        .findById(invitationId)
                        .filter(i -> i.getPlayerId().equals(lordId))
                        .orElse(null);
        if (invitation == null) {
            return false;
        }
        Guild guild = lockGuild(invitation.getGuildId());
        if (!invitations.existsByIdAndPlayerId(invitationId, lordId) || isFull(guild.getId())) {
            return false;
        }
        Optional<Long> current = members.findGuildIdOf(lordId);
        if (current.filter(id -> id.equals(guild.getId())).isPresent()) {
            invitations.delete(invitation);
            return true;
        }
        if (current.isPresent()) {
            leaveKeepingInvitations(lordId);
        }
        List<Long> toTell = new ArrayList<>(members.playerIdsOf(guild.getId()));
        members.saveAndFlush(
                new GuildMember(lordId, guild.getId(), GuildRole.MEMBER, clock.instant()));
        toTell.addAll(dropInvitationsOf(lordId));
        notifyAll(toTell);
        return true;
    }

    /** An NPC lord says no to an invitation (its round); its leader hears of it. */
    @Transactional
    public void declineForLord(long lordId, long invitationId) {
        invitations
                .findById(invitationId)
                .filter(i -> i.getPlayerId().equals(lordId))
                .ifPresent(
                        invitation -> {
                            invitations.delete(invitation);
                            members.leaderIdOf(invitation.getGuildId()).ifPresent(this::notify);
                        });
    }

    /**
     * Whether the guild has a human member: an NPC lord in it is a human's ally (12 §4), not in a
     * lord guild.
     */
    @Transactional(readOnly = true)
    public boolean hasHumans(long guildId) {
        return members.countHumansOf(guildId) > 0;
    }

    /**
     * Takes a lord out of its guild without touching its invitations (it is about to join another
     * one): a leader's guild passes to its longest member, the last one ends it.
     */
    private void leaveKeepingInvitations(long lordId) {
        Locked locked = lock(lordId);
        if (locked.me.getRole() == GuildRole.LEADER) {
            List<GuildMember> others = members.othersOldestFirst(locked.guild.getId(), lordId);
            if (!others.isEmpty()) {
                members.delete(locked.me);
                members.flush();
                banner.memberLeft(locked.guild.getId());
                GuildMember successor = others.getFirst();
                successor.setRole(GuildRole.LEADER);
                members.saveAndFlush(successor);
                notifyAll(members.playerIdsOf(locked.guild.getId()));
                return;
            }
        }
        drop(locked);
    }

    /** Ends every guild of NPC lords (the host switched lord guilds off, 09 §13). */
    @Transactional
    public int disbandLordGuilds() {
        List<Long> ids = members.lordGuildIds();
        for (long id : ids) {
            deleteGuild(lockGuild(id));
        }
        return ids.size();
    }

    /** Whether the guild exists and all its members are NPC lords. */
    @Transactional(readOnly = true)
    public boolean isLordGuild(long guildId) {
        return guilds.existsById(guildId) && members.countHumansOf(guildId) == 0;
    }

    /** The most lords a guild of NPC lords may have. */
    public int lordGuildMax() {
        WorldSettings world = worldService.settings();
        return Math.min(world.npcGuildMaxMembers(), world.guildMaxMembers());
    }

    private void requireLord(long playerId) {
        if (players.findById(playerId).filter(Player::isNpc).isEmpty()) {
            throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
        }
    }

    /** Another interval (tests; zero switches the limit off). */
    public void setInviteInterval(Duration interval) {
        this.inviteInterval = interval;
        lastInvited.clear();
    }

    // ---- helpers ----

    /**
     * Takes the player's invitation slot; given back if the invitation isn't saved after all.
     *
     * @throws ApiException 429 {@code INVITATION_RATE_LIMIT} if he was invited less than {@link
     *     #inviteInterval} ago
     */
    private void requireInviteSlot(long playerId) {
        lastInvited.take(
                playerId,
                clock.instant(),
                inviteInterval,
                () ->
                        new ApiException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                ErrorCode.INVITATION_RATE_LIMIT,
                                "A player can be invited once a minute"));
    }

    private record Locked(Guild guild, GuildMember me) {}

    /** The player's guild, locked, with his membership. */
    private Locked lock(long playerId) {
        long guildId =
                members.findGuildIdOf(playerId)
                        .orElseThrow(
                                () ->
                                        new GameRuleException(
                                                ErrorCode.NOT_IN_GUILD, "Not in a guild"));
        Guild guild = lockGuild(guildId);
        // Loaded only now, after the lock: the role can't be older than the lock.
        GuildMember me =
                members.findById(playerId)
                        .filter(m -> m.getGuildId().equals(guildId))
                        .orElseThrow(
                                () ->
                                        new GameRuleException(
                                                ErrorCode.NOT_IN_GUILD, "Not in a guild"));
        return new Locked(guild, me);
    }

    private Locked lockLeader(long playerId) {
        Locked locked = lock(playerId);
        if (locked.me.getRole() != GuildRole.LEADER) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.NOT_GUILD_LEADER,
                    "Only the leader may do that");
        }
        return locked;
    }

    private Guild lockGuild(long guildId) {
        return guilds.findForUpdate(guildId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GUILD_NOT_FOUND));
    }

    private GuildMember otherMemberOf(Locked locked, long playerId) {
        return members.findById(playerId)
                .filter(m -> m.getGuildId().equals(locked.guild.getId()))
                .orElseThrow(() -> new NotFoundException(ErrorCode.GUILD_MEMBER_NOT_FOUND));
    }

    private GuildInvitation ownInvitation(long playerId, long invitationId) {
        return invitations
                .findById(invitationId)
                .filter(i -> i.getPlayerId().equals(playerId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private boolean isHuman(long playerId) {
        return players.findById(playerId).map(p -> !p.isNpc()).orElse(false);
    }

    /** Removes the membership; the last member takes the guild down. */
    private void drop(Locked locked) {
        long guildId = locked.guild.getId();
        List<Long> everybody = members.playerIdsOf(guildId);
        if (everybody.size() <= 1) {
            deleteGuild(locked.guild);
        } else {
            members.delete(locked.me);
            members.flush();
            banner.memberLeft(guildId);
            notifyAll(everybody);
        }
    }

    private void deleteGuild(Guild guild) {
        // The invitations go with the guild (on delete cascade); the players it invited hear of it.
        List<Long> invited = invitations.invitedPlayerIds(guild.getId());
        if (members.countHumansOf(guild.getId()) > 0) {
            // the chronicle tells the humans' guilds (a lord guild ends quietly)
            chronicle.recordNow(
                    ChronicleKind.GUILD_DISBANDED,
                    ChronicleFacts.empty()
                            .withSide(
                                    ChronicleSide.guild(
                                            guild.getId(), guild.getName(), guild.getTag())));
        }
        // Through the repository, so the persistence context holds no member of a guild that is
        // gone.
        members.deleteAll(members.findAllById(members.playerIdsOf(guild.getId())));
        members.flush();
        guilds.delete(guild);
        guilds.flush();
        notifyAll(invited);
    }

    /**
     * Drops all open invitations of a player (he joined or founded a guild, or is gone).
     *
     * @return the leaders of the guilds that had invited him: their list of open invitations
     *     changed, they must be told
     */
    private List<Long> dropInvitationsOf(long playerId) {
        List<Long> leaders =
                invitations.guildIdsInvitingPlayer(playerId).stream()
                        .map(members::leaderIdOf)
                        .flatMap(Optional::stream)
                        .toList();
        invitations.deleteAllFor(playerId);
        return leaders;
    }

    private boolean isFull(long guildId) {
        return members.countByGuildId(guildId) >= worldService.settings().guildMaxMembers();
    }

    private boolean isLeaderOf(long playerId, long guildId) {
        return members.findById(playerId)
                .filter(m -> m.getGuildId().equals(guildId) && m.getRole() == GuildRole.LEADER)
                .isPresent();
    }

    private void requireEnabled() {
        WorldSettings world = worldService.settings();
        if (!world.guildsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched guilds on");
        }
    }

    /** A human whose account was not deleted (a deleted player can't join anything). */
    private boolean isActive(Player player) {
        return player.getAccountId() != null
                && accounts.findById(player.getAccountId())
                        .filter(a -> !a.isDisabled())
                        .isPresent();
    }

    private MyGuildDto myGuild(long playerId) {
        GuildMember membership = members.findById(playerId).orElse(null);
        if (membership == null) {
            return new MyGuildDto(null, null, invitations.findOfPlayer(playerId));
        }
        Guild guild = guilds.findById(membership.getGuildId()).orElseThrow();
        return new MyGuildDto(
                dto(guild, membership.getRole() == GuildRole.LEADER),
                membership.getRole(),
                List.of());
    }

    private GuildDto dto(Guild guild, boolean withInvitations) {
        List<GuildMemberDto> list = members.membersOf(guild.getId());
        long points = list.stream().mapToLong(GuildMemberDto::points).sum();
        return new GuildDto(
                guild.getId(),
                guild.getName(),
                guild.getTag(),
                guild.getDescription(),
                guild.getCreatedAt(),
                points,
                banner.levelOf(guild.getId()),
                list,
                withInvitations ? invitations.findOfGuild(guild.getId()) : null);
    }

    private void notify(long playerId) {
        events.publishEvent(new PlayerNotification(playerId, new GameNotification.GuildChanged()));
    }

    private void notifyAll(List<Long> playerIds) {
        playerIds.stream().distinct().forEach(this::notify);
    }

    /** The unique index that was hit tells which rule the request broke. */
    private static GameRuleException conflictOf(DataIntegrityViolationException e) {
        String message = Objects.toString(e.getMostSpecificCause().getMessage(), "");
        if (message.contains("guild_tag_lower")) {
            return new GameRuleException(ErrorCode.GUILD_TAG_TAKEN, "That guild tag is taken");
        }
        if (message.contains("guild_name_lower")) {
            return new GameRuleException(ErrorCode.GUILD_NAME_TAKEN, "That guild name is taken");
        }
        return new GameRuleException(ErrorCode.ALREADY_IN_GUILD, "Already in a guild");
    }
}
