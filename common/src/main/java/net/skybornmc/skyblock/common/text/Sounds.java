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
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Named sounds from {@code sounds.yml}: {@code success: "minecraft:entity.experience_orb.pickup 0.6 1.4"}.
 *
 * <p>Two per-player mute groups (F1, {@code /settings}): <b>UI</b> sounds ({@link #playUi}, every sound the menu
 * framework plays, plus the keys named in {@link #uiKeys} wherever they are played) and <b>event</b> sounds
 * ({@link #playEvent}: events, bosses, announcements). The menus module installs who wants which group
 * ({@link #setUiFilter}, {@link #setEventFilter}); without a filter everything plays.</p>
 */
public final class Sounds {

    /** Which group a played sound belongs to. */
    public enum Group { NORMAL, UI, EVENT }

    private static final Predicate<Player> EVERYONE = p -> true;

    private final Logger log;
    private volatile Map<String, Sound> sounds = Map.of();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private volatile Set<String> uiKeys = Set.of();
    private volatile Predicate<Player> wantsUi = EVERYONE;
    private volatile Predicate<Player> wantsEvents = EVERYONE;

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

    /** Plays a sound; keys listed as UI keys follow the player's UI sound setting. */
    public void play(Player player, String key) {
        play(player, key, Group.NORMAL);
    }

    /** Plays a menu/UI sound (click, open, page, confirmations inside menus): skipped for players who turned UI sounds off. */
    public void playUi(Player player, String key) {
        play(player, key, Group.UI);
    }

    /** Plays an event/boss/announcement sound: skipped for players who turned event sounds off. */
    public void playEvent(Player player, String key) {
        play(player, key, Group.EVENT);
    }

    /**
     * Who hears UI sounds, and which keys count as UI sounds even when played with {@link #play} (e.g. {@code click},
     * {@code menu-open}). {@code null} resets to "everyone".
     */
    public void setUiFilter(Set<String> keys, Predicate<Player> wants) {
        this.uiKeys = keys == null ? Set.of() : Set.copyOf(keys);
        this.wantsUi = wants == null ? EVERYONE : wants;
    }

    /** Who hears event sounds. {@code null} resets to "everyone". */
    public void setEventFilter(Predicate<Player> wants) {
        this.wantsEvents = wants == null ? EVERYONE : wants;
    }

    /** Keys that count as UI sounds wherever they are played. */
    public Set<String> uiKeys() {
        return uiKeys;
    }

    /**
     * Whether a sound plays, given its group, key and the player's two settings. Pure, for tests: a UI-listed key is a UI
     * sound in any group, except that an explicit event sound only follows the event setting.
     */
    public static boolean audible(Group group, String key, Set<String> uiKeys, boolean wantsUi, boolean wantsEvents) {
        return switch (group) {
            case EVENT -> wantsEvents;
            case UI -> wantsUi;
            case NORMAL -> wantsUi || key == null || !uiKeys.contains(key);
        };
    }

    private void play(Player player, String key, Group group) {
        boolean ui = group == Group.UI || (group == Group.NORMAL && uiKeys.contains(key));
        boolean event = group == Group.EVENT;
        if (ui || event) {
            try {
                if (!audible(group, key, uiKeys, !ui || wantsUi.test(player), !event || wantsEvents.test(player))) {
                    return;
                }
            } catch (RuntimeException e) {
                log.fine("Sound filter failed: " + e.getMessage()); // a broken filter never silences the server
            }
        }
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
