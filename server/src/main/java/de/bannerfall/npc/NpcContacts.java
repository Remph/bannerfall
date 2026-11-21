package de.bannerfall.npc;

import de.bannerfall.common.Savepoint;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportsCreated;
import de.bannerfall.report.SupportReport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * First contact between a human and an NPC lord (09-npc-lords §16.7, table {@code npc_contact}):
 * the first report a human gets in which a lord is the other side (a battle or scouting either way,
 * a support from or to the lord) writes the pair once, in the transaction of the report. From then
 * on the human sees the lord's variant on its player page.
 */
@Component
public class NpcContacts {

    private static final Logger log = LoggerFactory.getLogger(NpcContacts.class);

    private final JdbcClient jdbc;
    private final NpcBrainRepository brains;
    private final NpcTemplates templates;

    /**
     * A savepoint inside the report's transaction (2.1 feature review, finding 11): a bug here
     * rolls back only the contact, never the battle.
     */
    private final Savepoint savepoint;

    NpcContacts(
            JdbcClient jdbc,
            NpcBrainRepository brains,
            NpcTemplates templates,
            Savepoint savepoint) {
        this.savepoint = savepoint;
        this.jdbc = jdbc;
        this.brains = brains;
        this.templates = templates;
    }

    /**
     * A new report between two players: the pair's contact, if one is a human and one a lord.
     * Behind a savepoint: an error is logged, the report and its battle stand.
     */
    @EventListener
    void onReports(ReportsCreated created) {
        try {
            savepoint.run(() -> contactOf(created));
        } catch (RuntimeException e) {
            log.error("First contact: the {} report could not be checked", created.kind(), e);
        }
    }

    private void contactOf(ReportsCreated created) {
        Long a;
        Long b;
        switch (created.payload()) {
            case BattleReport battle -> {
                a = battle.attacker().playerId();
                b = battle.defender().playerId();
            }
            case SupportReport support -> {
                a = support.from().playerId();
                b = support.to().playerId();
            }
            default -> {
                return;
            }
        }
        if (a == null || b == null || a.equals(b)) {
            return; // barbarians and a lord's own moves meet nobody
        }
        if (created.recipients().contains(a)) {
            met(a, b, created.happenedAt());
        }
        if (created.recipients().contains(b)) {
            met(b, a, created.happenedAt());
        }
    }

    /**
     * Writes the contact of {@code playerId} with {@code otherId} at {@code at} if the first is a
     * human and the second a lord and they haven't met yet.
     */
    void met(long playerId, long otherId, Instant at) {
        jdbc.sql(
                        """
                        insert into npc_contact (player_id, lord_id, since)
                        select h.id, l.id, :since from player h, player l
                        where h.id = :player and l.id = :lord and not h.npc and l.npc
                        on conflict do nothing
                        """)
                .param("player", playerId)
                .param("lord", otherId)
                .param("since", Timestamp.from(at))
                .update();
    }

    /** When {@code playerId} first met the lord {@code lordId}, if ever. */
    public Optional<Instant> since(long playerId, long lordId) {
        return jdbc.sql(
                        "select since from npc_contact where player_id = :player and lord_id ="
                                + " :lord")
                .param("player", playerId)
                .param("lord", lordId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    /**
     * The lord's variant as {@code viewerId} sees it (09 §16.7): only once the viewer has met the
     * lord; the key is null for a lord without a variant (spawned before 2.1 or as PLAIN). Empty
     * before the first contact and for a player who is no lord.
     */
    public Optional<LordVariantDto> variantFor(long viewerId, long lordId) {
        return brains.findById(lordId)
                .flatMap(
                        brain ->
                                since(viewerId, lordId)
                                        .map(
                                                at ->
                                                        new LordVariantDto(
                                                                templates.variantKey(brain), at)));
    }
}
