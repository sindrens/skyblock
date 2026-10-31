package net.skybornmc.skyblock.common.menu;

import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * An item in a menu slot plus what happens on click ({@code null} handler = decoration only).
 *
 * <p>Left and right click both run {@code onClick} (GUI_DESIGN §0.2). {@code onRight} / {@code onShift} are optional
 * Java-only shortcuts (e.g. shift-click buys a stack); they need a plain action too, because Bedrock players only have
 * the plain click. See {@link ClickRoute}.</p>
 */
public record MenuButton(ItemStack item, Consumer<MenuClick> onClick, Consumer<MenuClick> onRight, Consumer<MenuClick> onShift) {

    public MenuButton {
        if (onClick == null && (onRight != null || onShift != null)) {
            throw new IllegalArgumentException("A Java-only shortcut needs a plain click action too (Bedrock has no right/shift click)");
        }
    }

    public MenuButton(ItemStack item, Consumer<MenuClick> onClick) {
        this(item, onClick, null, null);
    }

    public static MenuButton display(ItemStack item) {
        return new MenuButton(item, null);
    }

    public static MenuButton of(ItemStack item, Consumer<MenuClick> onClick) {
        return new MenuButton(item, onClick);
    }

    /** Adds a Java-only right-click shortcut (left click and Bedrock taps keep the plain action). */
    public MenuButton withRight(Consumer<MenuClick> handler) {
        return new MenuButton(item, onClick, handler, onShift);
    }

    /** Adds a Java-only shift-click shortcut (left or right with shift). */
    public MenuButton withShift(Consumer<MenuClick> handler) {
        return new MenuButton(item, onClick, onRight, handler);
    }

    /** The same actions on another item (the framework uses it for the marked copy). */
    MenuButton withItem(ItemStack other) {
        return new MenuButton(other, onClick, onRight, onShift);
    }

    public boolean clickable() {
        return onClick != null;
    }

    /** The handler for a routed click ({@code null} for {@link ClickRoute#IGNORED}). */
    public Consumer<MenuClick> handler(ClickRoute route) {
        return switch (route) {
            case PLAIN -> onClick;
            case RIGHT -> onRight != null ? onRight : onClick;
            case SHIFT -> onShift != null ? onShift : onClick;
            case IGNORED -> null;
        };
    }
}
