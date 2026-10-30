package net.skybornmc.skyblock.depths.combat;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loads {@code mobs.yml} (M4.8): copied from the jar on first start; a missing top-level section ({@code drop-items},
 * {@code mobs}, {@code combat-zones}, {@code ambient}) is added from the jar's copy. A broken file keeps the jar defaults
 * in memory and is never overwritten.
 */
public final class MobSections {

    public static final String FILE = "mobs.yml";
    public static final String ITEMS = "drop-items";
    static final List<String> SECTIONS = List.of(ITEMS, MobCatalog.MOBS, MobCatalog.COMBAT, MobCatalog.AMBIENT);

    private MobSections() {
    }

    public static YamlConfiguration load(File file, Consumer<String> problem) {
        YamlConfiguration defaults = defaults(problem);
        if (!file.exists()) {
            copyRaw(file, problem);
            if (!file.exists()) {
                return defaults;
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            problem.accept(FILE + ": not valid YAML, using the default mobs. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return defaults;
        }
        boolean changed = false;
        for (String section : SECTIONS) {
            if (!yaml.contains(section) && defaults.contains(section)) {
                yaml.set(section, defaults.get(section));
                yaml.setComments(section, defaults.getComments(section));
                changed = true;
            }
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                problem.accept(FILE + ": could not add the default sections: " + e.getMessage());
            }
        }
        return yaml;
    }

    /** Writes the jar's file as it is (keeps every comment) on first start. */
    private static void copyRaw(File file, Consumer<String> problem) {
        try (InputStream in = MobSections.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in != null) {
                java.nio.file.Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            problem.accept(FILE + ": could not copy the commented defaults: " + e.getMessage());
        }
    }

    static YamlConfiguration defaults(Consumer<String> problem) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = MobSections.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                problem.accept(FILE + " is missing from the jar");
            }
        } catch (IOException | InvalidConfigurationException e) {
            problem.accept(FILE + " in the jar is broken: " + e.getMessage());
        }
        return yaml;
    }
}
