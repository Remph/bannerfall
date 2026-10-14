package de.bannerfall.npc;

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
 * Loads the real {@code gamedata/npc/*.yml} without a Spring context, like {@code TestGameData}.
 */
public final class TestNpcData {

    private static final List<String> FILES =
            List.of(
                    "gamedata/npc/offensive.yml",
                    "gamedata/npc/balanced.yml",
                    "gamedata/npc/defensive.yml",
                    "gamedata/npc/balance.yml");

    private TestNpcData() {}

    public static NpcTemplates templates() {
        return binder().bind("npc", NpcTemplates.class).get();
    }

    public static NpcBalanceSettings balance() {
        return binder().bind("npc", NpcBalanceSettings.class).get();
    }

    private static Binder binder() {
        var loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = new ArrayList<>();
        try {
            for (String file : FILES) {
                sources.addAll(loader.load(file, new ClassPathResource(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}
