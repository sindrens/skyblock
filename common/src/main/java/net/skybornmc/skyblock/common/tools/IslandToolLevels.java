package net.skybornmc.skyblock.common.tools;

import org.bukkit.entity.Player;

/**
 * Owner 7 Nov 2026 (FINISH_PLAN batch A): the level a player's own island tool has right now, so quest steps like
 * "Reach hoe level 10" count a level that was reached before the step opened. Published by the islandtools module.
 */
@FunctionalInterface
public interface IslandToolLevels {

    /** The level of the player's own tool of {@code kind} (hoe, pickaxe, rod, sword); 0 while loading or without one. */
    int level(Player player, String kind);
}
