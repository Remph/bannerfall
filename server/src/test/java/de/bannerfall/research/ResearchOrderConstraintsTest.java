package de.bannerfall.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The finished rows of {@code research_order} are a player's unit levels, so they follow the player
 * and no village (V67): a village delete must not take them along silently.
 */
@IntegrationTest
class ResearchOrderConstraintsTest {

    @Autowired JdbcClient jdbc;

    @Test
    void theLevelsFollowTheirPlayerAndAreNotDeletedWithTheVillage() {
        Map<String, String> deleteRules =
                jdbc
                        .sql(
                                """
                                select kcu.column_name, rc.delete_rule
                                from information_schema.referential_constraints rc
                                join information_schema.key_column_usage kcu
                                  on kcu.constraint_name = rc.constraint_name
                                 and kcu.constraint_schema = rc.constraint_schema
                                where kcu.table_name = 'research_order'
                                """)
                        .query(
                                (rs, row) ->
                                        Map.entry(
                                                rs.getString("column_name"),
                                                rs.getString("delete_rule")))
                        .list()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(deleteRules)
                .containsEntry("player_id", "CASCADE")
                .containsEntry("village_id", "NO ACTION");
    }
}
