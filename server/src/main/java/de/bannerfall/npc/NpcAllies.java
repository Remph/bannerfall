package de.bannerfall.npc;

import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.guild.GuildService;
import de.bannerfall.message.LordLetter;
import de.bannerfall.message.MessageService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The allies step of a lord's round (2.1 S2b, 12-solo-and-allies §4, §5): an ally whose trust in a
 * human of its guild fell below 0 leaves with a letter; otherwise the lord answers its open
 * invitations, yes when its trust in the inviting human reaches its bar and the guild has room, no
 * with the reason. Every answer is a letter. Runs once per round, before the guild step, so a lord
 * that left can find a lord guild again in the same round.
 */
@Component
class NpcAllies {

    private static final Logger log = LoggerFactory.getLogger(NpcAllies.class);

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final GuildService guildService;
    private final MessageService messages;
    private final NpcTrust trust;
    private final NpcBrainRepository brains;
    private final NpcContacts contacts;
    private final Chronicle chronicle;
    private final NpcHelpSettings helpSettings;

    NpcAllies(
            JdbcClient jdbc,
            WorldService worldService,
            GuildService guildService,
            MessageService messages,
            NpcTrust trust,
            NpcBrainRepository brains,
            NpcContacts contacts,
            NpcHelpSettings helpSettings,
            Chronicle chronicle) {
        this.chronicle = chronicle;
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.guildService = guildService;
        this.messages = messages;
        this.trust = trust;
        this.brains = brains;
        this.contacts = contacts;
        this.helpSettings = helpSettings;
    }

    /** An open invitation of the lord. */
    record Invitation(long id, long guildId, long invitedBy, String guildName) {}

    /** The lord's guild as this step sees it. */
    record Guild(long id, String name, List<Long> humans, int members) {}

    /** The step at {@code now} (game time). */
    void run(long lordId, Instant now) {
        WorldSettings world = worldService.settings();
        if (!world.guildsEnabled()) {
            return;
        }
        Guild guild = guildOf(lordId);
        if (guild != null && !guild.humans().isEmpty()) {
            // an ally doesn't look for another guild while it stays: it declines, so no
            // invitation waits for ever (finding 08; one from before the refusal at the invite)
            invitations(lordId).forEach(i -> guildService.declineForLord(lordId, i.id()));
            leaveIfDistrusted(lordId, guild, now);
            return;
        }
        List<Invitation> open = invitations(lordId);
        if (open.isEmpty()) {
            return;
        }
        if (!world.soloMode() || !trust.active()) {
            // invitations to lords exist only in a solo world; one left from before is dropped
            open.forEach(i -> guildService.declineForLord(lordId, i.id()));
            return;
        }
        answer(lordId, guild != null, open, now);
    }

    /**
     * An ally whose trust in the guild's leader is below 0 leaves (12 §5). Only the leader counts
     * (2.1 feature review, finding 02): a friend who joins later starts at 0 and must not take the
     * leader's allies with one scouting. An attack of any human of the guild still makes it leave
     * at once ({@link NpcTrust}).
     */
    private void leaveIfDistrusted(long lordId, Guild guild, Instant now) {
        long leader = leaderOf(guild, lordId, now);
        boolean distrusted = NpcTrustRules.leaves(trust.trust(lordId, leader, now));
        if (!distrusted) {
            warn(lordId, guild, now);
            return;
        }
        guildService.removeLord(lordId);
        chronicle.record(
                ChronicleKind.ALLY_LEFT,
                now,
                ChronicleFacts.empty()
                        .withLord(chronicle.player(lordId))
                        .withSide(chronicle.guild(guild.id())));
        for (long human : guild.humans()) {
            messages.sendLetter(
                    lordId, human, new LordLetter(LordLetter.Kind.LEFT_TRUST, guild.name()));
        }
        log.info("NPC lord {} left guild {}: trust below 0", lordId, guild.id());
    }

    /**
     * The warning (2.1 S4, 12 §9): an ally whose trust in a human of its guild falls towards 0
     * writes him once; again only after its trust rose well above the mark.
     */
    private void warn(long lordId, Guild guild, Instant now) {
        for (long human : guild.humans()) {
            int shown = NpcTrustRules.shown(trust.trust(lordId, human, now));
            boolean warned = trust.warned(lordId, human);
            switch (NpcHelpRules.warning(shown, warned, helpSettings)) {
                case WRITE -> {
                    trust.warned(lordId, human, true, now);
                    messages.sendLetter(
                            lordId, human, new LordLetter(LordLetter.Kind.WARNING, guild.name()));
                    log.info("NPC lord {} warns {}: trust {}", lordId, human, shown);
                }
                case REARM -> trust.warned(lordId, human, false, now);
                case NONE -> {}
            }
        }
    }

    /**
     * The lord's answers, oldest invitation first: the first yes takes it into that guild (its
     * other invitations go with it), every no is a letter and makes that human wait.
     */
    private void answer(long lordId, boolean inLordGuild, List<Invitation> open, Instant now) {
        NpcPersonality personality = brains.findById(lordId).orElseThrow().getPersonality();
        int bar = NpcTrustRules.bar(trust.settings(), personality, inLordGuild);
        int maxMembers = worldService.settings().guildMaxMembers();
        for (Invitation invitation : open) {
            double value = trust.trust(lordId, invitation.invitedBy(), now);
            boolean room = members(invitation.guildId()) < maxMembers;
            NpcTrustRules.Answer answer = NpcTrustRules.answer(value, bar, room);
            if (answer == NpcTrustRules.Answer.YES
                    && guildService.acceptForLord(lordId, invitation.id())) {
                for (long human : humansOf(invitation.guildId())) {
                    contacts.met(human, lordId, now);
                }
                // its trust in the inviter stands at the bar: a fall towards 0 earns a warning
                trust.warned(lordId, invitation.invitedBy(), false, now);
                chronicle.record(
                        ChronicleKind.ALLY_JOINED,
                        now,
                        ChronicleFacts.empty()
                                .withLord(chronicle.player(lordId))
                                .withSide(chronicle.guild(invitation.guildId()))
                                .withPlayer(chronicle.player(invitation.invitedBy())));
                messages.sendLetter(
                        lordId,
                        invitation.invitedBy(),
                        new LordLetter(LordLetter.Kind.JOINED, invitation.guildName()));
                log.info(
                        "NPC lord {} joined guild {} (trust {})",
                        lordId,
                        invitation.guildId(),
                        NpcTrustRules.shown(value));
                return;
            }
            guildService.declineForLord(lordId, invitation.id());
            trust.refused(lordId, invitation.invitedBy(), now);
            messages.sendLetter(
                    lordId,
                    invitation.invitedBy(),
                    new LordLetter(
                            answer == NpcTrustRules.Answer.NOT_ENOUGH_TRUST
                                    ? LordLetter.Kind.REFUSED_TRUST
                                    : LordLetter.Kind.REFUSED_FULL,
                            invitation.guildName()));
        }
    }

    private Guild guildOf(long lordId) {
        return jdbc.sql(
                        """
                        select g.id, g.name from guild g join guild_member m on m.guild_id = g.id
                        where m.player_id = :lord
                        """)
                .param("lord", lordId)
                .query(
                        (rs, n) ->
                                new Guild(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        humansOf(rs.getLong("id")),
                                        members(rs.getLong("id"))))
                .optional()
                .orElse(null);
    }

    /**
     * The human whose trust keeps the ally: the guild's leader; the human it trusts most when the
     * leader is not a human (not expected: a lord never leads a guild of humans).
     */
    private long leaderOf(Guild guild, long lordId, Instant now) {
        return jdbc.sql(
                        """
                        select m.player_id from guild_member m join player p on p.id = m.player_id
                        where m.guild_id = :guild and m.role = 'LEADER' and not p.npc
                        """)
                .param("guild", guild.id())
                .query(Long.class)
                .optional()
                .orElseGet(
                        () ->
                                guild.humans().stream()
                                        .max(
                                                Comparator.comparingDouble(
                                                        human -> trust.trust(lordId, human, now)))
                                        .orElseThrow());
    }

    private List<Long> humansOf(long guildId) {
        return jdbc.sql(
                        """
                        select m.player_id from guild_member m join player p on p.id = m.player_id
                        where m.guild_id = :guild and not p.npc order by m.player_id
                        """)
                .param("guild", guildId)
                .query(Long.class)
                .list();
    }

    private int members(long guildId) {
        return jdbc.sql("select count(*) from guild_member where guild_id = :guild")
                .param("guild", guildId)
                .query(Integer.class)
                .single();
    }

    private List<Invitation> invitations(long lordId) {
        return jdbc.sql(
                        """
                        select i.id, i.guild_id, i.invited_by, g.name from guild_invitation i
                        join guild g on g.id = i.guild_id
                        where i.player_id = :lord order by i.id
                        """)
                .param("lord", lordId)
                .query(
                        (rs, n) ->
                                new Invitation(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getString(4)))
                .list();
    }
}
