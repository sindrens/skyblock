package net.skybornmc.skyblock.common.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code commands.yml}: per top-level command, its aliases and an optional cooldown override.
 * Missing commands are added with their defaults; the owner's values are never overwritten.
 * Invalid entries are reported and the code default is used. Aliases apply after a restart.
 */
final class CommandsFile {

    record Overrides(List<String> aliases, Duration cooldown) {
    }

    private static final String HEADER = """
            Command settings. One section per command:
              aliases: other names for the command (applied after a restart)
              cooldown-seconds: per-player cooldown; -1 = the built-in default
            New commands are added here automatically on updates; your values are never overwritten.""";

    private final File file;
    private final Map<String, Overrides> overrides = new ConcurrentHashMap<>();
    private final List<String> problems = new ArrayList<>();

    CommandsFile(File file) {
        this.file = file;
    }

    /** Loads the file, adding sections for {@code specs} that are missing. Returns problems. */
    synchronized List<String> load(Collection<CommandSpec> specs) {
        problems.clear();
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                problems.add("commands.yml: not valid YAML, using built-in aliases/cooldowns (file not changed). "
                        + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
                return List.copyOf(problems);
            }
        }
        boolean changed = false;
        for (CommandSpec spec : specs) {
            String key = spec.name();
            if (!yaml.isConfigurationSection(key)) {
                yaml.set(key + ".aliases", List.copyOf(spec.aliases()));
                yaml.set(key + ".cooldown-seconds", -1);
                changed = true;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            List<String> aliases = new ArrayList<>();
            for (String alias : section.getStringList("aliases")) {
                if (alias.matches("[a-z0-9_-]+") && !alias.equals(key)) {
                    aliases.add(alias);
                } else {
                    problems.add("commands.yml → " + key + ".aliases: '" + alias + "' is not a valid alias (use a-z, 0-9, - or _); skipped");
                }
            }
            Duration cooldown = spec.cooldown();
            Object raw = section.get("cooldown-seconds", -1);
            if (raw instanceof Number n && n.longValue() >= -1 && n.longValue() <= 86_400) {
                if (n.longValue() >= 0) {
                    cooldown = Duration.ofSeconds(n.longValue());
                }
            } else {
                problems.add("commands.yml → " + key + ".cooldown-seconds: '" + raw + "' must be a whole number from -1 to 86400; using the default");
            }
            overrides.put(key, new Overrides(List.copyOf(aliases), cooldown));
        }
        if (changed) {
            yaml.options().setHeader(List.of(HEADER.split("\n")));
            try {
                file.getParentFile().mkdirs();
                yaml.save(file);
            } catch (IOException e) {
                problems.add("commands.yml: could not save new commands: " + e.getMessage());
            }
        }
        return List.copyOf(problems);
    }

    Overrides get(CommandSpec spec) {
        return overrides.getOrDefault(spec.name(), new Overrides(List.copyOf(spec.aliases()), spec.cooldown()));
    }

    synchronized List<String> problems() {
        return List.copyOf(problems);
    }
}
