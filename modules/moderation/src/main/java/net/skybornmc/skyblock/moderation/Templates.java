package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.command.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Punishment templates from {@code punishments.yml}: categories → reasons → escalation ladder.
 * Every entry is validated; a broken entry is skipped with a precise error and a broken file keeps the previous
 * templates (same rules as {@link net.skybornmc.skyblock.common.config.ConfigManager}).
 */
final class Templates {

    /** One ladder step: what happens on the n-th offence. {@code duration == null} = permanent (or n/a for warn/kick). */
    record Step(Punishment.Type type, Duration duration, boolean ipWide) {

        static Step parse(String raw) {
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String kind = parts[0];
            String time = parts.length > 1 ? parts[1] : null;
            return switch (kind) {
                case "warn" -> new Step(Punishment.Type.WARN, null, false);
                case "kick" -> new Step(Punishment.Type.KICK, null, false);
                case "mute" -> new Step(Punishment.Type.MUTE, duration(time, raw), false);
                case "ban" -> new Step(Punishment.Type.BAN, duration(time, raw), false);
                case "ipban" -> new Step(Punishment.Type.BAN, duration(time, raw), true);
                default -> throw new IllegalArgumentException("'" + raw + "': start with warn, kick, mute, ban or ipban");
            };
        }

        private static Duration duration(String time, String raw) {
            if (time == null || time.equals("perm") || time.equals("permanent")) {
                return null;
            }
            try {
                return Durations.parse(time);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + raw + "': '" + time + "' is not a time (use 30m, 1h, 7d or perm)");
            }
        }

        String describe() {
            String base = type.name().toLowerCase(Locale.ROOT);
            if (ipWide) {
                base = "ip" + base;
            }
            if (type == Punishment.Type.WARN || type == Punishment.Type.KICK) {
                return base;
            }
            return base + " " + (duration == null ? "perm" : net.skybornmc.skyblock.common.util.Format.duration(duration));
        }
    }

    record Template(String id, String category, String name, Material icon, List<Step> ladder) {
        /** The step for a player who already has {@code previous} punishments with this template. */
        Step stepFor(int previous) {
            return ladder.get(Math.min(previous, ladder.size() - 1));
        }
    }

    record Category(String id, String name, Material icon, List<Template> templates) {
    }

    private volatile Map<String, Category> categories = Map.of();

    List<String> load(File file) {
        List<String> problems = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            problems.add("punishments.yml: not valid YAML, the previous templates stay active. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return problems;
        }
        Map<String, Category> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("categories");
        if (root == null) {
            problems.add("punishments.yml: missing 'categories' (no templates loaded)");
            categories = Map.of();
            return problems;
        }
        for (String catId : root.getKeys(false)) {
            ConfigurationSection cat = root.getConfigurationSection(catId);
            if (cat == null) {
                continue;
            }
            List<Template> templates = new ArrayList<>();
            ConfigurationSection reasons = cat.getConfigurationSection("reasons");
            if (reasons != null) {
                for (String id : reasons.getKeys(false)) {
                    ConfigurationSection t = reasons.getConfigurationSection(id);
                    if (t == null) {
                        continue;
                    }
                    try {
                        List<Step> ladder = new ArrayList<>();
                        for (String raw : t.getStringList("ladder")) {
                            ladder.add(Step.parse(raw));
                        }
                        if (ladder.isEmpty()) {
                            throw new IllegalArgumentException("'ladder' needs at least one step");
                        }
                        templates.add(new Template(id, catId, t.getString("name", id), material(t.getString("icon"), Material.PAPER),
                                List.copyOf(ladder)));
                    } catch (IllegalArgumentException e) {
                        problems.add("punishments.yml → categories." + catId + ".reasons." + id + ": " + e.getMessage() + " (skipped)");
                    }
                }
            }
            out.put(catId, new Category(catId, cat.getString("name", catId), material(cat.getString("icon"), Material.BOOK),
                    List.copyOf(templates)));
        }
        categories = Collections.unmodifiableMap(out);
        return problems;
    }

    private static Material material(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || !m.isItem() ? fallback : m;
    }

    List<Category> categories() {
        return List.copyOf(categories.values());
    }

    Optional<Template> template(String id) {
        for (Category c : categories.values()) {
            for (Template t : c.templates()) {
                if (t.id().equalsIgnoreCase(id)) {
                    return Optional.of(t);
                }
            }
        }
        return Optional.empty();
    }

    List<String> templateIds() {
        List<String> ids = new ArrayList<>();
        categories.values().forEach(c -> c.templates().forEach(t -> ids.add(t.id())));
        return ids;
    }
}
