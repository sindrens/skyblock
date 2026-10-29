package net.skybornmc.skyblock.common.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skybornmc.skyblock.common.placeholder.PlaceholderRegistry;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * {@code %phoenix_<key>%} gives the plain text, {@code %phoenix_<key>_colored%} the rich value as legacy section codes.
 * Only loaded when PlaceholderAPI is present (see {@link PapiHook}).
 */
final class PhoenixExpansion extends PlaceholderExpansion {

    private static final String COLORED = "_colored";

    private final Plugin plugin;
    private final PlaceholderRegistry registry;

    PhoenixExpansion(Plugin plugin, PlaceholderRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public String getIdentifier() {
        return "phoenix";
    }

    @Override
    public String getAuthor() {
        return "SkybornMC";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        Player player = offline == null ? null : offline.getPlayer();
        String key = params.toLowerCase(Locale.ROOT);
        if (key.endsWith(COLORED) && !registry.has(key)) {
            String base = key.substring(0, key.length() - COLORED.length());
            return registry.component(player, base)
                    .map(c -> LegacyComponentSerializer.legacySection().serialize(c))
                    .orElse(null);
        }
        return registry.text(player, key).orElse(null);
    }
}
