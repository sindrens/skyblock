package net.skybornmc.skyblock.worlds.builds;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code builds-state.yml}: when each build was last pasted and applied on this server and by whom. Shown in
 * {@code /skyblock builds list/info}; {@code apply} warns when a build was never pasted here.
 */
public final class BuildState {

    /** One record: time text (display zone), who, and details (blocks, seconds). */
    public record Mark(String at, String by, String details) {
    }

    private final Map<String, Mark> pasted = new ConcurrentHashMap<>();
    private final Map<String, Mark> applied = new ConcurrentHashMap<>();

    public Optional<Mark> pasted(String id) {
        return Optional.ofNullable(pasted.get(id));
    }

    public Optional<Mark> applied(String id) {
        return Optional.ofNullable(applied.get(id));
    }

    public void markPasted(String id, Mark mark) {
        pasted.put(id, mark);
    }

    public void markApplied(String id, Mark mark) {
        applied.put(id, mark);
    }

    public void load(File file) {
        pasted.clear();
        applied.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        read(yaml.getConfigurationSection("pasted"), pasted);
        read(yaml.getConfigurationSection("applied"), applied);
    }

    private static void read(ConfigurationSection s, Map<String, Mark> into) {
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            into.put(id, new Mark(s.getString(id + ".at", ""), s.getString(id + ".by", ""), s.getString(id + ".details", "")));
        }
    }

    /** The current state as YAML text (built on the main thread, written off it). */
    public String snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of("Which builds were pasted/applied on this server (written by /skyblock builds)."));
        new java.util.TreeMap<>(pasted).forEach((id, m) -> write(yaml, "pasted." + id, m));
        new java.util.TreeMap<>(applied).forEach((id, m) -> write(yaml, "applied." + id, m));
        return yaml.saveToString();
    }

    private static void write(YamlConfiguration yaml, String path, Mark m) {
        yaml.set(path + ".at", m.at());
        yaml.set(path + ".by", m.by());
        yaml.set(path + ".details", m.details());
    }

    public static void save(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        java.nio.file.Files.writeString(file.toPath(), text);
    }
}
