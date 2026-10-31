package net.skybornmc.skyblock.common.icon;

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
 */
public final class IconCatalog {

    /** Classpath location of the bundled catalogue (copied from {@code resourcepack/icons.json} by Gradle). */
    public static final String RESOURCE = "icons/icons.json";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)?");
    public static final Set<String> MEANINGS = Set.of("money", "reward", "move", "depths", "skills", "people", "home",
            "danger", "neutral", "locked", "currency-money", "currency-crystals", "currency-shards", "currency-credits");

    private final Map<String, IconDef> icons;
    private final List<String> problems;

    private IconCatalog(Map<String, IconDef> icons, List<String> problems) {
        this.icons = Collections.unmodifiableMap(icons);
        this.problems = List.copyOf(problems);
    }

    /** An empty catalogue (every key unknown). */
    public static IconCatalog empty() {
        return new IconCatalog(new LinkedHashMap<>(), List.of());
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
            return new IconCatalog(out, found);
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
        return new IconCatalog(out, found);
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

    public Optional<IconDef> get(String key) {
        return Optional.ofNullable(key == null ? null : icons.get(key));
    }

    public boolean has(String key) {
        return key != null && icons.containsKey(key);
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
