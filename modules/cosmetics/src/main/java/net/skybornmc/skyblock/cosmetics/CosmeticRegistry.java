package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every title and tag, from {@code tags-titles.yml} in the data folder. Staff edit it in-game (the editor saves back
 * to the file); the file can also be edited by hand and reloaded.
 */
public final class CosmeticRegistry {

    static final String FILE = "tags-titles.yml";

    private final JavaPlugin plugin;
    private volatile Map<String, Cosmetic> titles = Map.of();
    private volatile Map<String, Cosmetic> tags = Map.of();

    CosmeticRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads the file (copied from the jar on first start). A broken file keeps the previous registry. */
    void load(Consumer<String> problem) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            problem.accept(FILE + ": not valid YAML, the previous titles and tags stay active. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return;
        }
        titles = parse(yaml.getConfigurationSection("titles"), Cosmetics.Kind.TITLE, problem);
        tags = parse(yaml.getConfigurationSection("tags"), Cosmetics.Kind.TAG, problem);
    }

    static Map<String, Cosmetic> parse(ConfigurationSection section, Cosmetics.Kind kind, Consumer<String> problem) {
        Map<String, Cosmetic> map = new LinkedHashMap<>();
        if (section == null) {
            return Collections.unmodifiableMap(map);
        }
        String where = kind == Cosmetics.Kind.TITLE ? "titles" : "tags";
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null || !Cosmetic.validId(id)) {
                problem.accept(FILE + " → " + where + "." + id + ": invalid entry (ids use a-z, 0-9, _), skipped");
                continue;
            }
            Rarity rarity;
            try {
                rarity = Rarity.valueOf(c.getString("rarity", "common").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problem.accept(FILE + " → " + where + "." + id + ".rarity: unknown '" + c.getString("rarity") + "', using common");
                rarity = Rarity.COMMON;
            }
            map.put(id, new Cosmetic(kind, id, c.getString("display", id), rarity, c.getString("source", ""),
                    c.getBoolean("secret", false), c.getBoolean("season-persistent", true)));
        }
        return Collections.unmodifiableMap(map);
    }

    public List<Cosmetic> all(Cosmetics.Kind kind) {
        return new ArrayList<>(map(kind).values());
    }

    public Optional<Cosmetic> get(Cosmetics.Kind kind, String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(map(kind).get(id.toLowerCase(Locale.ROOT)));
    }

    public java.util.Set<String> ids(Cosmetics.Kind kind) {
        return map(kind).keySet();
    }

    private Map<String, Cosmetic> map(Cosmetics.Kind kind) {
        return kind == Cosmetics.Kind.TITLE ? titles : tags;
    }

    /** Adds or replaces one entry and writes the file (in-game editor). */
    synchronized void put(Cosmetic cosmetic) throws IOException {
        Map<String, Cosmetic> copy = new LinkedHashMap<>(map(cosmetic.kind()));
        copy.put(cosmetic.id(), cosmetic);
        replace(cosmetic.kind(), copy);
        save();
    }

    /** Removes one entry and writes the file. Players keep it in the database but it no longer shows. */
    synchronized boolean remove(Cosmetics.Kind kind, String id) throws IOException {
        Map<String, Cosmetic> copy = new LinkedHashMap<>(map(kind));
        boolean removed = copy.remove(id) != null;
        if (removed) {
            replace(kind, copy);
            save();
        }
        return removed;
    }

    private void replace(Cosmetics.Kind kind, Map<String, Cosmetic> map) {
        if (kind == Cosmetics.Kind.TITLE) {
            titles = Collections.unmodifiableMap(map);
        } else {
            tags = Collections.unmodifiableMap(map);
        }
    }

    private void save() throws IOException {
        File file = new File(plugin.getDataFolder(), FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file); // keep the header comments
        } catch (Exception ignored) {
            // rewrite from scratch
        }
        yaml.set("titles", null);
        yaml.set("tags", null);
        write(yaml, "titles", titles);
        write(yaml, "tags", tags);
        yaml.save(file);
    }

    private static void write(YamlConfiguration yaml, String root, Map<String, Cosmetic> map) {
        yaml.createSection(root);
        for (Cosmetic c : map.values()) {
            String base = root + "." + c.id();
            yaml.set(base + ".display", c.display());
            yaml.set(base + ".rarity", c.rarity().name().toLowerCase(Locale.ROOT));
            yaml.set(base + ".source", c.source());
            if (c.kind() == Cosmetics.Kind.TITLE) {
                yaml.set(base + ".secret", c.secret());
            }
            yaml.set(base + ".season-persistent", c.seasonPersistent());
        }
    }
}
