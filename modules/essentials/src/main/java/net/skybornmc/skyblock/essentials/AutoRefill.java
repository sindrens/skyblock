package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * QOL3 auto-refill (QoL #12): when you place the last block of a hotbar (or off-hand) stack, the next stack of the same
 * block moves from your backpack (inventory slots 9-35) into that slot, so building never stops to drag items around.
 * Plain items only (anything with our item data is skipped, like {@link DropGuard#valuable}); never in creative mode.
 * Switch in {@code /settings} ({@code auto-refill}, default on) and {@code essentials.yml → auto-refill.enabled}.
 * <p>
 * Dupe safety: the move runs one tick later on the main thread in one step (set the target, clear the source), and only
 * when the target slot is really empty and the source still holds a matching stack.
 */
final class AutoRefill implements Listener {

    /** Backpack slots of a player inventory (above the hotbar). */
    static final int BACKPACK_FIRST = 9;
    static final int BACKPACK_LAST = 35;
    static final int OFF_HAND = 40;

    private final Core core;
    private final EssentialsConfig config;
    private final EssentialsSettings settings;

    AutoRefill(Core core, EssentialsConfig config, EssentialsSettings settings) {
        this.core = core;
        this.config = config;
        this.settings = settings;
    }

    /** MONITOR + ignoreCancelled: only placements that really happen. Cheap unless this was the stack's last block. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.autoRefillEnabled) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack placed = event.getItemInHand();
        if (placed.getAmount() != 1 || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || DropGuard.valuable(placed) || !settings.get(player.getUniqueId(), EssentialsSettings.AUTO_REFILL)) {
            return;
        }
        int target = event.getHand() == EquipmentSlot.OFF_HAND ? OFF_HAND : player.getInventory().getHeldItemSlot();
        ItemStack template = placed.asOne();
        UUID id = player.getUniqueId();
        core.scheduler().later("essentials:auto-refill", 1L, () -> refill(id, target, template));
    }

    private void refill(UUID id, int target, ItemStack template) {
        Player player = core.plugin().getServer().getPlayer(id);
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack now = inv.getItem(target);
        if (now != null && !now.isEmpty()) {
            return; // not used up after all (placement refunded, or the player put something there)
        }
        int source = pick(target, slot -> {
            ItemStack candidate = inv.getItem(slot);
            return candidate != null && !candidate.isEmpty() && candidate.isSimilar(template) && !DropGuard.valuable(candidate);
        });
        if (source < 0) {
            return;
        }
        ItemStack moving = inv.getItem(source);
        inv.setItem(source, null);
        inv.setItem(target, moving);
    }

    // ------------------------------------------------------------------ pure rules (unit-tested)

    /**
     * The backpack slot to take the refill from: the first matching slot from 9 to 35 (never the target itself, never
     * the hotbar, armor or off hand); -1 when there is none.
     */
    static int pick(int target, IntPredicate matches) {
        for (int slot = BACKPACK_FIRST; slot <= BACKPACK_LAST; slot++) {
            if (slot != target && matches.test(slot)) {
                return slot;
            }
        }
        return -1;
    }
}
