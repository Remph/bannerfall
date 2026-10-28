package de.bannerfall.tutorial;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads a player's {@link TutorialFacts} with plain SQL. */
@Component
class TutorialFactsReader {

    private final JdbcClient jdbc;

    TutorialFactsReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    TutorialFacts of(long playerId) {
        return jdbc.sql(
                        """
                        select
                          coalesce(max(b_woodcutter), 0)    as woodcutter,
                          coalesce(max(b_headquarters), 0)  as headquarters,
                          coalesce(max(b_barracks), 0)      as barracks,
                          coalesce(max(b_market), 0)        as market,
                          coalesce(max(b_warehouse), 0)     as warehouse,
                          coalesce(max(b_smithy), 0)        as smithy,
                          coalesce(max(b_academy), 0)       as academy,
                          coalesce(max(b_banner_hall), 0)   as banner_hall,
                          count(*)                          as villages,
                          (select coalesce(sum(s.u_spearman), 0)
                             from unit_stack s join village v on v.id = s.home_village_id
                            where v.player_id = :p) as spearmen,
                          (exists (select 1 from movement
                                    where owner_player_id = :p and kind = 'ATTACK')
                           or exists (select 1 from report
                                       where player_id = :p and kind = 'ATTACK'
                                         and shared_by_id is null)) as attack_sent,
                          exists (select 1 from report
                                   where player_id = :p and kind = 'ATTACK' and read
                                     and shared_by_id is null) as attack_read,
                          (select count(*) from farm_target f
                             join village v on v.id = f.village_id
                            where v.player_id = :p) as farm_entries,
                          exists (select 1 from guild_member where player_id = :p) as in_guild
                        from village where player_id = :p
                        """)
                .param("p", playerId)
                .query(
                        (rs, row) ->
                                new TutorialFacts(
                                        rs.getInt("woodcutter"),
                                        rs.getInt("headquarters"),
                                        rs.getInt("barracks"),
                                        rs.getInt("market"),
                                        rs.getInt("warehouse"),
                                        rs.getInt("smithy"),
                                        rs.getInt("academy"),
                                        rs.getInt("banner_hall"),
                                        rs.getInt("villages"),
                                        rs.getInt("spearmen"),
                                        rs.getBoolean("attack_sent"),
                                        rs.getBoolean("attack_read"),
                                        rs.getInt("farm_entries"),
                                        rs.getBoolean("in_guild")))
                .single();
    }
}
