package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Each setting has its default twice: the column default of the migrations (what a new world, and
 * an existing world on an upgrade, gets) and {@link WorldSettings#defaults()}. This test migrates a
 * fresh schema and compares its row with the Java defaults, setting by setting. The test database's
 * own row can't be used: tests change it.
 */
@IntegrationTest
class WorldSettingsDefaultsTest {

    private static final String SCHEMA = "fresh_world_defaults";

    @Autowired DataSource dataSource;
    @Autowired JdbcClient jdbc;

    @Test
    void aFreshlyMigratedWorldHasTheJavaDefaults() throws Exception {
        jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(SCHEMA)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            Map<String, Object> row =
                    jdbc.sql("select * from " + SCHEMA + ".world_settings").query().singleRow();

            WorldSettings defaults = WorldSettings.defaults();
            List<String> differences = new ArrayList<>();
            for (RecordComponent component : WorldSettings.class.getRecordComponents()) {
                String column = snakeCase(component.getName());
                if (!row.containsKey(column)) {
                    differences.add(column + ": no such column");
                    continue;
                }
                Object expected = component.getAccessor().invoke(defaults);
                Object actual = row.get(column);
                if (!same(expected, actual)) {
                    differences.add(column + ": SQL " + actual + ", Java " + expected);
                }
            }
            assertThat(differences).isEmpty();
        } finally {
            jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
        }
    }

    private static boolean same(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number number && actual instanceof Number column) {
            return number.doubleValue() == column.doubleValue();
        }
        if (expected instanceof Enum<?> value) {
            return value.name().equals(actual.toString());
        }
        return expected.equals(actual);
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
