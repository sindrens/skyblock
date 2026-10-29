package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Named sounds from {@code sounds.yml}: {@code success: "minecraft:entity.experience_orb.pickup 0.6 1.4"}. */
public final class Sounds {

    private final Logger log;
    private volatile Map<String, Sound> sounds = Map.of();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    public Sounds(Logger log) {
        this.log = log;
    }

    public List<String> load(File file) {
        List<String> problems = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            problems.add("sounds.yml: not valid YAML, the previous sounds stay active.");
            return problems;
        }
        Map<String, Sound> map = new LinkedHashMap<>();
        ConfigurationSection root = yaml;
        for (String key : root.getKeys(true)) {
            if (root.isConfigurationSection(key)) {
                continue;
            }
            String[] parts = root.getString(key, "").trim().split("\\s+");
            try {
                Key id = Key.key(parts[0]);
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
                if (volume < 0 || volume > 10 || pitch < 0.5f || pitch > 2f) {
                    throw new NumberFormatException("volume 0-10, pitch 0.5-2");
                }
                map.put(key, Sound.sound(id, Sound.Source.MASTER, volume, pitch));
            } catch (InvalidKeyException | NumberFormatException e) {
                problems.add("sounds.yml → " + key + ": '" + root.getString(key) + "' must be 'minecraft:sound.id volume pitch' ("
                        + e.getMessage() + ")");
            }
        }
        sounds = Map.copyOf(map);
        reportedMissing.clear();
        return problems;
    }

    public void play(Player player, String key) {
        Sound sound = sounds.get(key);
        if (sound == null) {
            if (reportedMissing.add(key)) {
                log.warning("Missing sound '" + key + "' in sounds.yml");
            }
            return;
        }
        player.playSound(sound, Sound.Emitter.self());
    }

    public Set<String> keys() {
        return sounds.keySet();
    }
}
