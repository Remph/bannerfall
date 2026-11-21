package de.bannerfall.npc;

import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.guild.GuildService;
import de.bannerfall.message.LordLetter;
import de.bannerfall.message.MessageService;
import de.bannerfall.npc.NpcTrustRules.Change;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportsCreated;
import de.bannerfall.report.TradeReport;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trust between NPC lords and humans (2.1 S2b, 12-solo-and-allies §5, table {@code npc_trust}):
 * kept per lord and human, moved by what the human does to the lord, its guild mates and its
 * enemies, read by the lord when it answers an invitation and when it decides to stay in a human's
 * guild, and shown on the diplomacy page. Kept in every world with guilds and lord guilds on; only
 * a solo world lets a lord join a human's guild.
 *
 * <p>The events come from the reports (like the first contacts, {@link NpcContacts}): {@link
 * ReportsCreated} runs in the transaction of the battle, delivery or conquest, so a change commits
 * or rolls back with what caused it. All times are game time.
 */
@Component
public class NpcTrust {

    private static final Logger log = LoggerFactory.getLogger(NpcTrust.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final TypeReference<List<Change>> CHANGES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final NpcTrustSettings settings;
    private final GuildService guildService;
    private final MessageService messages;
    private final Chronicle chronicle;

    NpcTrust(
            JdbcClient jdbc,
            WorldService worldService,
            NpcTrustSettings settings,
            GuildService guildService,
            MessageService messages,
            Chronicle chronicle) {
        this.chronicle = chronicle;
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.settings = settings;
        this.guildService = guildService;
        this.messages = messages;
    }

    /**
     * A lord's trust towards a human as stored, with the time it was at.
     *
     * @param inviteAfter the next invitation waits until then (after a no); null if it needn't
     * @param changes the last three changes, newest first
     */
    public record Row(
            long lordId,
            long playerId,
            double trust,
            Instant trustAt,
            Instant inviteAfter,
            Instant dayStart,
            int daySupport,
            long dayShipped,
            List<Change> changes) {}

    /** Trust is kept while guilds and lord guilds are both on (the diplomacy page is shown). */
    public boolean active() {
        WorldSettings world = worldService.settings();
        return world.guildsEnabled() && world.npcGuildsEnabled();
    }

    /** The numbers (bars, waits) for the pages and the lords' answers. */
    public NpcTrustSettings settings() {
        return settings;
    }

    // ---- reading ----

    /** The lord's trust towards the human at {@code now} (game time), drifted; 0 if none yet. */
    public double trust(long lordId, long playerId, Instant now) {
        return row(lordId, playerId).map(row -> current(row, now)).orElse(0.0);
    }

    /** The stored value drifted from its time to {@code now}. */
    public double current(Row row, Instant now) {
        return NpcTrustRules.drifted(
                row.trust(),
                Duration.between(row.trustAt(), now),
                worldService.settings().speed(),
                settings.driftPerDay());
    }

    /** The stored row of the pair, if the two have one. */
    public Optional<Row> row(long lordId, long playerId) {
        return jdbc.sql(SELECT + " where lord_id = :lord and player_id = :player")
                .param("lord", lordId)
                .param("player", playerId)
                .query(this::map)
                .optional();
    }

    /** Every lord's row towards the human. */
    public List<Row> rowsOf(long playerId) {
        return jdbc.sql(SELECT + " where player_id = :player")
                .param("player", playerId)
                .query(this::map)
                .list();
    }

    private static final String SELECT =
            """
            select lord_id, player_id, trust, trust_at, invite_after, day_start, day_support,
                   day_shipped, changes::text as changes
            from npc_trust
            """;

    private Row map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(
                rs.getLong("lord_id"),
                rs.getLong("player_id"),
                rs.getDouble("trust"),
                rs.getTimestamp("trust_at").toInstant(),
                instant(rs.getTimestamp("invite_after")),
                instant(rs.getTimestamp("day_start")),
                rs.getInt("day_support"),
                rs.getLong("day_shipped"),
                JSON.readValue(rs.getString("changes"), CHANGES));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    // ---- changing ----

    /**
     * Moves the lord's trust towards the human by {@code delta} at {@code now} and notes the
     * change; nothing for a delta that rounds to 0.
     */
    void change(
            long lordId,
            long playerId,
            double delta,
            TrustReason reason,
            String village,
            Instant now) {
        Row row = locked(lordId, playerId, now);
        double before = current(row, now);
        double after =
                reason == TrustReason.CONQUERED
                        ? NpcTrustRules.MIN
                        : NpcTrustRules.plus(before, delta);
        int shownDelta = NpcTrustRules.shown(after) - NpcTrustRules.shown(before);
        if (shownDelta == 0 && reason != TrustReason.CONQUERED) {
            // too small to show: still kept (small shipments add up through the day's total)
            write(row, after, now, row.changes());
            return;
        }
        write(
                row,
                after,
                now,
                NpcTrustRules.withChange(row.changes(), new Change(reason, shownDelta, village)));
        log.debug("Trust of lord {} towards {}: {} ({})", lordId, playerId, after, reason);
    }

    /** After a no: the next invitation from this human waits a day (÷ speed, 12 §5). */
    void refused(long lordId, long playerId, Instant now) {
        locked(lordId, playerId, now);
        Duration wait =
                NpcTargeting.scaled(
                        Duration.ofSeconds(Math.round(settings.refusalWaitHours() * 3600)),
                        worldService.settings().speed());
        jdbc.sql(
                        "update npc_trust set invite_after = :after where lord_id = :lord and"
                                + " player_id = :player")
                .param("after", Timestamp.from(now.plus(wait)))
                .param("lord", lordId)
                .param("player", playerId)
                .update();
    }

    /**
     * The warning letter (2.1 S4): whether the lord has written its warning to the human; {@code
     * warned} sets it (written) or takes it back (ready to write again). A new pair starts as
     * written (2.1 feature review, finding 02): a lord warns only a human whose trust once stood
     * high (above {@code warn-again-above}, or the inviter's at the lord's join), never a new guild
     * mate it has never dealt with.
     */
    boolean warned(long lordId, long playerId) {
        return jdbc.sql(
                        "select warned from npc_trust where lord_id = :lord and player_id ="
                                + " :player")
                .param("lord", lordId)
                .param("player", playerId)
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    void warned(long lordId, long playerId, boolean warned, Instant now) {
        locked(lordId, playerId, now);
        jdbc.sql(
                        "update npc_trust set warned = :warned where lord_id = :lord and"
                                + " player_id = :player")
                .param("warned", warned)
                .param("lord", lordId)
                .param("player", playerId)
                .update();
    }

    /** The pair's row, created at 0 if it doesn't exist yet, locked for this transaction. */
    private Row locked(long lordId, long playerId, Instant now) {
        jdbc.sql(
                        """
                        insert into npc_trust (lord_id, player_id, trust, trust_at, warned)
                        select l.id, h.id, 0, :now, true from player l, player h
                        where l.id = :lord and h.id = :player and l.npc and not h.npc
                        on conflict do nothing
                        """)
                .param("now", Timestamp.from(now))
                .param("lord", lordId)
                .param("player", playerId)
                .update();
        return jdbc.sql(SELECT + " where lord_id = :lord and player_id = :player for update")
                .param("lord", lordId)
                .param("player", playerId)
                .query(this::map)
                .single();
    }

    private void write(Row row, double trust, Instant now, List<Change> changes) {
        jdbc.sql(
                        """
                        update npc_trust set trust = :trust, trust_at = :now,
                            changes = cast(:changes as jsonb)
                        where lord_id = :lord and player_id = :player
                        """)
                .param("trust", trust)
                .param("now", Timestamp.from(now))
                .param("changes", JSON.writeValueAsString(changes))
                .param("lord", row.lordId())
                .param("player", row.playerId())
                .update();
    }

    /**
     * The day of the caps (a game day ÷ speed from its first gain): the counters of a day that is
     * over start again at {@code now}. Returns the row as it is after that.
     */
    private Row today(Row row, Instant now) {
        Duration day = NpcTrustRules.day(worldService.settings().speed());
        if (row.dayStart() != null && now.isBefore(row.dayStart().plus(day))) {
            return row;
        }
        jdbc.sql(
                        """
                        update npc_trust set day_start = :now, day_support = 0, day_shipped = 0
                        where lord_id = :lord and player_id = :player
                        """)
                .param("now", Timestamp.from(now))
                .param("lord", row.lordId())
                .param("player", row.playerId())
                .update();
        return new Row(
                row.lordId(),
                row.playerId(),
                row.trust(),
                row.trustAt(),
                row.inviteAfter(),
                now,
                0,
                0,
                row.changes());
    }

    // ---- what moves it (12 §5) ----

    /** A new report: the trust it moves, if trust is kept in this world. */
    @EventListener
    void onReports(ReportsCreated created) {
        if (!active()) {
            return;
        }
        Instant at = created.happenedAt();
        switch (created.payload()) {
            case BattleReport battle
                    when created.kind() == ReportKind.ATTACK
                            || created.kind() == ReportKind.SCOUT ->
                    battle(battle, created.kind() == ReportKind.ATTACK, at);
            case BattleReport battle when created.kind() == ReportKind.DEFENSE ->
                    supported(battle, created, at);
            case TradeReport trade -> shipped(trade, at);
            case ConquestReport conquest when created.kind() == ReportKind.CONQUEST ->
                    conquered(conquest, at);
            default -> {}
        }
    }

    /** The attacker's copy of a battle (ATTACK or SCOUT): one per battle. */
    private void battle(BattleReport battle, boolean attack, Instant at) {
        Long attacker = battle.attacker().playerId();
        Long defender = battle.defender().playerId();
        if (attacker == null
                || defender == null
                || attacker.equals(defender)
                || !isHuman(attacker)) {
            return;
        }
        String village = battle.defender().name();
        // before an ally leaves below: its mates are the human's other allies
        List<Long> mates = attack ? lordMatesOf(defender, attacker) : List.of();
        if (isLord(defender)) {
            change(
                    defender,
                    attacker,
                    attack ? settings.attacked() : settings.scouted(),
                    attack ? TrustReason.ATTACKED : TrustReason.SCOUTED,
                    village,
                    at);
            if (attack && sameGuild(defender, attacker)) {
                // an ally the human attacks leaves at once, whatever its trust (12 §5)
                guildService.removeLord(defender);
                chronicle.record(
                        ChronicleKind.ALLY_LEFT,
                        at,
                        ChronicleFacts.empty()
                                .withLord(chronicle.player(defender))
                                .withSide(chronicle.guildOf(attacker))
                                .withPlayer(chronicle.player(attacker))
                                .withAttacked(true));
                messages.sendLetter(
                        defender,
                        attacker,
                        new LordLetter(LordLetter.Kind.LEFT_ATTACKED, guildNameOf(attacker)));
                log.info("NPC lord {} left the guild of {}: attacked", defender, attacker);
            }
        }
        if (attack) {
            for (long mate : mates) {
                change(
                        mate,
                        attacker,
                        settings.mateAttacked(),
                        TrustReason.MATE_ATTACKED,
                        village,
                        at);
            }
            if (battle.outcome() == BattleOutcome.ATTACKER_WON) {
                grudgeWin(attacker, defender, village, at);
            }
        }
    }

    /**
     * The supporters' copy of a battle at a lord's village (only written when there was a main
     * battle): every human whose support stood there, against somebody else's attack.
     */
    private void supported(BattleReport battle, ReportsCreated created, Instant at) {
        Long lord = battle.defender().playerId();
        Long attacker = battle.attacker().playerId();
        if (lord == null || !isLord(lord)) {
            return;
        }
        for (long supporter : created.recipients()) {
            if (supporter == lord
                    || supporter == (attacker == null ? -1 : attacker)
                    || !isHuman(supporter)) {
                continue;
            }
            Row row = today(locked(lord, supporter, at), at);
            double gain =
                    NpcTrustRules.capped(
                            settings.support(), row.daySupport(), settings.supportPerDay());
            if (gain <= 0) {
                continue;
            }
            jdbc.sql(
                            "update npc_trust set day_support = day_support + :gain where lord_id"
                                    + " = :lord and player_id = :player")
                    .param("gain", (int) Math.round(gain))
                    .param("lord", lord)
                    .param("player", supporter)
                    .update();
            change(lord, supporter, gain, TrustReason.SUPPORT, battle.defender().name(), at);
        }
    }

    /** Merchants of a human delivered resources to a lord. */
    private void shipped(TradeReport trade, Instant at) {
        Long from = trade.from().playerId();
        Long to = trade.to().playerId();
        if (from == null || to == null || !isHuman(from) || !isLord(to)) {
            return;
        }
        long amount = Math.max(0, trade.resources().total() - trade.lost().total());
        if (amount == 0) {
            return;
        }
        Row row = today(locked(to, from, at), at);
        double gain =
                NpcTrustRules.shippedGain(
                        row.dayShipped(),
                        amount,
                        settings.shippedPerThousand(),
                        settings.shippedPerDay());
        jdbc.sql(
                        "update npc_trust set day_shipped = day_shipped + :amount where lord_id ="
                                + " :lord and player_id = :player")
                .param("amount", amount)
                .param("lord", to)
                .param("player", from)
                .update();
        if (gain > 0) {
            change(to, from, gain, TrustReason.SHIPPED, trade.to().name(), at);
        }
    }

    /** A human conquered one of a lord's villages: its trust goes straight to the bottom. */
    private void conquered(ConquestReport conquest, Instant at) {
        Long lord = conquest.village().playerId();
        Long conqueror = conquest.from().playerId();
        if (lord == null || conqueror == null || !isLord(lord) || !isHuman(conqueror)) {
            return;
        }
        change(lord, conqueror, 0, TrustReason.CONQUERED, conquest.village().name(), at);
    }

    /**
     * A human beat {@code beaten}: every other lord with an active grudge against it trusts the
     * human more, once per grudge (the grudge is told apart by its end).
     */
    private void grudgeWin(long human, long beaten, String village, Instant at) {
        record Holder(long lordId, Instant until) {}
        List<Holder> holders =
                jdbc.sql(
                                """
                                select b.player_id,
                                       cast(b.memory -> 'grudges' -> cast(:target as text)
                                            ->> 'until' as timestamptz) as until
                                from npc_brain b join player p on p.id = b.player_id
                                where b.player_id <> :target and p.village_count > 0
                                  and (b.memory -> 'grudges' -> cast(:target as text)) is not null
                                """)
                        .param("target", beaten)
                        .query((rs, n) -> new Holder(rs.getLong(1), instant(rs.getTimestamp(2))))
                        .list();
        for (Holder holder : holders) {
            if (holder.until() == null || !holder.until().isAfter(at)) {
                continue;
            }
            int credited =
                    jdbc.sql(
                                    """
                                    insert into npc_trust_credit
                                        (lord_id, player_id, target_id, grudge_until)
                                    values (:lord, :player, :target, :until)
                                    on conflict (lord_id, player_id, target_id) do update
                                        set grudge_until = excluded.grudge_until
                                        where npc_trust_credit.grudge_until
                                              <> excluded.grudge_until
                                    """)
                            .param("lord", holder.lordId())
                            .param("player", human)
                            .param("target", beaten)
                            .param("until", Timestamp.from(holder.until()))
                            .update();
            if (credited > 0) {
                change(
                        holder.lordId(),
                        human,
                        settings.grudgeWin(),
                        TrustReason.GRUDGE_WIN,
                        village,
                        at);
            }
        }
    }

    // ---- who is who ----

    private boolean isHuman(long playerId) {
        return Boolean.FALSE.equals(npc(playerId));
    }

    private boolean isLord(long playerId) {
        return Boolean.TRUE.equals(npc(playerId));
    }

    private Boolean npc(long playerId) {
        return jdbc.sql("select npc from player where id = :id")
                .param("id", playerId)
                .query(Boolean.class)
                .optional()
                .orElse(null);
    }

    private boolean sameGuild(long a, long b) {
        return jdbc.sql(
                                """
                                select count(*) from guild_member x join guild_member y
                                    on y.guild_id = x.guild_id
                                where x.player_id = :a and y.player_id = :b
                                """)
                        .param("a", a)
                        .param("b", b)
                        .query(Long.class)
                        .single()
                > 0;
    }

    /** The lords in {@code playerId}'s guild other than it and {@code except}. */
    private List<Long> lordMatesOf(long playerId, long except) {
        return jdbc.sql(
                        """
                        select o.player_id from guild_member me
                        join guild_member o on o.guild_id = me.guild_id
                        join player p on p.id = o.player_id
                        where me.player_id = :player and o.player_id <> :player
                          and o.player_id <> :except and p.npc
                        order by o.player_id
                        """)
                .param("player", playerId)
                .param("except", except)
                .query(Long.class)
                .list();
    }

    private String guildNameOf(long playerId) {
        return jdbc.sql(
                        "select g.name from guild g join guild_member m on m.guild_id = g.id where"
                                + " m.player_id = :player")
                .param("player", playerId)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
