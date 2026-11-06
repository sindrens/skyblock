package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Money x100 for catalog files (sell.yml, shop.yml, upgrades.yml ...) that have their own loader ({@link MoneyScale}).
 * The bundled file carries {@code money-scale: 100}, so a fresh copy is never scaled; a live file without the marker is
 * scaled once, owner values too, with a backup, then marked. Call it right after reading the file and before adding
 * new bundled entries (those are x100 already). Pure apart from {@link #applyToFile}.
 */
public final class CatalogScale {

    private CatalogScale() {
    }

    /**
     * One place that holds money: a dotted pattern ({@code *} = any key at that level) and the rule. A matched section
     * with a {@code price} key has that price scaled (the {@code {price: 82, sellable: false}} form).
     *
     * @param sibling  when set, only nodes whose parent section has {@code sibling} in {@code values} (e.g. type: earn)
     */
    public record Path(String pattern, MoneyScale.Rule rule, String sibling, Set<String> values) {

        public static Path of(String pattern, MoneyScale.Rule rule) {
            return new Path(pattern, rule, null, Set.of());
        }

        public static Path when(String pattern, String sibling, Set<String> values, MoneyScale.Rule rule) {
            return new Path(pattern, rule, sibling, values);
        }
    }

    /** A list of maps (YAML {@code - {tier: 1, cost: 3350}}): {@code rule} on {@code key} of every map. */
    public static MoneyScale.Rule mapList(String key, MoneyScale.Rule rule) {
        return (raw, f) -> {
            if (!(raw instanceof List<?> list)) {
                return raw;
            }
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.containsKey(key)) {
                    Map<Object, Object> copy = new LinkedHashMap<>(m);
                    copy.put(key, rule.apply(m.get(key), f));
                    out.add(copy);
                } else {
                    out.add(o);
                }
            }
            return out;
        };
    }

    /** Reward lines {@code "money 750"}: only those lines, field 1. */
    public static MoneyScale.Rule moneyLines() {
        return MoneyScale.onlyLines(MoneyScale.startsWith("money "), MoneyScale.field(1, " "));
    }

    /** True when the file still needs the scale (no marker, or a lower one). */
    public static boolean needed(ConfigurationSection yaml) {
        return MoneyScale.fileScale(yaml) < MoneyScale.FACTOR;
    }

    /** Scales {@code yaml} in place and sets the marker; returns one log line per changed value (empty when not needed). */
    public static List<String> apply(ConfigurationSection yaml, List<Path> paths) {
        List<String> log = new ArrayList<>();
        if (!needed(yaml)) {
            return log;
        }
        long factor = MoneyScale.FACTOR / MoneyScale.fileScale(yaml);
        for (Path p : paths) {
            walk(yaml, "", p.pattern().split("\\."), 0, p, factor, log);
        }
        yaml.set(MoneyScale.KEY, MoneyScale.FACTOR);
        return log;
    }

    private static void walk(ConfigurationSection node, String prefix, String[] parts, int i, Path p, long factor, List<String> log) {
        String part = parts[i];
        List<String> keys = part.equals("*") ? new ArrayList<>(node.getKeys(false)) : List.of(part);
        for (String key : keys) {
            if (!node.contains(key)) {
                continue;
            }
            String here = prefix.isEmpty() ? key : prefix + "." + key;
            if (i < parts.length - 1) {
                ConfigurationSection child = node.getConfigurationSection(key);
                if (child != null) {
                    walk(child, here, parts, i + 1, p, factor, log);
                }
                continue;
            }
            if (p.sibling() != null && !p.values().contains(String.valueOf(node.get(p.sibling())).strip().toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            String target = key;
            if (node.isConfigurationSection(key)) {
                ConfigurationSection s = node.getConfigurationSection(key);
                if (s == null || !s.contains("price")) {
                    continue;
                }
                node = s;
                target = "price";
                here = here + ".price";
            }
            try {
                String line = MoneyScale.apply(node, target, p.rule(), factor);
                if (line != null) {
                    log.add(here + line.substring(target.length()));
                }
            } catch (ArithmeticException overflow) {
                log.add(here + ": too large to scale, left as it was");
            }
            if (target.equals("price")) {
                node = node.getParent();
            }
        }
    }

    /**
     * File version: scales a live catalog once, writes a backup ({@code <file>.pre-money-x100.bak}) first, saves it and
     * logs every change. Returns true when the file was scaled.
     */
    public static boolean applyToFile(File file, YamlConfiguration yaml, List<Path> paths, Logger log) {
        if (!file.exists() || !needed(yaml)) {
            return false;
        }
        File backup = new File(file.getParentFile(), file.getName() + ".pre-money-x100.bak");
        try {
            if (!backup.exists()) {
                java.nio.file.Files.copy(file.toPath(), backup.toPath());
            }
        } catch (IOException e) {
            log.warning(file.getName() + ": could not write the backup before money x100 (" + e.getMessage() + ")");
        }
        List<String> lines = apply(yaml, paths);
        for (String line : lines) {
            log.info(file.getName() + ": money x100 " + line);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning(file.getName() + ": could not save after money x100 (" + e.getMessage() + ")");
        }
        log.info(file.getName() + ": money x100 done (" + lines.size() + " value(s))");
        return true;
    }
}
