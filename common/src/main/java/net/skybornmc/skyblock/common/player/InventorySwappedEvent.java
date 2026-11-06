package net.skybornmc.skyblock.common.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * The player's whole inventory was just replaced by the set of another world group (owner 3 Nov 2026: the Depths has its
 * own inventory; the island and spawn share one). Main thread, after the new items are in place. Modules that put items
 * into the inventory per world (island tools, the menu star) check it again here.
 *
 * <p>While a swap waits for its items to load, the player carries the metadata {@link #PENDING}; modules skip their own
 * per-world item work until this event fires.</p>
 */
public final class InventorySwappedEvent extends PlayerEvent {

    /** Bukkit metadata key set while a swap is waiting for the database. */
    public static final String PENDING = "skyblock-inventory-swap";

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean depths;

    public InventorySwappedEvent(Player player, boolean depths) {
        super(player);
        this.depths = depths;
    }

    /** True when the Depths set is now live, false for the island/spawn set. */
    public boolean depths() {
        return depths;
    }

    /** True while the player's inventory swap waits for the database. */
    public static boolean pending(Player player) {
        return player.hasMetadata(PENDING);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
