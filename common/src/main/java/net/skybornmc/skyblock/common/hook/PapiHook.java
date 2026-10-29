package net.skybornmc.skyblock.common.hook;

import net.skybornmc.skyblock.common.placeholder.PlaceholderRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Registers the {@code %phoenix_<key>%} PlaceholderAPI expansion only when PlaceholderAPI is enabled. The expansion
 * class (which references the PAPI API) is only loaded inside {@link #registerIfPresent}.
 */
public final class PapiHook {

    private PapiHook() {
    }

    /** Returns true when the expansion was registered. */
    public static boolean registerIfPresent(Plugin plugin, PlaceholderRegistry registry) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return false;
        }
        try {
            boolean ok = new PhoenixExpansion(plugin, registry).register();
            plugin.getLogger().info(ok ? "PlaceholderAPI found: %phoenix_<key>% placeholders registered."
                    : "PlaceholderAPI found but the expansion could not be registered.");
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI expansion failed: " + t);
            return false;
        }
    }
}
