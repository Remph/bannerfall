package de.bannerfall;

import de.bannerfall.gamedata.GameData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads the real {@code gamedata/*.yml} without a Spring context, so pure rule tests use exactly
 * the numbers the game uses (same binder as the application).
 */
public final class TestGameData {

    private static final List<String> FILES =
            List.of("gamedata/buildings.yml", "gamedata/units.yml");

    private TestGameData() {}

    public static GameData load() {
        var loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = new ArrayList<>();
        try {
            for (String file : FILES) {
                sources.addAll(loader.load(file, new ClassPathResource(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("gamedata", GameData.class)
                .get();
    }
}
