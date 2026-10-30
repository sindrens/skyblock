package net.skybornmc.skyblock.common.economy;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player put money into a bank and it was saved (main thread, fired by the economy module). Information only.
 */
public final class BankDepositEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long amount;
    private final boolean island;

    public BankDepositEvent(Player player, long amount, boolean island) {
        super(player);
        this.amount = amount;
        this.island = island;
    }

    /** Money deposited. */
    public long amount() {
        return amount;
    }

    /** True for the island bank, false for the personal bank of the profile. */
    public boolean island() {
        return island;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
