package net.skybornmc.skyblock.common.stacking;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Weapon-based multi-kill for stacked mobs (owner S4). Without a provider (or when it returns 1) a lethal hit kills one
 * mob of the stack. The enchants module implements this (Slaughter kills N per hit, Bloodlust adds to it, Plunder and
 * Soul Reaper as {@link #bonus}) and registers it with {@code core.services().register(id, StackKillProvider.class, impl)};
 * the stacking module asks it on every lethal hit by a player on a stack of two or more, and for the bonus on every
 * player kill of a stacked or spawner mob and on every {@link StackingService#killFromStack} batch.
 *
 * <p>Called on the main thread inside the death event: keep it cheap (read the enchant level from the item, no I/O).</p>
 */
@FunctionalInterface
public interface StackKillProvider {

    /**
     * How many mobs of the stack this lethal hit kills. Called exactly once per lethal player hit on a stack (so a
     * provider may count hit streaks here).
     *
     * @param killer    the player who landed the hit
     * @param weapon    the item in the killer's main hand (may be air)
     * @param victim    the stacked mob (still alive, about to die)
     * @param stackSize mobs in the stack, including the victim (2 or more)
     * @return 1 for a normal kill; values above {@code stackSize} are clamped, values below 1 count as 1
     */
    int killsPerHit(Player killer, ItemStack weapon, LivingEntity victim, int stackSize);

    /**
     * Per-mob loot and XP bonus of the weapon for a batch of stacked kills (Plunder, Soul Reaper). Must not change state.
     *
     * @param killer the player
     * @param weapon the item in the killer's main hand (may be air)
     * @param victim the mob (or stack) being killed
     */
    default StackKillBonus bonus(Player killer, ItemStack weapon, LivingEntity victim) {
        return StackKillBonus.NONE;
    }
}
