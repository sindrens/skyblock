package net.skybornmc.skyblock.common.sell;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * An online player sold items to the server and was paid (main thread, fired by the economy module after the sale is
 * saved). {@code source} is the sale source ({@code satchel}, {@code hand}, {@code all}, {@code gui}, {@code afk}...).
 * Information only: the sale is final.
 */
public final class ItemsSoldEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String source;
    private final long items;
    private final long value;

    public ItemsSoldEvent(Player player, String source, long items, long value) {
        super(player);
        this.source = source;
        this.items = items;
        this.value = value;
    }

    public String source() {
        return source;
    }

    /** Number of items sold. */
    public long items() {
        return items;
    }

    /** Money paid. */
    public long value() {
        return value;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
