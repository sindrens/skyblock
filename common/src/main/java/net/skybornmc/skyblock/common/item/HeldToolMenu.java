package net.skybornmc.skyblock.common.item;

import org.bukkit.entity.Player;

/**
 * Feedback 4 Nov 2026 (B7): {@code /tool} opens the menu of the tool in your hand (for Bedrock players, where a click in
 * the air may not reach the server). Two modules have tools with a menu (custom tools, island tools) but only one can own
 * the {@code /tool} command, so the owner asks this service (island tools provide it) when the item in hand isn't its own.
 */
public interface HeldToolMenu {

    /** Opens the menu of the tool in {@code player}'s main hand; false (nothing shown) when it is not such a tool. */
    boolean openHeld(Player player);
}
