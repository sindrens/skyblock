package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Adds crates that are new in the bundled {@code crates.yml} to the server's file (pure, tested). */
final class CatalogMerge {

    private CatalogMerge() {
    }

    /**
     * Copies every bundled crate the file does not have yet. Existing crates are never touched: a reward the owner deleted
     * must not come back, and their odds stay as edited. True if something was added.
     */
    static boolean addMissing(YamlConfiguration yaml, YamlConfiguration bundled) {
        ConfigurationSection root = bundled.getConfigurationSection(CrateCatalog.ROOT);
        if (root == null) {
            return false;
        }
        boolean changed = false;
        for (String id : root.getKeys(false)) {
            String path = CrateCatalog.ROOT + "." + id;
            if (!yaml.contains(path)) {
                ConfigurationSection from = root.getConfigurationSection(id);
                if (from != null) {
                    copy(from, yaml.createSection(path));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            ConfigurationSection inner = from.getConfigurationSection(key);
            if (inner != null) {
                copy(inner, to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
        }
    }
}
