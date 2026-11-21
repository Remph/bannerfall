package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.guild.GuildService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Allies in a solo world (2.1 S2b, 12-solo-and-allies §5): the Solo preset with the scripted humans
 * of {@link SimulatedHumans}. Two of them win lords as allies at the start (their trust is set to
 * 80, which every bar allows, then they invite):
 *
 * <ul>
 *   <li>the first farmer keeps his two allies by supporting them (+10 every game day, as a support
 *       that stands in their villages would), and never attacks lords: his guild must not win the
 *       world, the lords must not carry him to a win alone;
 *   <li>the warlord allies with the lords he attacks anyway (his nearest ones): every attack on an
 *       ally must end the alliance at once, so an ally is never a free farm.
 * </ul>
 *
 * A class of its own for a JVM of its own, see {@link NpcSimulationTest}.
 */
@Tag("simulation")
@IntegrationTest
class NpcSimulationAlliesTest extends NpcSimulationTest {

    private static final double SEEDED_TRUST = 80;
    private static final double SUPPORT = 10;

    @Autowired GuildService guildService;
    @Autowired NpcTrust trust;
    @Autowired TransactionTemplate tx;

    private long farmer;
    private long warlord;
    private long farmerGuild;
    private long warlordGuild;
    private Instant start;
    private Instant nextSupport;
    private long lastReport;

    /** Allies of the farmer as the last step saw them, and the most villages his guild held. */
    private int farmerAllies;

    private int farmerAlliesMax;
    private double farmerShareMax;
    private Instant farmerAlliesLost;

    /** The warlord's attacks that arrived at a lord of his own guild, and how many it survived. */
    private int attacksOnAllies;

    private int alliancesKeptAfterAttack;
    private int warlordJoins;
    private final Map<Long, Long> guildOfLord = new HashMap<>();

    @Override
    boolean activeHumans() {
        return true;
    }

    @Override
    void scenarioSettings() {
        jdbc.sql(
                        """
                        update world_settings set speed = 4, npc_lord_count = 8,
                            barbarian_initial_count = 40, guilds_enabled = true,
                            npc_guilds_enabled = true, npc_guild_max_members = 3,
                            guild_max_members = 10,
                            victory_mode = 'DOMINATION', domination_percent = 60,
                            victory_hold_hours = 48, night_bonus_enabled = false,
                            solo_mode = true
                        """)
                .update();
    }

    @Override
    void scenarioStart(List<Long> humans, Instant start) {
        this.start = start;
        this.nextSupport = start;
        // SimulatedHumans registers two farmers, the raider and the warlord, in that order
        farmer = humans.get(0);
        warlord = humans.get(3);
        farmerGuild = guildService.create(farmer, "Bannerbund", "BB").guild().id();
        warlordGuild = guildService.create(warlord, "Eisenhand", "EH").guild().id();
        // the warlord first: he takes the lords he attacks anyway, the farmer the next free ones
        for (long lord : nearestLords(warlord, 2)) {
            ally(warlord, lord, start);
        }
        for (long lord : nearestLords(farmer, 2)) {
            ally(farmer, lord, start);
        }
        lastReport = jdbc.sql("select coalesce(max(id), 0) from report").query(Long.class).single();
    }

    private void ally(long human, long lord, Instant now) {
        tx.executeWithoutResult(
                status -> {
                    trust.change(lord, human, SEEDED_TRUST, TrustReason.SUPPORT, "-", now);
                    guildService.inviteLord(human, lord);
                });
    }

    /** The lords nearest to the human's first village that are not invited yet. */
    private List<Long> nearestLords(long human, int count) {
        return jdbc.sql(
                        """
                        select p.id from player p
                        join village v on v.player_id = p.id
                        cross join (select x, y from village where player_id = :human
                                    order by id limit 1) h
                        where p.npc
                          and not exists (select 1 from guild_invitation i where i.player_id = p.id)
                        group by p.id
                        order by min((v.x - h.x) * (v.x - h.x) + (v.y - h.y) * (v.y - h.y)), p.id
                        limit :count
                        """)
                .param("human", human)
                .param("count", count)
                .query(Long.class)
                .list();
    }

    @Override
    void scenarioStep(Instant now) {
        // the attacks that arrived since the last step: one on a lord of the warlord's guild must
        // have ended the alliance in the same transaction
        List<long[]> attacks =
                jdbc.sql(
                                """
                                select id, (payload->'defender'->>'playerId')::bigint from report
                                where id > :last and player_id = :warlord and kind = 'ATTACK'
                                  and payload->'defender'->>'playerId' is not null order by id
                                """)
                        .param("last", lastReport)
                        .param("warlord", warlord)
                        .query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)})
                        .list();
        for (long[] attack : attacks) {
            lastReport = Math.max(lastReport, attack[0]);
            Long before = guildOfLord.get(attack[1]);
            if (before != null && before == warlordGuild) {
                attacksOnAllies++;
                if (warlordGuild == guildOf(attack[1])) {
                    alliancesKeptAfterAttack++;
                }
            }
        }
        lastReport =
                Math.max(
                        lastReport,
                        jdbc.sql("select coalesce(max(id), 0) from report")
                                .query(Long.class)
                                .single());
        guildOfLord.clear();
        jdbc.sql(
                        """
                        select m.player_id, m.guild_id from guild_member m
                        join player p on p.id = m.player_id where p.npc
                        """)
                .query(
                        (rs, n) -> {
                            guildOfLord.put(rs.getLong(1), rs.getLong(2));
                            return null;
                        })
                .list();
        int allies =
                (int) guildOfLord.values().stream().filter(guild -> guild == farmerGuild).count();
        int joinedWarlord =
                (int) guildOfLord.values().stream().filter(guild -> guild == warlordGuild).count();
        warlordJoins = Math.max(warlordJoins, joinedWarlord);
        if (allies < farmerAllies && farmerAlliesLost == null) {
            farmerAlliesLost = now;
        }
        farmerAllies = allies;
        farmerAlliesMax = Math.max(farmerAlliesMax, allies);
        farmerShareMax = Math.max(farmerShareMax, farmerShare());
        // the farmer's support: +10 to each ally every game day (a day ÷ speed)
        if (!now.isBefore(nextSupport)) {
            nextSupport = now.plus(Duration.ofHours(6));
            guildOfLord.forEach(
                    (lord, guild) -> {
                        if (guild == farmerGuild) {
                            tx.executeWithoutResult(
                                    status ->
                                            trust.change(
                                                    lord,
                                                    farmer,
                                                    SUPPORT,
                                                    TrustReason.SUPPORT,
                                                    "-",
                                                    now));
                        }
                    });
        }
    }

    private long guildOf(long playerId) {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .optional()
                .orElse(-1L);
    }

    /** The farmer's guild's share of all villages, in percent. */
    private double farmerShare() {
        return jdbc.sql(
                        """
                        select 100.0 * count(*) filter (where m.guild_id = :guild)
                               / greatest(count(*), 1)
                        from village v left join guild_member m on m.player_id = v.player_id
                        """)
                .param("guild", farmerGuild)
                .query(Double.class)
                .single();
    }

    @Override
    String scenarioLines() {
        String lost =
                farmerAlliesLost == null
                        ? "never"
                        : String.format(
                                Locale.ROOT,
                                "day %.1f",
                                Duration.between(start, farmerAlliesLost).toMinutes() / 1440.0);
        String winner =
                jdbc.sql(
                                "select coalesce(winner_kind || ' ' || winner_name, 'nobody')"
                                        + " from world_result")
                        .query(String.class)
                        .optional()
                        .orElse("the world did not end");
        return String.format(
                Locale.ROOT,
                """

                ALLIES (2.1 S2b)
                  supported guild: allies at most %d, at the end %d, first one lost: %s;
                  its share of the villages at most %.1f %% (60 %% wins)
                  warlord: at most %d allies; %d attacks on his own allies, %d of them kept the ally
                  winner: %s
                """,
                farmerAlliesMax,
                farmerAllies,
                lost,
                farmerShareMax,
                warlordJoins,
                attacksOnAllies,
                alliancesKeptAfterAttack,
                winner);
    }

    @Override
    void scenarioAssert() {
        assertThat(farmerAlliesMax).as("lords that joined the supported guild").isEqualTo(2);
        assertThat(warlordJoins).as("lords that joined the warlord").isPositive();
        assertThat(alliancesKeptAfterAttack).as("allies that stayed after an attack").isZero();
        assertThat(farmerShareMax)
                .as("the supported guild's best share of the land")
                .isLessThan(60);
        assertThat(
                        jdbc.sql(
                                        "select count(*) from world_result where winner_kind ="
                                                + " 'GUILD' and winner_ref_id = ?")
                                .param(farmerGuild)
                                .query(Integer.class)
                                .single())
                .as("the supported guild won the world")
                .isZero();
    }

    @Test
    void withAlliesInASoloWorld() throws Exception {
        run("solo world, allies", 0);
    }
}
