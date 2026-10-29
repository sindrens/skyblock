package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loads a free-form YAML file ({@code menu.yml}, {@code help.yml}) from the data folder: copied from the jar on first
 * start; on updates, keys that are new in the bundled copy are added (the owner's edits are never overwritten).
 * A broken file is reported and {@code null} is returned so the caller keeps its previous content.
 */
final class YamlFiles {

    private YamlFiles() {
    }

    static YamlConfiguration load(JavaPlugin plugin, String name, Consumer<String> problem) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            problem.accept(name + ": not valid YAML, the previous version stays active. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return null;
        }
        YamlConfiguration bundled = bundled(plugin, name);
        if (bundled != null) {
            boolean changed = false;
            for (String key : bundled.getKeys(true)) {
                if (!bundled.isConfigurationSection(key) && !yaml.contains(key)) {
                    yaml.set(key, bundled.get(key));
                    List<String> comments = bundled.getComments(key);
                    if (!comments.isEmpty()) {
                        yaml.setComments(key, comments);
                    }
                    changed = true;
                }
            }
            if (changed) {
                try {
                    yaml.save(file);
                } catch (Exception e) {
                    problem.accept(name + ": could not save new keys: " + e.getMessage());
                }
            }
        }
        return yaml;
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String name) {
        InputStream in = plugin.getResource(name);
        if (in == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (Exception e) {
            return null;
        }
    }
}
