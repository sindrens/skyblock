package net.skybornmc.skyblock.common.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A menu was opened for a player (main thread, fired by {@link MenuService#open} after the menu is shown). The tutorial
 * uses it for "open /menu → Island", "open the Forge" and similar steps. {@link #menuId()} is {@link Menu#menuId()}
 * ({@code hub}, {@code island}, {@code forge}, {@code satchel}, {@code bank}...).
 */
public final class MenuOpenedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String menuId;
    private final String parentId;

    public MenuOpenedEvent(Player player, String menuId, String parentId) {
        super(player);
        this.menuId = menuId;
        this.parentId = parentId;
    }

    /** Id of the opened menu. */
    public String menuId() {
        return menuId;
    }

    /** Id of its parent menu (the one Back returns to), or {@code null}. */
    public String parentId() {
        return parentId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
