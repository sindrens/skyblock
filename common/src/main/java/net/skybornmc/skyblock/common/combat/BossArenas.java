package net.skybornmc.skyblock.common.combat;

import org.bukkit.entity.Player;

/**
 * "Is this player in a boss arena?" (owner answer 377: {@code /ec} and {@code /craft} are blocked there), published by the
 * module that owns the arenas (the Depths boss arena now, world bosses later):
 * {@code core.services().optional(BossArenas.class)}. Main thread.
 */
@FunctionalInterface
public interface BossArenas {

    boolean inBossArena(Player player);
}
