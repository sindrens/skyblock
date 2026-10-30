package net.skybornmc.skyblock.common.stacking;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Weapon-based multi-kill for stacked mobs (owner S4). Without a provider (or when it returns 1) a lethal hit kills one
 * mob of the stack. The future enchant module implements this (e.g. "Slaughter" kills N per hit) and registers it with
 * {@code core.services().register(id, StackKillProvider.class, impl)}; the stacking module asks it on every lethal hit
 * by a player on a stack of two or more.
 *
 * <p>Called on the main thread inside the death event: keep it cheap (read the enchant level from the item, no I/O).</p>
 */
@FunctionalInterface
public interface StackKillProvider {

    /**
     * How many mobs of the stack this lethal hit kills.
     *
     * @param killer    the player who landed the hit
     * @param weapon    the item in the killer's main hand (may be air)
     * @param victim    the stacked mob (still alive, about to die)
     * @param stackSize mobs in the stack, including the victim (2 or more)
     * @return 1 for a normal kill; values above {@code stackSize} are clamped, values below 1 count as 1
     */
    int killsPerHit(Player killer, ItemStack weapon, LivingEntity victim, int stackSize);
}
