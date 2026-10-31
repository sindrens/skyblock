package net.skybornmc.skyblock.common.staff;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired by the staff module (main thread) right after a player entered or left staff mode ({@code /s}), also when staff
 * mode ends on quit or module disable. F15: the islands module turns the island protection bypass on for staff with
 * {@link #ISLAND_CREATIVE_PERMISSION} when they enter, and off when they leave.
 */
public final class StaffModeEvent extends PlayerEvent {

    /**
     * F15 (owner and admin): in staff mode, island bypass turns on by itself and creative works on islands. Declared by
     * the staff module ({@code permissions/staff.yml}); checked by staff, islands and essentials.
     */
    public static final String ISLAND_CREATIVE_PERMISSION = "skyblock.staff.island-creative";

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean entered;

    public StaffModeEvent(Player player, boolean entered) {
        super(player);
        this.entered = entered;
    }

    /** True when the player entered staff mode, false when they left it. */
    public boolean entered() {
        return entered;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
