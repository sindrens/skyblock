package net.skybornmc.skyblock.common.icon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The icon catalogue (I7 pack v2, GUI-P1-PACK D2). Pure: parses the JSON text of {@code icons/icons.json} (JSON is
 * valid YAML, so {@link YamlConfiguration} reads it and no JSON library is needed). Invalid entries are reported and
 * skipped; the rest stays usable.
 *
 * <p>Two kinds of key (icon set v3): a <b>custom</b> icon ({@code icons} list: own picture in the pack, base item as the
 * fallback) and a <b>plain vanilla</b> key ({@code vanilla} map: key to item id, no picture, no model; the menu keeps its
 * stable key but shows the real Minecraft item for everyone). {@link #has} is true for both.
 */
public final class IconCatalog {

    /** Classpath location of the bundled catalogue (copied from {@code resourcepack/icons.json} by Gradle). */
    public static final String RESOURCE = "icons/icons.json";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)?");
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_]+");
    public static final Set<String> MEANINGS = Set.of("money", "reward", "move", "depths", "skills", "people", "home",
            "danger", "neutral", "locked", "currency-money", "currency-crystals", "currency-shards", "currency-credits");

    private final Map<String, IconDef> icons;
    private final Map<String, String> vanilla;
    private final List<String> problems;

    private IconCatalog(Map<String, IconDef> icons, Map<String, String> vanilla, List<String> problems) {
        this.icons = Collections.unmodifiableMap(icons);
        this.vanilla = Collections.unmodifiableMap(vanilla);
        this.problems = List.copyOf(problems);
    }

    /** An empty catalogue (every key unknown). */
    public static IconCatalog empty() {
        return new IconCatalog(new LinkedHashMap<>(), new LinkedHashMap<>(), List.of());
    }

    /** Parses the catalogue; problems are collected ({@link #problems()}). */
    public static IconCatalog load(String jsonText) {
        return load(jsonText, p -> { });
    }

    /** Parses the catalogue and reports each problem (bad key, duplicate, wrong field type) to {@code problem}. */
    public static IconCatalog load(String jsonText, Consumer<String> problem) {
        List<String> found = new ArrayList<>();
        Consumer<String> report = p -> {
            found.add(p);
            problem.accept(p);
        };
        Map<String, IconDef> out = new LinkedHashMap<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(jsonText == null ? "" : jsonText);
        } catch (InvalidConfigurationException e) {
            report.accept("icons.json is not valid JSON: " + e.getMessage());
            return new IconCatalog(out, new LinkedHashMap<>(), found);
        }
        List<Map<?, ?>> list = yaml.getMapList("icons");
        Set<String> files = new HashSet<>();
        int index = 0;
        for (Map<?, ?> raw : list) {
            String where = "icons[" + index++ + "]";
            String key = string(raw.get("key"));
            String base = string(raw.get("base"));
            String name = string(raw.get("name"));
            String meaning = string(raw.get("meaning"));
            String file = string(raw.get("file"));
            Object slice = raw.get("slice");
            if (key == null || !KEY.matcher(key).matches()) {
                report.accept(where + ": key '" + raw.get("key") + "' must look like group/name (a-z, 0-9, _)");
                continue;
            }
            where += " " + key;
            if (out.containsKey(key)) {
                report.accept(where + ": duplicate key, skipped");
                continue;
            }
            if (base == null || base.isBlank() || name == null || meaning == null || file == null || !(slice instanceof Number)) {
                report.accept(where + ": base, name, meaning, file (strings) and slice (number) are required");
                continue;
            }
            if (!MEANINGS.contains(meaning)) {
                report.accept(where + ": unknown meaning '" + meaning + "'");
                continue;
            }
            if (!file.equals(key.replace('/', '_')) || !files.add(file)) {
                report.accept(where + ": file must be '" + key.replace('/', '_') + "' and unique");
                continue;
            }
            out.put(key, new IconDef(key, base, name, meaning, file, ((Number) slice).intValue()));
        }
        Map<String, String> plain = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("vanilla");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String item = section.getString(key);
                if (!KEY.matcher(key).matches() || item == null || !ITEM.matcher(item).matches()) {
                    report.accept("vanilla." + key + ": must be a group/name key and an item id (a-z, 0-9, _)");
                } else if (out.containsKey(key)) {
                    report.accept("vanilla." + key + ": also a custom icon, skipped");
                } else {
                    plain.put(key, item);
                }
            }
        }
        return new IconCatalog(out, plain, found);
    }

    /** The catalogue bundled in the plugin jar; an empty catalogue (reported) when it is missing. */
    public static IconCatalog bundled(ClassLoader loader, Consumer<String> problem) {
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                problem.accept(RESOURCE + " is missing from the jar; every icon shows its plain base item");
                return empty();
            }
            return load(new String(in.readAllBytes(), StandardCharsets.UTF_8), problem);
        } catch (IOException e) {
            problem.accept("could not read " + RESOURCE + ": " + e.getMessage());
            return empty();
        }
    }

    private static String string(Object value) {
        return value instanceof String s ? s : null;
    }

    /** The custom icon of {@code key}; empty for a plain vanilla key and an unknown key. */
    public Optional<IconDef> get(String key) {
        return Optional.ofNullable(key == null ? null : icons.get(key));
    }

    /** The vanilla item id of a plain vanilla key (no custom picture); empty for a custom or unknown key. */
    public Optional<String> vanilla(String key) {
        return Optional.ofNullable(key == null ? null : vanilla.get(key));
    }

    /** True for a custom icon and for a plain vanilla key. */
    public boolean has(String key) {
        return key != null && (icons.containsKey(key) || vanilla.containsKey(key));
    }

    /** True only for a key with its own picture in the pack. */
    public boolean isCustom(String key) {
        return key != null && icons.containsKey(key);
    }

    /** The plain vanilla keys (key to item id). */
    public Map<String, String> vanillaKeys() {
        return vanilla;
    }

    public Collection<IconDef> all() {
        return icons.values();
    }

    public List<String> problems() {
        return problems;
    }

    /** The {@code custom_model_data} string of an icon: its key. */
    public static String cmdString(IconDef icon) {
        return icon.key();
    }
}
