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

    /** sounds.yml section whose entries replace the same-named sound for viewers with the resource pack. */
    public static final String PACK_PREFIX = "pack-sound.";

    private static final Predicate<Player> EVERYONE = p -> true;

    private final Logger log;
    /**
     * Built-in pack sounds (RP6) used when sounds.yml has no {@code pack-sound.*} entries yet (a live file written before
     * the pack existed keeps working): the pack's own short, soft UI sounds replace the vanilla ones for pack viewers only.
     */
    private static final Map<String, Sound> DEFAULT_PACK = Map.of(
            "click", Sound.sound(Key.key("phoenix:ui.click"), Sound.Source.MASTER, 0.4f, 1f),
            "success", Sound.sound(Key.key("phoenix:ui.success"), Sound.Source.MASTER, 0.6f, 1f),
            "error", Sound.sound(Key.key("phoenix:ui.error"), Sound.Source.MASTER, 0.8f, 1f));

    private volatile Map<String, Sound> sounds = Map.of();
    private volatile Map<String, Sound> packSounds = DEFAULT_PACK;
    private volatile Predicate<Player> hasPack = p -> false;
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
        Map<String, Sound> pack = new LinkedHashMap<>(DEFAULT_PACK);
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
                Sound parsed = Sound.sound(id, Sound.Source.MASTER, volume, pitch);
                if (key.startsWith(PACK_PREFIX)) {
                    pack.put(key.substring(PACK_PREFIX.length()), parsed); // pack-sound.<key>: only for pack viewers
                } else {
                    map.put(key, parsed);
                }
            } catch (InvalidKeyException | NumberFormatException e) {
                problems.add("sounds.yml → " + key + ": '" + root.getString(key) + "' must be 'minecraft:sound.id volume pitch' ("
                        + e.getMessage() + ")");
            }
        }
        sounds = Map.copyOf(map);
        packSounds = Map.copyOf(pack);
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

    /** Who has the resource pack loaded (the pack module installs it); {@code null} = nobody. */
    public void setPackViewers(Predicate<Player> hasPack) {
        this.hasPack = hasPack == null ? p -> false : hasPack;
    }

    /**
     * The sound to play: the {@code pack-sound.<key>} entry for a viewer with the pack, otherwise the normal one. Pure,
     * for tests; a missing pack entry falls back to the normal sound, a missing normal sound stays missing.
     */
    public static Sound pick(Map<String, Sound> normal, Map<String, Sound> pack, String key, boolean viewerHasPack) {
        if (viewerHasPack) {
            Sound s = pack.get(key);
            if (s != null) {
                return s;
            }
        }
        return normal.get(key);
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
        boolean packViewer = false;
        try {
            packViewer = hasPack.test(player);
        } catch (RuntimeException e) {
            log.fine("Pack viewer check failed: " + e.getMessage());
        }
        Sound sound = pick(sounds, packSounds, key, packViewer);
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
