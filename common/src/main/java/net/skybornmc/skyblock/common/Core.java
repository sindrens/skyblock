package net.skybornmc.skyblock.common;

import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.CoreConfig;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.player.PlayerService;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Sounds;
import net.skybornmc.skyblock.common.text.Style;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The shared framework handed to every module: plugin, configs, style, messages, sounds, database, player data,
 * commands, menus and items. Later milestones add the audit log and the service registry here.
 */
public record Core(JavaPlugin plugin, CoreConfig config, ConfigManager configs, Style style, Messages messages, Sounds sounds,
                   Database database, PlayerService players, CommandService commands, MenuService menus,
                   ItemRegistry items, DupeDetector dupes) {

    /** Messages of one module ({@code messages/<module>.yml}); loads the file on first use. */
    public ModuleMessages messages(String module) {
        if (!messages.modules().contains(module)) {
            messages.load(module);
        }
        return messages.of(module);
    }
}
