package net.skybornmc.skyblock.common.economy;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A Tebex chargeback or refund was applied ({@code /credit chargeback}, CREDIT-SHOP 5.3), main thread. {@code taken}
 * bought credits were taken back; {@code owed} were already spent. The credit shop module lists the player's orders for
 * staff to revoke by hand; nothing is removed automatically.
 */
public final class StoreChargebackEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String transaction;
    private final long taken;
    private final long owed;

    public StoreChargebackEvent(UUID player, String transaction, long taken, long owed) {
        this.player = player;
        this.transaction = transaction;
        this.taken = taken;
        this.owed = owed;
    }

    public UUID player() {
        return player;
    }

    public String transaction() {
        return transaction;
    }

    public long taken() {
        return taken;
    }

    public long owed() {
        return owed;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
