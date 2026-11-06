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
 * @param target   what the action is done to (Complete Developer Spec §9.1): an upper-case block, crop, entity or item key
 *                 such as {@code WHEAT} or {@code ZOMBIE}, or a machine kind such as {@code generator}; null = unknown.
 *                 A bonus part with a target applies only when this equals it.
 */
public record BonusContext(Player player, ItemStack item, ToolInfo tool, String activity, String target) {

    /** The old shape (no target): every existing caller keeps working. */
    public BonusContext(Player player, ItemStack item, ToolInfo tool, String activity) {
        this(player, item, tool, activity, null);
    }

    public static BonusContext of(Player player) {
        return new BonusContext(player, null, null, null, null);
    }

    public static BonusContext of(Player player, ItemStack item, String activity) {
        return new BonusContext(player, item, null, activity, null);
    }

    /** Only a tool (item attributes, lore). */
    public static BonusContext ofTool(ToolInfo tool) {
        return new BonusContext(null, null, tool, null, null);
    }

    public BonusContext withTool(ToolInfo info) {
        return new BonusContext(player, item, info, activity, target);
    }

    public BonusContext withActivity(String value) {
        return new BonusContext(player, item, tool, value, target);
    }

    /** The same context for one target ({@code WHEAT}, {@code ZOMBIE}, {@code generator}); upper-cased for blocks/entities. */
    public BonusContext withTarget(String value) {
        return new BonusContext(player, item, tool, activity, value);
    }
}
