package net.skybornmc.skyblock.common.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One accepted click on a menu button. */
public record MenuClick(Player player, Menu menu, int slot, ClickType type) {

    public boolean isLeft() {
        return type.isLeftClick();
    }

    public boolean isRight() {
        return type.isRightClick();
    }

    public boolean isShift() {
        return type.isShiftClick();
    }
}
