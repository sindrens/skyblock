package net.skybornmc.skyblock.common.bonus;

import net.skybornmc.skyblock.common.tools.ToolInfo;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What a bonus is asked for. Every part is optional: item attributes are computed without a player (only the tool),
 * a sell bonus has a player and no item.
 *
 * @param player   the player who acts, or null
 * @param item     the item used (custom tool, set piece), or null
 * @param tool     the item's tool data if the caller already read it (saves a read), or null
 * @param activity what the action is, e.g. a skill id ({@code mining}) for skill XP, {@code sell}, or null
 */
public record BonusContext(Player player, ItemStack item, ToolInfo tool, String activity) {

    public static BonusContext of(Player player) {
        return new BonusContext(player, null, null, null);
    }

    public static BonusContext of(Player player, ItemStack item, String activity) {
        return new BonusContext(player, item, null, activity);
    }

    /** Only a tool (item attributes, lore). */
    public static BonusContext ofTool(ToolInfo tool) {
        return new BonusContext(null, null, tool, null);
    }

    public BonusContext withTool(ToolInfo info) {
        return new BonusContext(player, item, info, activity);
    }

    public BonusContext withActivity(String value) {
        return new BonusContext(player, item, tool, value);
    }
}
