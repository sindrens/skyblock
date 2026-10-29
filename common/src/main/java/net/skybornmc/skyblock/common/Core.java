package net.skybornmc.skyblock.common;

import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.CoreConfig;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Sounds;
import net.skybornmc.skyblock.common.text.Style;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The shared framework handed to every module: plugin, configs, style, messages and sounds.
 * Later milestones add the database, player data, commands, menus, items, audit log and services here.
 */
public record Core(JavaPlugin plugin, CoreConfig config, ConfigManager configs, Style style, Messages messages, Sounds sounds) {

    /** Messages of one module ({@code messages/<module>.yml}); loads the file on first use. */
    public ModuleMessages messages(String module) {
        if (!messages.modules().contains(module)) {
            messages.load(module);
        }
        return messages.of(module);
    }
}
