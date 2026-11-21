package de.bannerfall.victory;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The guard of the sleeping world (2.1, 12-solo-and-allies §2.4): every timestamp column of the
 * schema is either a game clock that {@link FrozenTime} moves on a wake, a reopen and a
 * fast-forward step, or listed below as real time with the reason. A new column fails this test
 * until somebody decides which it is.
 */
@IntegrationTest
class FrozenTimeColumnsTest {

    /** Moved by {@code WorldService.shiftClocks} (the settings row, through JPA). */
    private static final Set<String> SETTINGS_ROW =
            Set.of(
                    "world_settings.initialized_at",
                    "world_settings.ends_at",
                    "world_settings.victory_leader_since");

    /**
     * Real time: history (when something happened, as players and the host read it), real-time
     * limits and locks, and the sleep itself. Keep each line's reason.
     */
    private static final Set<String> REAL_TIME =
            Set.of(
                    // when something happened: shown as it happened, like reports and chat
                    "abuse_event.at",
                    "account.created_at",
                    "account.last_login_at",
                    "achievement_block.revoked_at",
                    "admin_action.at",
                    "banner.created_at",
                    "banner.equipped_at",
                    "banner_event.happened_at",
                    "build_order.created_at",
                    "chat_message.created_at",
                    // when a moment of the chronicle was written (I1): history; its game day is
                    // kept beside it
                    "chronicle_event.at",
                    "chronicle_frame.at",
                    "dismiss_order.created_at",
                    "farm_target.created_at",
                    // "sent 2 h ago" on the farm list: history (decided in S1, 12 §2.4)
                    "farm_target.last_raid_at",
                    "farm_target.last_sent_at",
                    "game_event.created_at",
                    "game_event.processed_at",
                    "guild.created_at",
                    "guild_enemy.created_at",
                    "guild_invitation.created_at",
                    "guild_member.joined_at",
                    "movement.created_at",
                    "npc_brain.last_think_at",
                    "npc_contact.since", // first contact with a lord (09 §16.7): history
                    "npc_help_request.closed_at", // when a help request ended (S4): history
                    "player.created_at",
                    "player_achievement.earned_at",
                    "player_block.created_at",
                    "private_message.created_at",
                    "private_message.read_at",
                    "push_subscription.created_at",
                    "recruit_order.created_at",
                    "report.created_at",
                    "report.happened_at",
                    "report_share_recipient.sent_at",
                    "research_order.created_at",
                    "return_watch.created_at",
                    "trade_offer.created_at",
                    "trade_transport.created_at",
                    "tutorial_progress.done_at",
                    "village.created_at",
                    "village.owner_since",
                    "village_note.updated_at",
                    "world_result.ended_at",
                    // real-time limits, locks and jobs (12 §3)
                    "account.banned_until",
                    "career_sync.sent_at",
                    "market_exchange.exchanged_at", // the trading limit per calendar day
                    "one_time_job.done_at",
                    "push_subscription.last_sent_at",
                    "statistics_since.since",
                    "storage_full_push.pushed_at",
                    // the world's own dates: the sleep itself, the pre-game, the end
                    "world_settings.asleep_since",
                    "world_settings.ended_at",
                    "world_settings.heartbeat_at", // the server's own real time (review 01)
                    "world_settings.opens_at",
                    "world_settings.started_at");

    @Autowired JdbcClient jdbc;

    @Test
    void everyTimestampColumnIsAGameClockOrRealTime() throws Exception {
        Set<String> columns =
                new TreeSet<>(
                        jdbc.sql(
                                        """
                                        select table_name || '.' || column_name
                                        from information_schema.columns
                                        where table_schema = 'public'
                                          and data_type like 'timestamp%'
                                          and table_name <> 'flyway_schema_history'
                                        """)
                                .query(String.class)
                                .list());
        Set<String> shifted = shiftedColumns();
        shifted.addAll(SETTINGS_ROW);

        Set<String> undecided = new TreeSet<>(columns);
        undecided.removeAll(shifted);
        undecided.removeAll(REAL_TIME);
        assertThat(undecided)
                .as(
                        "timestamp columns that are neither moved by FrozenTime (a game clock) nor"
                                + " listed as real time here; decide for each")
                .isEmpty();

        Set<String> both = new TreeSet<>(shifted);
        both.retainAll(REAL_TIME);
        assertThat(both).as("moved and listed as real time").isEmpty();

        Set<String> gone = new TreeSet<>(shifted);
        gone.addAll(REAL_TIME);
        gone.removeAll(columns);
        assertThat(gone).as("listed here or moved, but not in the schema").isEmpty();
    }

    /** "table.column" for every column a statement of {@code FrozenTime.SHIFTS} moves. */
    @SuppressWarnings("unchecked")
    private static Set<String> shiftedColumns() throws ReflectiveOperationException {
        Field field = FrozenTime.class.getDeclaredField("SHIFTS");
        field.setAccessible(true);
        Set<String> shifted = new TreeSet<>();
        Pattern table = Pattern.compile("update (\\w+)");
        Pattern column = Pattern.compile("(\\w+) =\\s+(least\\()?\\1 \\+");
        for (String sql : (List<String>) field.get(null)) {
            Matcher t = table.matcher(sql);
            assertThat(t.find()).as(sql).isTrue();
            Matcher c = column.matcher(sql);
            int found = 0;
            while (c.find()) {
                shifted.add(t.group(1) + "." + c.group(1));
                found++;
            }
            assertThat(found).as("no column found in " + sql).isPositive();
        }
        return shifted;
    }
}
