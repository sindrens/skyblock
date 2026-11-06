package net.skybornmc.skyblock.cosmetics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Upgrades the live {@code tags-titles.yml} to new bundled tags without touching staff edits (owner 2 Nov 2026: "fix all
 * the tags, bold, creative, relevant"). A tag display that still equals one of its old bundled texts word for word gets
 * the new bundled display; a tag id in {@link #ADDED} that the live file lacks is copied in once (a tag staff deleted on
 * purpose is not re-added: the {@code tag-upgrades} marker remembers what was copied). Pure.
 */
final class TagUpgrades {

    /** id → old bundled displays (every earlier default). */
    static final Map<String, List<String>> OLD_DISPLAYS = Map.of(
            "phoenix", List.of("<grad:phoenix>Phoenix</grad>", "<c:brand>[<grad:phoenix>Phoenix</grad>]</c:brand>"),
            "og", List.of("<c:flame>OG", "<c:flame>[OG]"),
            "ember", List.of("<c:ember>Ember", "<c:ember>[Ember]"),
            "spark", List.of("<c:flame>Spark"),
            "ash", List.of("<c:parchment>Ash"),
            "skyborn", List.of("<grad:skyborn>Skyborn</grad>", "<grad:skyborn>[Skyborn]</grad>"),
            "ashes", List.of("<c:text>Ashes"),
            "inferno", List.of("<gradient:#C21F2B:#FF8A1F:#FFD23F>Inferno</gradient>"));

    /** Tags added to the bundled file on 2 Nov 2026, copied into live files once. */
    static final List<String> ADDED = List.of("cinder", "blaze", "skyward", "abyssal", "harvest", "grinder", "mogul",
            "stormborn", "pyro", "eclipse");

    static final String MARKER = "tag-upgrades";
    static final int VERSION = 1;

    private TagUpgrades() {
    }

    /** True when {@code live} changed. */
    static boolean apply(YamlConfiguration live, YamlConfiguration bundled) {
        boolean changed = false;
        ConfigurationSection newTags = bundled.getConfigurationSection("tags");
        if (newTags == null) {
            return false;
        }
        for (Map.Entry<String, List<String>> e : OLD_DISPLAYS.entrySet()) {
            String path = "tags." + e.getKey() + ".display";
            String now = live.getString(path);
            String next = bundled.getString(path);
            if (now != null && next != null && !now.equals(next) && e.getValue().contains(now)) {
                live.set(path, next);
                changed = true;
            }
        }
        if (live.getInt(MARKER, 0) < VERSION) {
            for (String id : ADDED) {
                ConfigurationSection add = newTags.getConfigurationSection(id);
                if (add != null && !live.contains("tags." + id)) {
                    for (String key : add.getKeys(false)) {
                        live.set("tags." + id + "." + key, add.get(key));
                    }
                }
            }
            live.set(MARKER, VERSION);
            changed = true;
        }
        return changed;
    }
}
