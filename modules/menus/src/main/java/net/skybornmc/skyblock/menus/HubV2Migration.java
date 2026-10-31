package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;

/**
 * GUI-2: one-time switch of a live {@code menu.yml} from the old 28-entry hub ({@code entries}) and the UX3 warps list
 * ({@code warps}) to the clean hub ({@code hub}, {@code hub-groups}), the You menu ({@code you}) and the Travel menu
 * ({@code travel}). The new sections arrive from the bundled file (new keys are always added, {@link YamlFiles}); this
 * removes the old ones and carries the targets of the places that stay (an owner-moved market or crate yard keeps its
 * spot). The old file is kept next to it as a backup by the caller. Runs only while {@code entries} exists, so once.
 */
final class HubV2Migration {

    static final String BACKUP = "menu.yml.before-gui2";

    /** Old warp id → Travel place id (Tavern, Hall of Heroes and the player warp list are not in Travel any more). */
    static final Map<String, String> PLACES = Map.of(
            "spawn", "spawn",
            "market", "market",
            "crate-yard", "crate-yard",
            "forge", "forge-quarter",
            "depths", "depths-gate");

    static final List<String> HEADER = List.of(
            "Phoenix Skyblock menus: the /menu hub, You and Travel (GUI-2), and the /staff hub.",
            "Texts (names and descriptions) are in messages/navigation.yml. Reload: /skyblock reload menus");

    private HubV2Migration() {
    }

    /** True if {@code yaml} was changed (save it). */
    static boolean apply(YamlConfiguration yaml) {
        if (!yaml.isConfigurationSection("entries")) {
            return false;
        }
        ConfigurationSection warps = yaml.getConfigurationSection("warps");
        if (warps != null) {
            for (Map.Entry<String, String> place : PLACES.entrySet()) {
                ConfigurationSection old = warps.getConfigurationSection(place.getKey());
                if (old == null || old.getString("target", "").isBlank()) {
                    continue;
                }
                String path = "travel." + place.getValue();
                yaml.set(path + ".target", old.getString("target"));
                if (old.contains("reason")) {
                    yaml.set(path + ".reason", old.getString("reason"));
                }
                if (old.contains("permission")) {
                    yaml.set(path + ".permission", old.getString("permission"));
                }
            }
        }
        yaml.set("entries", null);
        yaml.set("warps", null);
        yaml.options().setHeader(HEADER);
        return true;
    }
}
