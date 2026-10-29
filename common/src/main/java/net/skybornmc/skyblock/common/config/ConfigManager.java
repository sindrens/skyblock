package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Loads, validates, merges, saves and describes typed {@link ModuleConfig} files.
 *
 * <ul>
 *   <li><b>Merge:</b> missing keys are added with their default and description; existing values are never overwritten.</li>
 *   <li><b>Validation:</b> a wrong type or out-of-range value is reported with file, key, value and the allowed range;
 *       the previous value (or the default on first load) stays active.</li>
 *   <li><b>Migrations:</b> renamed keys are moved when the file's {@code config-version} is older.</li>
 * </ul>
 */
public final class ConfigManager {

    static final String VERSION_KEY = "config-version";

    private final File folder;
    private final Logger log;
    private final Map<ModuleConfig, Map<Field, Object>> defaults = new IdentityHashMap<>();
    private final Map<String, ModuleConfig> loaded = new LinkedHashMap<>();
    private final Map<String, List<String>> problems = new LinkedHashMap<>();

    public ConfigManager(File folder, Logger log) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Loads (or creates) the file for {@code config}. Returns the list of problems found (empty = all good). */
    public List<String> load(ModuleConfig config) {
        defaults.computeIfAbsent(config, this::snapshotDefaults);
        loaded.put(config.fileName(), config);
        File file = new File(folder, config.fileName());
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> problems = new ArrayList<>();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                // Broken YAML syntax: never overwrite the owner's file, keep every current value.
                String problem = config.fileName() + ": the file is not valid YAML, nothing was changed (all current values kept). "
                        + firstLine(e.getMessage());
                log.severe(problem);
                this.problems.put(config.fileName(), List.of(problem));
                return List.of(problem);
            }
        }
        boolean changed = migrate(config, yaml);

        for (Field field : settingFields(config)) {
            Setting setting = field.getAnnotation(Setting.class);
            String path = pathOf(field, setting);
            if (!yaml.contains(path)) {
                yaml.set(path, toYaml(read(field, config)));
                yaml.setComments(path, comments(setting));
                changed = true;
                continue;
            }
            Object raw = yaml.get(path);
            try {
                Object value = convert(field.getType(), raw, path);
                checkRange(setting, value, path);
                write(field, config, value);
            } catch (IllegalArgumentException e) {
                String problem = config.fileName() + " → " + path + ": " + e.getMessage()
                        + " (keeping " + toYaml(read(field, config)) + ")";
                problems.add(problem);
                log.severe(problem);
            }
        }
        if (changed) {
            yaml.set(VERSION_KEY, config.version());
            if (!config.header().isBlank()) {
                yaml.options().setHeader(List.of(config.header().split("\n")));
            }
            saveQuietly(yaml, file);
        }
        this.problems.put(config.fileName(), List.copyOf(problems));
        return problems;
    }

    /** Every config loaded so far, by file name. */
    public Map<String, ModuleConfig> loaded() {
        return Collections.unmodifiableMap(loaded);
    }

    /** Problems found during the latest load of each file (only files with problems). */
    public Map<String, List<String>> problems() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        problems.forEach((file, list) -> {
            if (!list.isEmpty()) {
                out.put(file, list);
            }
        });
        return out;
    }

    /** Writes the current values of {@code config} to its file (used by the in-game editor). */
    public void save(ModuleConfig config) throws IOException {
        File file = new File(folder, config.fileName());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Field field : settingFields(config)) {
            Setting setting = field.getAnnotation(Setting.class);
            String path = pathOf(field, setting);
            yaml.set(path, toYaml(read(field, config)));
            yaml.setComments(path, comments(setting));
        }
        yaml.set(VERSION_KEY, config.version());
        folder.mkdirs();
        yaml.save(file);
    }

    /**
     * Sets one value by path after validating it (for the in-game editor). Does not save; call {@link #save}.
     *
     * @throws IllegalArgumentException with a player-readable reason if the value is invalid
     */
    public void set(ModuleConfig config, String path, Object rawValue) {
        for (Field field : settingFields(config)) {
            Setting setting = field.getAnnotation(Setting.class);
            if (pathOf(field, setting).equals(path)) {
                Object value = convert(field.getType(), rawValue, path);
                checkRange(setting, value, path);
                write(field, config, value);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown setting '" + path + "'");
    }

    /** All settings of {@code config} with current values, defaults and limits. */
    public List<SettingInfo> describe(ModuleConfig config) {
        Map<Field, Object> defs = defaults.computeIfAbsent(config, this::snapshotDefaults);
        List<SettingInfo> out = new ArrayList<>();
        for (Field field : settingFields(config)) {
            Setting s = field.getAnnotation(Setting.class);
            out.add(new SettingInfo(pathOf(field, s), s.desc(), field.getType(), read(field, config), defs.get(field),
                    s.min(), s.max(), s.restart(), s.level()));
        }
        return Collections.unmodifiableList(out);
    }

    // ---------------------------------------------------------------- internals

    private boolean migrate(ModuleConfig config, YamlConfiguration yaml) {
        int fileVersion = yaml.getInt(VERSION_KEY, yaml.getKeys(false).isEmpty() ? config.version() : 1);
        boolean changed = false;
        for (int v = fileVersion + 1; v <= config.version(); v++) {
            for (Map.Entry<String, String> rename : config.migrations().getOrDefault(v, Map.of()).entrySet()) {
                if (yaml.contains(rename.getKey()) && !yaml.contains(rename.getValue())) {
                    yaml.set(rename.getValue(), yaml.get(rename.getKey()));
                    yaml.set(rename.getKey(), null);
                    log.info(config.fileName() + ": migrated '" + rename.getKey() + "' → '" + rename.getValue() + "'");
                    changed = true;
                }
            }
        }
        if (fileVersion < config.version()) {
            changed = true;
        }
        return changed;
    }

    private Map<Field, Object> snapshotDefaults(ModuleConfig config) {
        Map<Field, Object> map = new IdentityHashMap<>();
        for (Field field : settingFields(config)) {
            map.put(field, read(field, config));
        }
        return map;
    }

    static List<Field> settingFields(ModuleConfig config) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = config.getClass(); c != null && c != ModuleConfig.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Setting.class) && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
        }
        return fields;
    }

    static String pathOf(Field field, Setting setting) {
        if (!setting.path().isBlank()) {
            return setting.path();
        }
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(Class<?> type, Object raw, String path) {
        if (raw == null) {
            throw new IllegalArgumentException("missing value");
        }
        String text = String.valueOf(raw).trim();
        try {
            if (type == boolean.class || type == Boolean.class) {
                if (raw instanceof Boolean b) {
                    return b;
                }
                return switch (text.toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "on" -> true;
                    case "false", "no", "off" -> false;
                    default -> throw new IllegalArgumentException("'" + text + "' is not true/false");
                };
            }
            if (type == int.class || type == Integer.class) {
                if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
                    return Math.toIntExact(n.longValue());
                }
                return Integer.parseInt(text);
            }
            if (type == long.class || type == Long.class) {
                if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
                    return n.longValue();
                }
                return Long.parseLong(text);
            }
            if (type == double.class || type == Double.class) {
                return raw instanceof Number n ? n.doubleValue() : Double.parseDouble(text);
            }
            if (type == String.class) {
                return raw instanceof String s ? s : text;
            }
            if (List.class.isAssignableFrom(type)) {
                if (raw instanceof List<?> list) {
                    return list.stream().map(String::valueOf).toList();
                }
                throw new IllegalArgumentException("'" + text + "' is not a list");
            }
            if (type.isEnum()) {
                try {
                    return Enum.valueOf((Class<Enum>) type, text.toUpperCase(Locale.ROOT).replace('-', '_'));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("'" + text + "' is not one of " + java.util.Arrays.toString(type.getEnumConstants()));
                }
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + friendlyType(type));
        }
        throw new IllegalArgumentException("unsupported setting type " + type.getSimpleName());
    }

    static void checkRange(Setting setting, Object value, String path) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d < setting.min() || d > setting.max()) {
                throw new IllegalArgumentException(format(d) + " is outside the allowed range "
                        + format(setting.min()) + " to " + format(setting.max()));
            }
        }
    }

    static String friendlyType(Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "whole number";
        }
        if (type == double.class || type == Double.class) {
            return "number";
        }
        return type.getSimpleName();
    }

    /** Value as it is written in the YAML file (enums in lower kebab-case). */
    public static String display(Object value) {
        return String.valueOf(toYaml(value));
    }

    /** Description plus allowed range, written above each key. */
    static List<String> comments(Setting setting) {
        List<String> lines = new ArrayList<>();
        if (!setting.desc().isBlank()) {
            lines.add(setting.desc());
        }
        boolean hasMin = setting.min() != Double.NEGATIVE_INFINITY;
        boolean hasMax = setting.max() != Double.POSITIVE_INFINITY;
        if (hasMin || hasMax) {
            lines.add("Allowed: " + format(setting.min()) + " to " + format(setting.max()));
        }
        if (setting.restart()) {
            lines.add("Needs a restart to take effect.");
        }
        return lines;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    private static String format(double d) {
        if (Double.isInfinite(d)) {
            return d > 0 ? "∞" : "-∞";
        }
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static Object toYaml(Object value) {
        return value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT).replace('_', '-') : value;
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Field field, Object owner, Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void saveQuietly(YamlConfiguration yaml, File file) {
        try {
            folder.mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            log.severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
