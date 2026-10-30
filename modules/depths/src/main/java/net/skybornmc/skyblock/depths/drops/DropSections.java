package net.skybornmc.skyblock.depths.drops;

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
 * Reads the {@code drop-items} and {@code drops} sections of {@code depths.yml}; a missing section is copied from
 * {@code depths-drops-defaults.yml} in the jar first (the owner edits the copy). A broken file keeps the defaults in
 * memory and never overwrites the owner's file.
 */
public final class DropSections {

    public static final String ITEMS = "drop-items";
    public static final String DROPS = "drops";
    static final List<String> SECTIONS = List.of(ITEMS, DROPS);
    static final String DEFAULTS = "depths-drops-defaults.yml";

    private DropSections() {
    }

    public static YamlConfiguration load(File file, Consumer<String> problem) {
        YamlConfiguration defaults = defaults(problem);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            problem.accept(file.getName() + ": not valid YAML, using the default drop tables. "
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
                problem.accept(file.getName() + ": could not add the default drop tables: " + e.getMessage());
            }
        }
        return yaml;
    }

    static YamlConfiguration defaults(Consumer<String> problem) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = DropSections.class.getClassLoader().getResourceAsStream(DEFAULTS)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                problem.accept(DEFAULTS + " is missing from the jar");
            }
        } catch (IOException | InvalidConfigurationException e) {
            problem.accept(DEFAULTS + " in the jar is broken: " + e.getMessage());
        }
        return yaml;
    }
}
