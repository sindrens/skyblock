package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * UX3: the /menu Warps button used to run {@code is warp} (the player warp browser). It now opens the warps menu (built-in
 * action {@value #WARPS}, like {@code settings} and {@code help}), which lists the server places and the player warps.
 * An existing menu.yml that still has the old default is switched once; an owner-chosen command stays.
 */
final class HubWarpsMigration {

    /** Built-in hub action that opens {@link WarpsMenu} (not a server command). */
    static final String WARPS = "warps";

    private static final String OLD_COMMAND = "is warp";

    private HubWarpsMigration() {
    }

    /** True if {@code yaml} was changed. */
    static boolean apply(YamlConfiguration yaml) {
        String path = "entries.warps";
        if (!yaml.isConfigurationSection(path) || !OLD_COMMAND.equalsIgnoreCase(yaml.getString(path + ".command", "").strip())) {
            return false;
        }
        yaml.set(path + ".command", WARPS);
        yaml.set(path + ".status", "active");
        return true;
    }
}
