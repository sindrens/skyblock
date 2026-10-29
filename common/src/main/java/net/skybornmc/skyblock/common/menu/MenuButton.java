package net.skybornmc.skyblock.common.menu;

import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** An item in a menu slot plus what happens on click ({@code null} handler = decoration only). */
public record MenuButton(ItemStack item, Consumer<MenuClick> onClick) {

    public static MenuButton display(ItemStack item) {
        return new MenuButton(item, null);
    }

    public static MenuButton of(ItemStack item, Consumer<MenuClick> onClick) {
        return new MenuButton(item, onClick);
    }

    public boolean clickable() {
        return onClick != null;
    }
}
